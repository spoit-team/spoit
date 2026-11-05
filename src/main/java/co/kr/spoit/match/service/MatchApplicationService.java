package co.kr.spoit.match.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.facility.service.FacilityScheduleUnavailableException;
import co.kr.spoit.mat.domain.SportRating;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.ApplicationType;
import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.match.dto.GroupMemberRow;
import co.kr.spoit.match.dto.JoinPage;
import co.kr.spoit.match.dto.JoinPage.Completion;
import co.kr.spoit.match.dto.JoinPage.ConflictMatch;
import co.kr.spoit.match.dto.JoinPage.ConflictPerson;
import co.kr.spoit.match.dto.JoinPage.JoinMatch;
import co.kr.spoit.match.dto.JoinPage.Person;
import co.kr.spoit.match.dto.JoinPage.Selection;
import co.kr.spoit.match.dto.MatchDetailPage;
import co.kr.spoit.match.dto.MatchDetailPage.TeamMemberOption;
import co.kr.spoit.match.dto.MatchDetailPage.TeamOption;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.MatchTitleRow;
import co.kr.spoit.match.dto.TeamClubRow;
import co.kr.spoit.match.dto.TeamMemberRow;
import co.kr.spoit.match.dto.TimeConflictRow;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.service.MemberAccountLockService;
import lombok.RequiredArgsConstructor;

