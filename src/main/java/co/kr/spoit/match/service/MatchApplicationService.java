package co.kr.spoit.match.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.facility.service.FacilityScheduleUnavailableException;
import co.kr.spoit.mat.domain.SportRating;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.match.dto.GroupMemberRow;
import co.kr.spoit.match.dto.JoinPage;
import co.kr.spoit.match.dto.JoinPage.Completion;
import co.kr.spoit.match.dto.JoinPage.JoinMatch;
import co.kr.spoit.match.dto.JoinPage.Person;
import co.kr.spoit.match.dto.JoinPage.Selection;
import co.kr.spoit.match.dto.MatchDetailPage;
import co.kr.spoit.match.dto.MatchDetailPage.TeamMemberOption;
import co.kr.spoit.match.dto.MatchDetailPage.TeamOption;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.TeamClubRow;
import co.kr.spoit.match.dto.TeamMemberRow;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;

/**
 * RES-03 개인 참가 신청, RES-04 팀 단위 신청, RES-05 신청 취소.
 *
 * <p>신청은 match_application(대기)에만 쌓고, match_participant 행은 확정 시점에
 * application_id로 연결해 만든다. 화면에 보이는 참가자는 확정된 행이다.</p>
 *
 * <p>팀 신청은 대표가 고른 팀원마다 신청 행을 하나씩 만들고 group_application_id(대표 행 id)로 묶는다.
 * 확정·취소는 묶음 전체를 한 번에 하고, 취소는 대표만 할 수 있다.</p>
 *
 * <p>확정은 시설 관리자 승인으로만 한다({@link #confirm}). 정원이 다 차도 신청은 대기로 남는다.</p>
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
    private static final String FREE_SUMMARY_NOTE = "현재는 결제 없이 무료로 신청이 접수됩니다.";

    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String RECEIPT_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String INDIVIDUAL = "individual";
    private static final String TEAM = "team";

    private static final String NO_TEAM_MEMBER = "함께 참가할 팀원을 한 명 이상 선택해 주세요.";
    private static final String INVALID_TEAM_MEMBER = "선택한 팀원 정보를 확인할 수 없습니다. 매치 상세에서 다시 선택해 주세요.";

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
                .feeSummaryNote(FREE_SUMMARY_NOTE)
                .build());
    }

    /**
     * RES-04 팀 신청 내용 화면. 동호회나 팀원이 로그인 회원이 고를 수 없는 값이면 empty
     * (화면에서 "신청 내용을 확인할 수 없습니다"로 안내). 신청할 수 없는 상태면 화면은 보여 주고 사유를 안내한다.
     */
    public Optional<JoinPage> prepareTeam(long matchId, long userId, long clubId, List<Long> clubMemberIds) {
        Optional<Member> member = memberMapper.findById(userId);
        if (member.isEmpty()) {
            return Optional.empty();
        }
        List<Long> ids = distinctIds(clubMemberIds);
        return matchService.findDetail(matchId, userId).flatMap(detail -> {
            Optional<TeamOption> club = detail.getClubs().stream()
                    .filter(c -> c.getClubId() == clubId)
                    .findFirst();
            if (club.isEmpty()) {
                return Optional.empty();
            }
            Map<Long, TeamMemberOption> byId = club.get().getMembers().stream()
                    .collect(Collectors.toMap(TeamMemberOption::getClubMemberId, Function.identity()));
            if (!byId.keySet().containsAll(ids)) {
                return Optional.empty();
            }
            List<TeamMemberOption> selected = ids.stream().map(byId::get).toList();
            int seats = 1 + selected.size();

            String blocked = null;
            if (!detail.isCanApply()) {
                blocked = detail.getApplyMessage();
            } else if (selected.isEmpty()) {
                blocked = NO_TEAM_MEMBER;
            } else if (seats > detail.getAvailableSeats()) {
                blocked = overCapacity(detail.getAvailableSeats(), seats);
            } else {
                blocked = selected.stream().filter(TeamMemberOption::isAlreadyApplied).findFirst()
                        .map(m -> alreadyApplied(m.getNickname()))
                        .orElse(null);
            }

            List<Person> persons = new ArrayList<>();
            persons.add(new Person(member.get().getNickname(), "신청 대표"));
            selected.forEach(m -> persons.add(new Person(m.getNickname(), m.isLeader() ? "동호회장" : "팀원")));

            return Optional.of(JoinPage.builder()
                    .match(toJoinMatch(detail))
                    .selection(new Selection(teamKindLabel(club.get().getClubName()), member.get().getNickname(),
                            member.get().getPhone(), TEAM, seats, persons, clubId, ids))
                    .canReserve(blocked == null)
                    .capacityNotice(blocked == null ? "" : blocked)
                    .feeTotalText(FREE_TOTAL_TEXT)
                    .feeSummaryNote(FREE_SUMMARY_NOTE)
                    .build());
        });
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
        requireApplicableMember(userId, now);

        MatchRow match = lockForApply(matchId, now);
        if (applicationMapper.existsActiveEntry(matchId, userId, ApplicationStatus.activeValues())) {
            throw new MatchApplyException("이미 참가 신청한 매칭방입니다.");
        }
        int occupied = countOccupied(matchId);
        if (occupied + 1 > match.getMaxParticipants()) {
            throw new MatchApplyException("정원이 모두 찼습니다.");
        }

        MatchApplication application = MatchApplication.individual(matchId, userId, newReceiptNo(now), now);
        applicationMapper.insert(application);
        return application.getReceiptNo();
    }

    /**
     * RES-04 팀 신청 접수 (대표자 일괄 신청). 대표와 고른 팀원마다 대기 신청 행을 만들고 대표 행 id로 묶는다.
     * 팀원 동의 없이 바로 접수한다. 잠금·운영 일정 확인은 개인 신청과 같다.
     *
     * @param clubMemberIds 함께 참가할 동호회원 (신청 대표 본인 제외, 1명 이상)
     * @return 대표 행의 접수 번호 (팀 접수 번호로 보여 준다)
     */
    @Transactional
    public String applyTeam(long matchId, long userId, long clubId, List<Long> clubMemberIds) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        requireApplicableMember(userId, now);
        List<Long> ids = distinctIds(clubMemberIds);
        if (ids.isEmpty()) {
            throw new MatchApplyException(NO_TEAM_MEMBER);
        }

        MatchRow match = lockForApply(matchId, now);
        TeamClubRow club = applicationMapper.findTeamClub(clubId, userId);
        if (club == null) {
            throw new MatchApplyException("팀으로 신청할 수 있는 동호회가 아닙니다.");
        }
        if (!Objects.equals(club.getSportId(), match.getSportId())) {
            throw new MatchApplyException("동호회 종목과 매치 종목이 다릅니다.");
        }
        if (applicationMapper.existsActiveEntry(matchId, userId, ApplicationStatus.activeValues())) {
            throw new MatchApplyException("이미 참가 신청한 매칭방입니다.");
        }

        // 회원 목록은 잠금 뒤에 읽어야 "이미 신청" 판단이 다른 신청과 겹치지 않는다.
        Map<Long, TeamMemberRow> byId = applicationMapper
                .findTeamMembers(List.of(clubId), matchId, match.getSportId(), ApplicationStatus.activeValues())
                .stream()
                .filter(m -> m.getUserId() != userId)
                .collect(Collectors.toMap(TeamMemberRow::getClubMemberId, Function.identity()));
        List<TeamMemberRow> selected = new ArrayList<>();
        for (Long id : ids) {
            TeamMemberRow m = byId.get(id);
            if (m == null) {
                throw new MatchApplyException(INVALID_TEAM_MEMBER);
            }
            if (m.getPenaltyTime() != null && m.getPenaltyTime().isAfter(now)) {
                throw new MatchApplyException(m.getNickname() + "님은 이용 정지 기간이라 함께 신청할 수 없습니다.");
            }
            if (m.isAlreadyApplied()) {
                throw new MatchApplyException(alreadyApplied(m.getNickname()));
            }
            selected.add(m);
        }

        int seats = 1 + selected.size();
        int occupied = countOccupied(matchId);
        if (occupied + seats > match.getMaxParticipants()) {
            throw new MatchApplyException(overCapacity(Math.max(0, match.getMaxParticipants() - occupied), seats));
        }

        MatchApplication leader = MatchApplication.teamMember(matchId, userId, clubId, null, newReceiptNo(now), now);
        applicationMapper.insert(leader);
        applicationMapper.markGroupLeader(leader.getApplicationId());
        for (TeamMemberRow m : selected) {
            applicationMapper.insert(MatchApplication.teamMember(matchId, m.getUserId(), clubId,
                    leader.getApplicationId(), newReceiptNo(now), now));
        }
        return leader.getReceiptNo();
    }

    /** 세션의 principal은 로그인 시점 값이라 탈퇴·정지는 DB로 다시 확인한다. */
    private void requireApplicableMember(long userId, LocalDateTime now) {
        Member member = memberMapper.findById(userId)
                .orElseThrow(() -> new MatchApplyException("회원 정보를 확인할 수 없습니다."));
        if (member.getDeletedAt() != null) {
            throw new MatchApplyException("탈퇴한 회원은 신청할 수 없습니다.");
        }
        if (member.getPenaltyTime() != null && member.getPenaltyTime().isAfter(now)) {
            throw new MatchApplyException("이용 정지 기간에는 신청할 수 없습니다.");
        }
    }

    /** 시설 → 매칭방 순으로 잠그고, 운영 일정·모집 상태·시작 여부를 확인한 매칭방 */
    private MatchRow lockForApply(long matchId, LocalDateTime now) {
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

        MatchRow match = applicationMapper.lockMatch(matchId);
        if (match == null) {
            throw new MatchApplyException("매칭방 정보를 찾을 수 없습니다.");
        }
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
        return match;
    }

    private int countOccupied(long matchId) {
        return applicationMapper.countOccupiedSeats(matchId,
                ParticipationStatus.occupyingValues(), ApplicationStatus.WAITING.dbValue());
    }

    /** 시설 서비스가 넘긴 표시용 메시지를 그대로 쓰되, 비어 있으면 기본 안내로 대체한다. */
    private static String displayMessage(FacilityScheduleUnavailableException e, String fallback) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    /**
     * 신청 확정 (시설 관리자 승인): match_application → match_participant 연동.
     * 참가자 행은 여기서만 만들며 application_id는 항상 채운다. 호출처(관리자 운영 화면)는 별도 구현.
     *
     * <p>RES-04 팀 신청 행이면 같은 묶음의 대기 행을 모두 함께 확정한다 (팀은 함께 들어오고 함께 나간다).</p>
     */
    @Transactional
    public void confirm(long applicationId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        MatchApplication application = applicationMapper.findById(applicationId);
        if (application == null) {
            throw new MatchApplyException("신청 정보를 찾을 수 없습니다.");
        }
        // 신청 접수와 같은 순서(매칭방 → 신청)로 잠가 교착을 피한다.
        MatchRow match = applicationMapper.lockMatch(application.getMatchId());
        if (match == null) {
            throw new MatchApplyException("매칭방 정보를 찾을 수 없습니다.");
        }
        if (!application.isTeamGroup()) {
            if (!confirmLocked(application.getApplicationId(), application.getApplicantUserId(), match, now)) {
                throw new MatchApplyException("이미 처리된 신청입니다.");
            }
            return;
        }
        int confirmed = 0;
        for (GroupMemberRow row : applicationMapper.findGroupMembers(application.getGroupApplicationId())) {
            if (ApplicationStatus.WAITING.dbValue().equals(row.getStatus())
                    && confirmLocked(row.getApplicationId(), row.getUserId(), match, now)) {
                confirmed++;
            }
        }
        if (confirmed == 0) {
            throw new MatchApplyException("이미 처리된 신청입니다.");
        }
    }

    /** @return 확정했으면 true, 이미 대기 상태가 아니면 false */
    private boolean confirmLocked(long applicationId, long userId, MatchRow match, LocalDateTime now) {
        int updated = applicationMapper.markConfirmed(applicationId,
                ApplicationStatus.WAITING.dbValue(), ApplicationStatus.CONFIRMED.dbValue(), now);
        if (updated == 0) {
            return false;
        }
        BigDecimal entryMmr = sportRatingMapper
                .findByUserIdAndSportId(userId, match.getSportId())
                .map(SportRating::getCurrentMmr)
                .orElse(DEFAULT_MMR);
        applicationMapper.insertParticipant(applicationId, match.getMatchId(),
                userId, ParticipationStatus.CONFIRMED.dbValue(), entryMmr, now);
        return true;
    }

    /**
     * RES-05 신청 취소. 확정된 신청이면 연결된 참가자 행도 함께 취소해 정원을 돌려준다.
     * 팀 배정이 끝난 매치는 팀 구성·팀장이 깨지므로 받지 않는다.
     *
     * <p>RES-04 팀 신청은 대표만 취소할 수 있고, 묶음 전체가 함께 취소된다. 팀원 일부 취소는 아직 지원하지 않는다.</p>
     */
    @Transactional
    public void cancel(long applicationId, long userId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        MatchApplication application = applicationMapper.findById(applicationId);
        if (application == null) {
            throw new MatchApplyException("취소할 수 있는 신청이 아닙니다.");
        }
        Long groupId = application.getGroupApplicationId();
        if (groupId != null) {
            MatchApplication leader = groupId.equals(application.getApplicationId())
                    ? application : applicationMapper.findById(groupId);
            if (leader == null || leader.getApplicantUserId() != userId) {
                throw new MatchApplyException(application.getApplicantUserId() == userId
                        ? "팀 신청은 신청한 대표만 취소할 수 있습니다."
                        : "취소할 수 있는 신청이 아닙니다.");
            }
        } else if (application.getApplicantUserId() != userId) {
            throw new MatchApplyException("취소할 수 있는 신청이 아닙니다.");
        }

        // 신청 접수·확정과 같은 순서(매칭방 → 신청)로 잠근다.
        MatchRow match = applicationMapper.lockMatch(application.getMatchId());
        if (match == null) {
            throw new MatchApplyException("매칭방 정보를 찾을 수 없습니다.");
        }
        if (MatchStatus.MATCHED.dbValue().equals(match.getStatus())) {
            throw new MatchApplyException("팀 배정이 끝난 매치는 취소할 수 없습니다. 시설에 문의해 주세요.");
        }
        if (!MatchStatus.RECRUITING.dbValue().equals(match.getStatus())) {
            throw new MatchApplyException("취소할 수 없는 매칭방입니다.");
        }
        if (!match.getStartAt().isAfter(now)) {
            throw new MatchApplyException("이미 시작한 경기는 취소할 수 없습니다.");
        }

        if (groupId != null) {
            int updated = applicationMapper.markGroupCancelled(groupId, ApplicationStatus.activeValues(),
                    ApplicationStatus.CANCELLED.dbValue(), userId, now);
            if (updated == 0) {
                throw new MatchApplyException("이미 취소되었거나 처리된 신청입니다.");
            }
            applicationMapper.cancelGroupParticipants(groupId, ParticipationStatus.occupyingValues(),
                    ParticipationStatus.CANCELLED.dbValue(), now);
            return;
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

        Selection selection;
        String participantText;
        if (application.isTeamGroup()) {
            List<GroupMemberRow> group = applicationMapper.findGroupMembers(application.getGroupApplicationId());
            List<Person> persons = group.stream()
                    .map(g -> new Person(g.getNickname(), g.getUserId() == userId ? "신청 대표" : "팀원"))
                    .toList();
            TeamClubRow club = application.getClubId() == null ? null
                    : applicationMapper.findTeamClub(application.getClubId(), userId);
            selection = new Selection(teamKindLabel(club == null ? null : club.getClubName()), nickname,
                    member.get().getPhone(), TEAM, persons.size(), persons, application.getClubId(), List.of());
            participantText = persons.stream().map(Person::getNickname).collect(Collectors.joining(", "));
        } else {
            selection = individualSelection(member.get());
            participantText = nickname;
        }

        return matchService.findDetail(matchId, userId).map(detail -> JoinPage.builder()
                .match(toJoinMatch(detail))
                .selection(selection)
                // 완료 화면 요약 영역이 "예약 불가"로 보이지 않게 한다. 폼은 그리지 않는다.
                .canReserve(true)
                .completion(Completion.builder()
                        .title("매치 참가 신청이 접수되었습니다")
                        .message(confirmed
                                ? "참가가 확정되었습니다. 팀 배정 결과는 매치 상세에서 확인할 수 있습니다."
                                : "시설 관리자가 신청을 확인하면 참가가 확정됩니다.")
                        .roomTitle(detail.getTitle())
                        .applicantText(nickname + " · " + selection.getSeats() + "명 신청")
                        .participantText(participantText)
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
                List.of(new Person(member.getNickname(), "신청자")), null, List.of());
    }

    private static String teamKindLabel(String clubName) {
        return clubName == null ? "팀 신청" : "팀 신청 · " + clubName;
    }

    private static String alreadyApplied(String nickname) {
        return nickname + "님은 이미 이 매칭방에 신청했습니다.";
    }

    private static String overCapacity(int available, int seats) {
        return "남은 자리 " + available + "명보다 신청 인원 " + seats + "명이 많습니다.";
    }

    /** 순서를 지키며 중복·null 제거 */
    private static List<Long> distinctIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return List.copyOf(ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)));
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
