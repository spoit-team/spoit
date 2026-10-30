package co.kr.spoit.match.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.facility.service.FacilityScheduleUnavailableException;
import co.kr.spoit.mat.domain.SportRating;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.ApplicationType;
import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.match.dto.JoinPage;
import co.kr.spoit.match.dto.JoinPage.Completion;
import co.kr.spoit.match.dto.JoinPage.JoinMatch;
import co.kr.spoit.match.dto.JoinPage.Person;
import co.kr.spoit.match.dto.JoinPage.Selection;
import co.kr.spoit.match.dto.MatchDetailPage;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;

/**
 * RES-03 개인 참가 신청, RES-05 신청 취소.
 *
 * <p>신청은 match_application(대기)에만 쌓고, match_participant 행은 확정 시점에
 * application_id로 연결해 만든다. 화면에 보이는 참가자는 확정된 행이다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchApplicationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** sport_rating이 없는 회원의 시작 MMR (match_participant.entry_mmr 기본값과 같다) */
    private static final BigDecimal DEFAULT_MMR = new BigDecimal("1000.00");

    /** 결제 연동 전까지 모든 매치를 무료로 접수한다. 유료 결제를 붙일 때 이 값과 문구를 바꾼다. */
    private static final String FREE_TOTAL_TEXT = "0원";

    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String RECEIPT_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String INDIVIDUAL = "individual";

    private final MatchApplicationMapper applicationMapper;
    private final MemberMapper memberMapper;
    private final SportRatingMapper sportRatingMapper;
    private final MatchService matchService;
    private final FacilityReservationService facilityReservationService;

    /** 신청 내용 화면. 신청할 수 없는 상태여도 화면은 보여 주고 사유를 안내한다. */
    public Optional<JoinPage> prepareIndividual(long matchId, long userId) {
        Optional<Member> member = memberMapper.findById(userId);
        if (member.isEmpty()) {
            return Optional.empty();
        }
        return matchService.findDetail(matchId, userId).map(detail -> JoinPage.builder()
                .match(toJoinMatch(detail))
                .selection(individualSelection(member.get()))
                .canReserve(detail.isCanApply())
                .capacityNotice(detail.isCanApply() ? "" : detail.getApplyMessage())
                .feeTotalText(FREE_TOTAL_TEXT)
                .feeSummaryNote("현재는 결제 없이 무료로 신청이 접수됩니다.")
                .build());
    }

    /**
     * 개인 신청 접수. 같은 매칭방 신청은 social_match 행 잠금으로 한 줄로 세운다.
     *
     * <p>기존 매칭방이라도 신청 시점의 시설 운영 일정·휴관을 다시 확인한다.
     * 잠금 순서는 시설 → 매칭방으로 고정해 운영시간 변경·휴관 설정과 교착 없이 직렬화한다.
     * 신청자 개인 조회는 잠금 전에 이미 스냅샷을 만들었을 수 있으므로, 시설 서비스가
     * 현재 값을 잠금 조회로 다시 읽는다.</p>
     *
     * @return 접수 번호
     */
    @Transactional
    public String applyIndividual(long matchId, long userId) {
        LocalDateTime now = LocalDateTime.now(ZONE);

        // 세션의 principal은 로그인 시점 값이라 탈퇴·정지는 DB로 다시 확인한다.
        Member member = memberMapper.findById(userId)
                .orElseThrow(() -> new MatchApplyException("회원 정보를 확인할 수 없습니다."));
        if (member.getDeletedAt() != null) {
            throw new MatchApplyException("탈퇴한 회원은 신청할 수 없습니다.");
        }
        if (member.getPenaltyTime() != null && member.getPenaltyTime().isAfter(now)) {
            throw new MatchApplyException("이용 정지 기간에는 신청할 수 없습니다.");
        }

        // 잠금 순서(시설 → 매칭방)를 지키려고 시설 id만 먼저 읽는다.
        Long facilityId = applicationMapper.findFacilityId(matchId);
        if (facilityId == null) {
            throw new MatchApplyException("매칭방 정보를 찾을 수 없습니다.");
        }

        try {
            facilityReservationService.lockActiveFacility(facilityId);
        } catch (FacilityScheduleUnavailableException e) {
            throw new MatchApplyException(displayMessage(e, "현재 시설 운영 일정으로는 신청할 수 없습니다."));
        }

        MatchRow match = lockMatch(matchId);
        // 후보 id를 읽은 뒤 시설이 바뀌었으면 잠근 시설과 검증 대상이 어긋난다.
        if (match.getFacilityId() == null
                || match.getFacilityId().longValue() != facilityId.longValue()) {
            throw new MatchApplyException("매칭방 정보가 변경되었습니다. 다시 시도해 주세요.");
        }
        if (match.getStartAt() == null || match.getEndAt() == null
                || !match.getEndAt().isAfter(match.getStartAt())) {
            throw new MatchApplyException("매칭방 이용 시간을 확인할 수 없습니다.");
        }

        try {
            facilityReservationService.requireAvailable(facilityId, match.getStartAt(), match.getEndAt());
        } catch (FacilityScheduleUnavailableException e) {
            throw new MatchApplyException(displayMessage(e, "현재 시설 운영 일정으로는 신청할 수 없습니다."));
        }

        if (!MatchStatus.RECRUITING.dbValue().equals(match.getStatus())) {
            throw new MatchApplyException("모집 중인 매칭방이 아닙니다.");
        }
        if (!match.getStartAt().isAfter(now)) {
            throw new MatchApplyException("이미 시작한 매칭방입니다.");
        }
        if (applicationMapper.existsActiveEntry(matchId, userId, ApplicationStatus.activeValues())) {
            throw new MatchApplyException("이미 참가 신청한 매칭방입니다.");
        }
        int occupied = applicationMapper.countOccupiedSeats(matchId,
                ParticipationStatus.occupyingValues(),
                ApplicationStatus.WAITING.dbValue(),
                ApplicationType.INDIVIDUAL.dbValue());
        if (occupied + 1 > match.getMaxParticipants()) {
            throw new MatchApplyException("정원이 모두 찼습니다.");
        }

        MatchApplication application = MatchApplication.individual(matchId, userId, newReceiptNo(now), now);
        applicationMapper.insert(application);
        return application.getReceiptNo();
    }

    /** 시설 서비스가 넘긴 표시용 메시지를 그대로 쓰되, 비어 있으면 기본 안내로 대체한다. */
    private static String displayMessage(FacilityScheduleUnavailableException e, String fallback) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    /**
     * 매칭방 행을 잠그고 없다면 안내 예외로 끊는다.
     *
     * <p>호출부마다 잠금 순서(접수: 시설 → 매칭방, 확정·취소: 매칭방 → 신청)가 이미 정해져 있고,
     * 이 헬퍼는 그 순서 중 {@code lockMatch} 호출 지점만 감싼다. 없을 때의 안내 문구도 같다.</p>
     */
    private MatchRow lockMatch(long matchId) {
        MatchRow match = applicationMapper.lockMatch(matchId);
        if (match == null) {
            throw new MatchApplyException("매칭방 정보를 찾을 수 없습니다.");
        }
        return match;
    }

    /**
     * 신청 확정: match_application → match_participant 연동.
     * 참가자 행은 여기서만 만들며 application_id는 항상 채운다. 호출처(시설 관리자 승인 등)는 별도 구현.
     */
    @Transactional
    public void confirm(long applicationId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        MatchApplication application = applicationMapper.findById(applicationId);
        if (application == null) {
            throw new MatchApplyException("신청 정보를 찾을 수 없습니다.");
        }
        if (!ApplicationType.INDIVIDUAL.dbValue().equals(application.getApplicationType())) {
            throw new MatchApplyException("동호회 신청 확정은 아직 지원하지 않습니다.");
        }
        // 신청 접수와 같은 순서(매칭방 → 신청)로 잠가 교착을 피한다.
        MatchRow match = lockMatch(application.getMatchId());
        int updated = applicationMapper.markConfirmed(applicationId,
                ApplicationStatus.WAITING.dbValue(), ApplicationStatus.CONFIRMED.dbValue(), now);
        if (updated == 0) {
            throw new MatchApplyException("이미 처리된 신청입니다.");
        }
        BigDecimal entryMmr = sportRatingMapper
                .findByUserIdAndSportId(application.getApplicantUserId(), match.getSportId())
                .map(SportRating::getCurrentMmr)
                .orElse(DEFAULT_MMR);
        applicationMapper.insertParticipant(applicationId, application.getMatchId(),
                application.getApplicantUserId(), ParticipationStatus.CONFIRMED.dbValue(), entryMmr, now);
    }

    /**
     * RES-05 신청 취소 (신청자 본인). 확정된 신청이면 연결된 참가자 행도 함께 취소해 정원을 돌려준다.
     * 팀 배정이 끝난 매치는 팀 구성·팀장이 깨지므로 받지 않는다.
     */
    @Transactional
    public void cancel(long applicationId, long userId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        MatchApplication application = applicationMapper.findById(applicationId);
        if (application == null || application.getApplicantUserId() != userId) {
            throw new MatchApplyException("취소할 수 있는 신청이 아닙니다.");
        }
        // 신청 접수·확정과 같은 순서(매칭방 → 신청)로 잠근다.
        MatchRow match = lockMatch(application.getMatchId());
        if (MatchStatus.MATCHED.dbValue().equals(match.getStatus())) {
            throw new MatchApplyException("팀 배정이 끝난 매치는 취소할 수 없습니다. 시설에 문의해 주세요.");
        }
        if (!MatchStatus.RECRUITING.dbValue().equals(match.getStatus())) {
            throw new MatchApplyException("취소할 수 없는 매칭방입니다.");
        }
        if (!match.getStartAt().isAfter(now)) {
            throw new MatchApplyException("이미 시작한 경기는 취소할 수 없습니다.");
        }
        int updated = applicationMapper.markCancelled(applicationId, ApplicationStatus.activeValues(),
                ApplicationStatus.CANCELLED.dbValue(), userId, now);
        if (updated == 0) {
            throw new MatchApplyException("이미 취소되었거나 처리된 신청입니다.");
        }
        applicationMapper.cancelParticipants(applicationId, ParticipationStatus.occupyingValues(),
                ParticipationStatus.CANCELLED.dbValue(), now);
    }

    /** 완료 화면. 본인 신청이 아니거나 다른 매칭방 접수 번호면 empty */
    public Optional<JoinPage> findCompletion(long matchId, String receiptNo, long userId) {
        MatchApplication application = applicationMapper.findByReceiptNo(receiptNo);
        if (application == null
                || application.getMatchId() != matchId
                || application.getApplicantUserId() != userId) {
            return Optional.empty();
        }
        Optional<Member> member = memberMapper.findById(userId);
        if (member.isEmpty()) {
            return Optional.empty();
        }
        boolean confirmed = ApplicationStatus.CONFIRMED.dbValue().equals(application.getStatus());
        String nickname = member.get().getNickname();
        return matchService.findDetail(matchId, userId).map(detail -> JoinPage.builder()
                .match(toJoinMatch(detail))
                .selection(individualSelection(member.get()))
                // 완료 화면 요약 영역이 "예약 불가"로 보이지 않게 한다. 폼은 그리지 않는다.
                .canReserve(true)
                .completion(Completion.builder()
                        .title("매치 참가 신청이 접수되었습니다")
                        .message(confirmed
                                ? "참가가 확정되었습니다. 팀 배정 결과는 매치 상세에서 확인할 수 있습니다."
                                : "시설 관리자가 신청을 확인하면 참가가 확정됩니다.")
                        .roomTitle(detail.getTitle())
                        .applicantText(nickname + " · 1명 신청")
                        .participantText(nickname)
                        .receiptNo(application.getReceiptNo())
                        .stateLabel(confirmed ? "참가 확정" : "확정 대기")
                        .paymentGuidance("무료 신청 · 별도 결제 없음")
                        .totalText(FREE_TOTAL_TEXT)
                        .teamAssigned(false)
                        .build())
                .build());
    }

    private JoinMatch toJoinMatch(MatchDetailPage d) {
        return JoinMatch.builder()
                .matchId(d.getMatchId())
                .title(d.getTitle())
                .sportName(d.getSportName())
                .statusLabel(d.getStatusLabel())
                .facilityName(d.getPlace())
                .dateLabel(d.getDateLabel())
                .time(d.getTime())
                .place(d.getFacility().getCaption())
                .levelLabel(d.getLevelLabel())
                .feeText(d.getFeeText())
                .capacityLabel(d.getOccupancyLabel())
                .maxParticipants(d.getMaxParticipants())
                .occupiedSeats(d.getOccupiedSeats())
                .occupancyPercent(d.getOccupancyPercent())
                .remainingLabel(d.getAvailableSeats() > 0 ? "남은 자리 " + d.getAvailableSeats() + "명" : "정원 마감")
                .build();
    }

    private Selection individualSelection(Member member) {
        return new Selection("개인 신청", member.getNickname(), member.getPhone(), INDIVIDUAL, 1,
                List.of(new Person(member.getNickname(), "신청자")));
    }

    /** RCPT-20261027-XXXXXXXX (헷갈리는 0·O·1·I 제외). uk_match_application_receipt로 중복은 DB가 막는다. */
    private String newReceiptNo(LocalDateTime now) {
        StringBuilder sb = new StringBuilder("RCPT-").append(now.format(RECEIPT_DATE)).append('-');
        for (int i = 0; i < 8; i++) {
            sb.append(RECEIPT_CHARS.charAt(RANDOM.nextInt(RECEIPT_CHARS.length())));
        }
        return sb.toString();
    }
}