/**
 * RES-03 개인 참가 신청, RES-04 팀 단위 신청, RES-05 신청 취소.
 *
 * <p>신청은 match_application(대기)에만 쌓고, match_participant 행은 확정 시점에
 * application_id로 연결해 만든다. 화면에 보이는 참가자는 확정된 행이다.</p>
 *
 * <p>팀 신청은 대표가 고른 팀원마다 신청 행을 하나씩 만들고 group_application_id(대표 행 id)로 묶는다.
 * 취소는 대표만 할 수 있다.</p>
 *
 * <p>확정·반려는 시설 관리자가 운영 화면에서 신청 행 하나씩 처리한다({@link #confirm}, {@link #reject}).
 * 팀 신청도 팀원마다 따로 처리하며, 정원이 다 차도 신청은 대기로 남는다.</p>
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

    /** 개인 매치 신청이 탈퇴를 막는 매칭방 상태. "종료"는 경기 종료 후 스케줄러가 기록하므로 여기 없다. */
    private static final List<String> BLOCKING_MATCH_STATUSES =
            List.of(MatchStatus.RECRUITING.dbValue(), MatchStatus.MATCHED.dbValue());

    private static final String NO_TEAM_MEMBER = "함께 참가할 팀원을 한 명 이상 선택해 주세요.";
    private static final String INVALID_TEAM_MEMBER = "선택한 팀원 정보를 확인할 수 없습니다. 매치 상세에서 다시 선택해 주세요.";

    private static final DateTimeFormatter CONFLICT_START = DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREAN);
    private static final DateTimeFormatter CONFLICT_END = DateTimeFormatter.ofPattern("HH:mm");

    private final MatchApplicationMapper applicationMapper;
    private final MemberMapper memberMapper;
    private final SportRatingMapper sportRatingMapper;
    private final MatchService matchService;
    private final FacilityReservationService facilityReservationService;
    private final MemberAccountLockService accountLockService;
    private final ClubService clubService;

    /**
     * 회원 탈퇴를 막는 진행 중 개인 매치 신청의 매치 제목.
     *
     * <p>대기·확정 신청이면서 매칭방이 모집 중이거나 매칭 완료일 때만 차단한다. "매칭 완료"는 경기가
     * 아직 진행 중이라는 뜻이고, 경기 종료 후 스케줄러가 "종료"로 바꾸면 더 이상 막지 않는다.
     * 종료·취소된 매치는 막지 않으므로 경기가 끝난 직후 탈퇴할 수 있다.</p>
     *
     * <p>같은 경기의 중복 신청은 한 번으로 접고, 제목이 같은 서로 다른 경기는 각각 남긴다.</p>
     */
    public List<String> findActiveIndividualMatchTitles(long userId) {
        List<MatchTitleRow> rows = applicationMapper.findActiveIndividualMatchTitles(userId,
                ApplicationType.INDIVIDUAL.dbValue(), ApplicationStatus.activeValues(),
                BLOCKING_MATCH_STATUSES);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(MatchTitleRow::getTitle)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 신청 내용 화면. 신청할 수 없는 상태여도 화면은 보여 주고 사유를 안내한다. */
    public Optional<JoinPage> prepareIndividual(long matchId, long userId) {
        Optional<Member> member = memberMapper.findById(userId);
        if (member.isEmpty()) {
            return Optional.empty();
        }
        return matchService.findDetail(matchId, userId).map(detail -> {
            String blocked = detail.isCanApply() ? null : detail.getApplyMessage();
            List<ConflictPerson> conflicts = blocked == null ? findConflicts(matchId, List.of(userId), userId) : List.of();
            if (!conflicts.isEmpty()) {
                blocked = conflictMessage(conflicts);
            }
            return JoinPage.builder()
                    .match(toJoinMatch(detail))
                    .selection(individualSelection(member.get()))
                    .canReserve(blocked == null)
                    .capacityNotice(blocked == null ? "" : blocked)
                    .timeConflicts(conflicts)
                    .feeTotalText(FREE_TOTAL_TEXT)
                    .feeSummaryNote(FREE_SUMMARY_NOTE)
                    .build();
        });
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
            List<ConflictPerson> conflicts = List.of();
            if (blocked == null) {
                List<Long> userIds = new ArrayList<>();
                userIds.add(userId);
                selected.forEach(m -> userIds.add(m.getUserId()));
                conflicts = findConflicts(matchId, userIds, userId);
                if (!conflicts.isEmpty()) {
                    blocked = conflictMessage(conflicts);
                }
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
                    .timeConflicts(conflicts)
                    .feeTotalText(FREE_TOTAL_TEXT)
                    .feeSummaryNote(FREE_SUMMARY_NOTE)
                    .build());
        });
    }

    /**
     * 개인 신청 접수. 같은 매칭방 신청은 social_match 행 잠금으로 한 줄로 세운다.
     *
     * <p>기존 매칭방이라도 신청 시점의 시설 운영 일정·휴관을 다시 확인한다.
     * 잠금 순서는 시설 → 매칭방 → 신청자 계정으로 고정해 운영시간 변경·휴관 설정과 교착 없이 직렬화한다.
     * 시설 id 조회처럼 잠금 전에 실행되는 일반 조회가 REPEATABLE READ 스냅샷을 일찍 만들면 잠금 뒤
     * 정원·중복 확인이 낡은 값을 볼 수 있으므로 이 트랜잭션만 READ COMMITTED로 실행한다.</p>
     *
     * @return 접수 번호
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String applyIndividual(long matchId, long userId) {
        // 첫 조회로 회원 행을 잠가, 같은 회원의 다른 매치 신청이 끝난 뒤의 상태로 시간 겹침을 확인한다.
        applicationMapper.lockUsers(List.of(userId));
        LocalDateTime now = LocalDateTime.now(ZONE);

        MatchRow match = lockForApply(matchId, now);
        // 잠금 순서: 시설 → 매칭방 → 신청자 계정. 계정을 잠근 뒤 최신 값으로 탈퇴·정지를 다시 확인해야
        // 동시 탈퇴가 신청 행을 남기지 않는다(잠금 전 조회는 스냅샷일 수 있다).
        requireApplicableMemberLocked(userId, now);
        if (applicationMapper.existsActiveEntry(matchId, userId, ApplicationStatus.activeValues())) {
            throw new MatchApplyException("이미 참가 신청한 매칭방입니다.");
        }
        int occupied = countOccupied(matchId);
        if (occupied + 1 > match.getMaxParticipants()) {
            throw new MatchApplyException("정원이 모두 찼습니다.");
        }
        requireNoTimeConflict(matchId, List.of(userId), userId);

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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public String applyTeam(long matchId, long userId, long clubId, List<Long> clubMemberIds) {
        List<Long> ids = distinctIds(clubMemberIds);
        if (ids.isEmpty()) {
            throw new MatchApplyException(NO_TEAM_MEMBER);
        }
        // 첫 조회로 대표와 팀원 회원 행을 잠근다 (개인 신청과 같은 이유, 잠금 읽기만 써서 스냅숏을 늦춘다).
        List<Long> lockIds = new ArrayList<>(applicationMapper.lockClubMemberUserIds(ids));
        lockIds.add(userId);
        applicationMapper.lockUsers(distinctIds(lockIds));
        LocalDateTime now = LocalDateTime.now(ZONE);
        requireApplicableMember(userId, now);

        MatchRow match = lockForApply(matchId, now);
        // 잠금 순서: 시설 → 매칭방 → 동호회 → 포함 계정. 동호회 행을 먼저 잠가야 동시 회장 위임(동호회 → 계정)과
        // 순서가 같아진다. 여기서 계정을 먼저 잠그면 FK 삽입이 동호회 공유 잠금을 기다리며 교착이 생긴다.
        if (clubService.lockActiveClub(clubId) == null) {
            throw new MatchApplyException("팀으로 신청할 수 있는 동호회가 아닙니다.");
        }
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

        // 잠금 순서: 시설 → 매칭방 → 신청에 포함되는 모든 계정(식별자 오름차순).
        // 대표와 팀원 계정을 함께 잠가야 동시 탈퇴가 팀 신청 행을 남기지 않는다.
        List<Long> accountIds = new ArrayList<>();
        accountIds.add(userId);
        for (TeamMemberRow m : selected) {
            accountIds.add(m.getUserId());
        }
        Map<Long, Member> lockedMembers = lockAccounts(accountIds);

        // 잠근 최신 값으로 다시 확인한다(잠금 전 조회는 스냅샷일 수 있다).
        Member leaderAccount = lockedMembers.get(userId);
        if (leaderAccount == null || leaderAccount.getDeletedAt() != null) {
            throw new MatchApplyException("탈퇴한 회원은 신청할 수 없습니다.");
        }
        if (leaderAccount.getPenaltyTime() != null && leaderAccount.getPenaltyTime().isAfter(now)) {
            throw new MatchApplyException("이용 정지 기간에는 신청할 수 없습니다.");
        }
        for (TeamMemberRow m : selected) {
            Member account = lockedMembers.get(m.getUserId());
            // 목록 조회는 미탈퇴 회원만 주므로, 잠근 값이 없거나 탈퇴면 같은 안내로 되돌린다.
            if (account == null || account.getDeletedAt() != null) {
                throw new MatchApplyException(INVALID_TEAM_MEMBER);
            }
            if (account.getPenaltyTime() != null && account.getPenaltyTime().isAfter(now)) {
                throw new MatchApplyException(m.getNickname() + "님은 이용 정지 기간이라 함께 신청할 수 없습니다.");
            }
        }

        int seats = 1 + selected.size();
        int occupied = countOccupied(matchId);
        if (occupied + seats > match.getMaxParticipants()) {
            throw new MatchApplyException(overCapacity(Math.max(0, match.getMaxParticipants() - occupied), seats));
        }
        List<Long> applicantIds = new ArrayList<>();
        applicantIds.add(userId);
        selected.forEach(m -> applicantIds.add(m.getUserId()));
        requireNoTimeConflict(matchId, applicantIds, userId);

        MatchApplication leader = MatchApplication.teamMember(matchId, userId, clubId, null, newReceiptNo(now), now);
        applicationMapper.insert(leader);
        applicationMapper.markGroupLeader(leader.getApplicationId());
        for (TeamMemberRow m : selected) {
            applicationMapper.insert(MatchApplication.teamMember(matchId, m.getUserId(), clubId,
                    leader.getApplicationId(), newReceiptNo(now), now));
        }
        return leader.getReceiptNo();
    }

    /** 세션 principal은 로그인 시점 값이라, 계정을 잠근 뒤 최신 값으로 탈퇴·정지를 확인한다. */
    private void requireApplicableMemberLocked(long userId, LocalDateTime now) {
        Member member = accountLockService.lockById(userId);
        if (member == null) {
            throw new MatchApplyException("회원 정보를 확인할 수 없습니다.");
        }
        if (member.getDeletedAt() != null) {
            throw new MatchApplyException("탈퇴한 회원은 신청할 수 없습니다.");
        }
        if (member.getPenaltyTime() != null && member.getPenaltyTime().isAfter(now)) {
            throw new MatchApplyException("이용 정지 기간에는 신청할 수 없습니다.");
        }
    }

    private void requireNoTimeConflict(long matchId, List<Long> userIds, long userId) {
        List<ConflictPerson> conflicts = findConflicts(matchId, userIds, userId);
        if (!conflicts.isEmpty()) {
            throw new MatchApplyException(conflictMessage(conflicts));
        }
    }

    /**
     * 회원별로 묶은 시간 겹침 매치. 회원 순서는 userIds 순서(대표 먼저)를 따른다.
     * 한 매치에 신청 행과 참가자 행이 함께 있으면 한 번만 보이고, 확정 상태를 우선한다.
     */
    private List<ConflictPerson> findConflicts(long matchId, List<Long> userIds, long userId) {
        List<TimeConflictRow> rows = applicationMapper.findTimeConflicts(matchId, userIds,
                ApplicationStatus.activeValues(), MatchStatus.CANCELLED.dbValue());
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<Long, TimeConflictRow>> byUser = new LinkedHashMap<>();
        for (TimeConflictRow row : rows) {
            byUser.computeIfAbsent(row.getUserId(), k -> new LinkedHashMap<>())
                    .merge(row.getMatchId(), row, (a, b) ->
                            ApplicationStatus.CONFIRMED.dbValue().equals(b.getStatus()) ? b : a);
        }
        List<ConflictPerson> persons = new ArrayList<>();
        for (Long id : userIds) {
            Map<Long, TimeConflictRow> matches = byUser.remove(id);
            if (matches == null) {
                continue;
            }
            List<ConflictMatch> items = matches.values().stream()
                    .map(r -> new ConflictMatch(r.getTitle(),
                            r.getStartAt().format(CONFLICT_START) + "~" + r.getEndAt().format(CONFLICT_END),
                            r.getFacilityName(),
                            ApplicationStatus.CONFIRMED.dbValue().equals(r.getStatus()) ? "참가 확정" : "확정 대기"))
                    .toList();
            String nickname = matches.values().iterator().next().getNickname();
            persons.add(new ConflictPerson(nickname, id == userId, items));
        }
        return persons;
    }

    /** 본인: "이미 참가 신청한 다른 매치와…", 팀원: "박민수님이 이미…" (여러 명이면 "외 N명") */
    private static String conflictMessage(List<ConflictPerson> conflicts) {
        ConflictPerson first = conflicts.get(0);
        String others = conflicts.size() > 1 ? " 외 " + (conflicts.size() - 1) + "명" : "";
        String who;
        if (first.isMe()) {
            who = others.isEmpty() ? "" : "회원님" + others + "이 ";
        } else {
            who = first.getNickname() + "님" + others + "이 ";
        }
        return who + "이미 참가 신청한 다른 매치와 시간이 겹칩니다.";
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

    /** 여러 계정을 잠그고 식별자별 최신 값으로 돌려준다. 잠금 순서는 서비스가 식별자 오름차순으로 고정한다. */
    private Map<Long, Member> lockAccounts(List<Long> userIds) {
        Map<Long, Member> locked = new LinkedHashMap<>();
        for (Member member : accountLockService.lockAllById(userIds)) {
            locked.put(member.getUserId(), member);
        }
        return locked;
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
     * 참가자 행은 여기서만 만들며 application_id는 항상 채운다.
     *
     * <p>팀 신청도 이 신청 행(팀원 한 명)만 확정한다. 담당 시설이 아닌 신청은 없는 신청과 같이 거절한다.</p>
     */
    @Transactional
    public void confirm(long applicationId, long managerUserId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        MatchApplication application = findForDecision(applicationId, managerUserId);
        MatchRow match = lockMatchForDecision(application, now);
        if (!confirmLocked(application.getApplicationId(), application.getApplicantUserId(), match, now)) {
            throw new MatchApplyException("이미 처리된 신청입니다.");
        }
    }

    /**
     * 신청 반려 (시설 관리자): 대기 → 반려. 참가자 행은 만들지 않으므로 정원 자리가 돌아간다.
     * 팀 신청도 이 신청 행(팀원 한 명)만 반려한다.
     */
    @Transactional
    public void reject(long applicationId, long managerUserId) {
        LocalDateTime now = LocalDateTime.now(ZONE);
        MatchApplication application = findForDecision(applicationId, managerUserId);
        lockMatchForDecision(application, now);
        int updated = applicationMapper.markRejected(application.getApplicationId(),
                ApplicationStatus.WAITING.dbValue(), ApplicationStatus.REJECTED.dbValue());
        if (updated == 0) {
            throw new MatchApplyException("이미 처리된 신청입니다.");
        }
    }

    /** 다른 시설의 신청인지 존재하지 않는 신청인지 구분하지 않고 같은 문구로 거절한다. */
    private MatchApplication findForDecision(long applicationId, long managerUserId) {
        MatchApplication application = applicationMapper.findById(applicationId);
        if (application == null || applicationMapper.countManagedApplication(applicationId, managerUserId) == 0) {
            throw new MatchApplyException("신청 정보를 찾을 수 없습니다.");
        }
        return application;
    }

    /** 신청 접수와 같은 순서(매칭방 → 신청)로 잠가 교착을 피하고, 모집 중·경기 전인지 확인한다. */
    private MatchRow lockMatchForDecision(MatchApplication application, LocalDateTime now) {
        MatchRow match = applicationMapper.lockMatch(application.getMatchId());
        if (match == null) {
            throw new MatchApplyException("매칭방 정보를 찾을 수 없습니다.");
        }
        if (!MatchStatus.RECRUITING.dbValue().equals(match.getStatus())
                || match.getStartAt() == null || !match.getStartAt().isAfter(now)) {
            throw new MatchApplyException("모집 중인 경기의 신청만 승인하거나 반려할 수 있습니다.");
        }
        return match;
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
     * <p>RES-04 팀 신청은 대표만 취소할 수 있고, 묶음 전체가 함께 취소된다.</p>
     */
    @Transactional
    public void cancel(long applicationId, long userId) {
        cancel(applicationId, userId, List.of(), null);
    }

    /**
     * RES-04 팀 신청 대표의 팀원 선택 취소. 고른 팀원이 없으면 {@link #cancel(long, long)}과 같이 묶음 전체를 취소한다.
     *
     * <p>남는 활성 팀원이 없으면 묶음 전체 취소와 같다. 대표 본인이 빠지면 남는 팀원 중 새 대표를 골라야 하고,
     * 남는 팀원 행은 새 대표 행의 묶음으로 옮긴다. 취소한 행은 이전 묶음에 남아 취소 기록이 된다.</p>
     *
     * @param memberApplicationIds   취소할 팀원의 신청 행 id
     * @param newLeaderApplicationId 대표가 빠질 때 새 대표가 될 팀원의 신청 행 id
     */
    @Transactional
    public void cancel(long applicationId, long userId, List<Long> memberApplicationIds, Long newLeaderApplicationId) {
        List<Long> targets = memberApplicationIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(memberApplicationIds));
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
        } else if (application.getApplicantUserId() != userId || !targets.isEmpty()) {
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
            if (targets.isEmpty() || !cancelMembersLocked(groupId, userId, targets, newLeaderApplicationId, now)) {
                cancelGroupLocked(groupId, userId, now);
            }
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

    private void cancelGroupLocked(long groupId, long userId, LocalDateTime now) {
        int updated = applicationMapper.markGroupCancelled(groupId, ApplicationStatus.activeValues(),
                ApplicationStatus.CANCELLED.dbValue(), userId, now);
        if (updated == 0) {
            throw new MatchApplyException("이미 취소되었거나 처리된 신청입니다.");
        }
        applicationMapper.cancelGroupParticipants(groupId, ParticipationStatus.occupyingValues(),
                ParticipationStatus.CANCELLED.dbValue(), now);
    }

    /**
     * 매칭방 잠금 뒤 팀원 일부를 취소한다.
     *
     * @return 일부만 취소했으면 true, 활성 팀원 전원을 골라 묶음 전체 취소로 넘겨야 하면 false
     */
    private boolean cancelMembersLocked(long groupId, long userId, List<Long> targets,
                                        Long newLeaderApplicationId, LocalDateTime now) {
        // 잠금 전 확인한 대표 정보는 다른 요청이 대표를 넘겼으면 낡았을 수 있어 최신 행으로 다시 확인한다.
        List<GroupMemberRow> members = applicationMapper.lockGroupMembers(groupId);
        List<Long> active = members.stream()
                .filter(m -> ApplicationStatus.activeValues().contains(m.getStatus()))
                .map(GroupMemberRow::getApplicationId)
                .toList();
        boolean leaderActive = members.stream().anyMatch(m -> m.getApplicationId() == groupId
                && m.getUserId() == userId && active.contains(m.getApplicationId()));
        if (!leaderActive) {
            throw new MatchApplyException("이미 취소되었거나 처리된 신청입니다.");
        }
        if (!active.containsAll(targets)) {
            throw new MatchApplyException("이미 취소되었거나 이 팀 신청에 없는 팀원이 포함되어 있습니다.");
        }
        List<Long> remaining = active.stream().filter(id -> !targets.contains(id)).toList();
        if (remaining.isEmpty()) {
            return false;
        }
        boolean leaderLeaves = targets.contains(groupId);
        if (leaderLeaves && (newLeaderApplicationId == null || !remaining.contains(newLeaderApplicationId))) {
            throw new MatchApplyException("대표가 빠지면 남는 팀원 중 새 대표를 선택해 주세요.");
        }

        for (Long target : targets) {
            if (applicationMapper.markCancelled(target, ApplicationStatus.activeValues(),
                    ApplicationStatus.CANCELLED.dbValue(), userId, now) == 0) {
                throw new MatchApplyException("이미 취소되었거나 처리된 신청입니다.");
            }
            applicationMapper.cancelParticipants(target, ParticipationStatus.occupyingValues(),
                    ParticipationStatus.CANCELLED.dbValue(), now);
        }
        if (leaderLeaves && applicationMapper.moveGroup(remaining, groupId, newLeaderApplicationId) != remaining.size()) {
            throw new MatchApplyException("팀 신청 정보가 바뀌었습니다. 다시 시도해 주세요.");
        }
        return true;
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
