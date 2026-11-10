package co.kr.spoit.club.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TreeSet;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

import co.kr.spoit.club.domain.Club;
import co.kr.spoit.club.domain.ClubJoinRequest;
import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubRegionOption;
import co.kr.spoit.club.domain.ClubSchedule;
import co.kr.spoit.club.domain.ClubSportCount;
import co.kr.spoit.club.domain.ClubSportOption;
import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.club.dto.ClubBlockRow;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubCreateForm;
import co.kr.spoit.club.dto.ClubDetailResponse;
import co.kr.spoit.club.dto.ClubManageResponse;
import co.kr.spoit.club.dto.ClubMemberHistoryView;
import co.kr.spoit.club.dto.ClubWithdrawalBlocks;
import co.kr.spoit.club.dto.ClubSearchForm;
import co.kr.spoit.club.dto.ClubSportTabResponse;
import co.kr.spoit.club.dto.MyClubResponse;
import co.kr.spoit.club.dto.MyJoinRequestResponse;
import co.kr.spoit.club.mapper.ClubMapper;
import co.kr.spoit.mat.domain.RatingStatus;
import co.kr.spoit.mat.dto.ParticipantHistoryView;
import co.kr.spoit.mat.dto.SportRatingResponse;
import co.kr.spoit.mat.mapper.ParticipantHistoryMapper;
import co.kr.spoit.mat.service.SportRatingService;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.service.MemberAccountLockService;
import co.kr.spoit.notification.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ClubServiceImpl implements ClubService {

    private final ClubMapper clubMapper;
    private final MemberAccountLockService accountLockService;
    private final NotificationDispatcher notificationDispatcher;
    private static final int NAME_MAX_LENGTH = 30;
    private static final int INTRO_MAX_LENGTH = 500;
    private static final int MIN_MEMBERS = 2;
    private static final int MAX_MEMBERS = 30;
    private static final int MESSAGE_MAX_LENGTH = 200;
    private static final int KICK_REASON_MAX_LENGTH = 200;

    private final SportRatingService sportRatingService;
    private final ParticipantHistoryMapper historyMapper;
    // MAT ParticipantHistoryService와 같은 값(그쪽은 private이라 복사)
    // 신고·제재는 시스템 관리 담당 도메인이라 enum이 없어 db/seed.sql의 report.status·penalty.status 값을
    // 쓴다.
    private static final String REPORT_CONFIRMED = "처리 완료";
    private static final String PENALTY_ACTIVE = "적용 중";
    private static final String NO_RATING_LABEL = "기록 없음";
    private static final int RECENT_CHANGE_LIMIT = 5;

    // 목록: 검색,필터,정렬 조건으로 조회
    @Override
    public List<ClubCardResponse> findClubCards(ClubSearchForm filters) {
        List<ClubSummary> clubs = clubMapper.selectClubs(filters);

        List<ClubCardResponse> cards = new ArrayList<>();
        for (ClubSummary club : clubs) {
            cards.add(ClubCardResponse.from(club));
        }
        return cards;
    }

    @Override
    public List<ClubSportTabResponse> findSportTabs(Long selectedSportId) {
        List<ClubSportCount> sports = clubMapper.selectClubSportCounts();
        int total = 0;
        for (ClubSportCount sport : sports) {
            total += sport.getClubCount();
        }
        List<ClubSportTabResponse> tabs = new ArrayList<>();
        tabs.add(ClubSportTabResponse.all(total, selectedSportId));
        for (ClubSportCount sport : sports) {
            tabs.add(ClubSportTabResponse.from(sport, selectedSportId));
        }
        return tabs;
    }

    @Override
    public ClubDetailResponse findClubDetail(Long clubId, Long userId) {
        ClubSummary club = clubMapper.selectClubDetail(clubId);
        if (club == null) {
            log.info("없는 동호회 조회: clubId={}", clubId);
            return null;
        }
        List<ClubMember> members = clubMapper.selectClubMembers(clubId);
        Long pendingJoinRequestId = clubMapper.selectMyPendingJoinRequestId(clubId, userId);
        return ClubDetailResponse.from(club, members, userId, pendingJoinRequestId);
    }

    @Override
    public List<ClubSportOption> findSportOptions() {
        return clubMapper.selectClubSportOptions();
    }

    @Override
    public List<ClubRegionOption> findRegionOptions() {
        return clubMapper.selectClubRegionOptions();
    }

    // 동호회 만들기 저장
    @Override
    @Transactional
    public Long createClub(Long userId, ClubCreateForm form) throws ClubNameExistException {
        if (userId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        // 1. 입력 정리·검사
        Club club = toClub(null, form);
        // 2. 지역 확인
        if (form.getRegionId() == null || !clubMapper.existsRegion(form.getRegionId())) {
            throw new IllegalArgumentException("선택한 지역을 찾을 수 없습니다.");
        }
        // 3. 종목 확인
        if (form.getSportId() == null || !clubMapper.existsSport(form.getSportId())) {
            throw new IllegalArgumentException("선택한 종목을 찾을 수 없습니다.");
        }
        if (clubMapper.existsActiveClubName(club.getClubName())) {
            throw new ClubNameExistException();
        }
        // 저장(동호회,지역,동호회장 순서 모두 성공해야 함)
        log.info("동호회 저장 직전: {}", club);
        try {
            clubMapper.insertClub(club);
        } catch (DuplicateKeyException exception) {
            // 동시에 같은 이름을 만들면 UK로 막음
            log.warn("동호회 이름 동시 저장 충돌: {}", club.getClubName());
            throw new ClubNameExistException();
        }
        clubMapper.insertClubRegion(club.getClubId(), form.getRegionId());
        // 동호회장 등록 전에 생성자 계정을 잠근다. 회장 소속이 생기는 순간 탈퇴가 커밋되지 않도록
        // 탈퇴 경로와 같은 회원 행에서 직렬화한다(잠금 순서: 동호회 → 계정).
        // 잠근 뒤 최신 값으로 탈퇴를 확인해야 이미 탈퇴한 회원이 회장으로 등록되지 않는다.
        Member creator = accountLockService.lockById(userId);
        if (creator == null || creator.getDeletedAt() != null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        clubMapper.insertClubLeader(club.getClubId(), userId);
        log.info("동호회 저장 완료: clubId={}, userId={}", club.getClubId(), userId);
        return club.getClubId();
    }

    // 만들기-수정 공통: 입력 정리-검사 후 Club으로
    private Club toClub(Long clubId, ClubCreateForm form) {
        if (form == null) {
            throw new IllegalArgumentException("입력 정보를 확인해 주세요.");
        }
        // 1. 브라우저 입력 정리, 검사
        String clubName = trim(form.getClubName());
        if (clubName.isEmpty() || clubName.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("동호회 이름은 1~30자로 입력해 주세요.");
        }
        String intro = trim(form.getIntro()).replace("\r\n", "\n");
        if (intro.isEmpty() || intro.length() > INTRO_MAX_LENGTH) {
            throw new IllegalArgumentException("동호회 소개는 1~500자로 입력해 주세요.");
        }
        Integer maxMembers = form.getMaxMembers();
        if (maxMembers == null || maxMembers < MIN_MEMBERS || maxMembers > MAX_MEMBERS) {
            throw new IllegalArgumentException("모집 정원은 2~30명으로 입력해주세요.");
        }
        String activityDays = normalizeDays(form.getActivityDays());
        LocalTime start = form.getActivityStartTime();
        LocalTime end = form.getActivityEndTime();
        if (start == null || end == null) {
            throw new IllegalArgumentException("활동 시작 시간과 종료 시간을 입력해주세요.");
        }
        // 익일로 넘김, 같은 시각만 거부
        if (start.equals(end)) {
            throw new IllegalArgumentException("활동 시작 시간과 종료시간이 같을 수 없습니다.");
        }

        return Club.builder()
                .clubId(clubId)
                .sportId(form.getSportId())
                .clubName(clubName)
                .intro(intro)
                .maxMembers(maxMembers)
                .activityDays(activityDays)
                .activityStartTime(start)
                .activityEndTime(end)
                .recruitStatus(form.getRecruitStatus())
                .build();
    }

    // 동호회 정보 수정
    @Override
    @Transactional
    public void updateClub(Long userId, Long clubId, ClubCreateForm form) throws ClubNameExistException {
        Club club = toClub(clubId, form);
        if (!"모집중".equals(club.getRecruitStatus()) && !"마감".equals(club.getRecruitStatus())) {
            throw new IllegalArgumentException("모집 상태 값이 올바르지 않습니다.");
        }
        getActiveClubForUpdate(clubId);
        // 첫 SQL은 잠금이어야 해서 지역 확인은 잠근 뒤에
        if (form.getRegionId() == null || !clubMapper.existsRegion(form.getRegionId())) {
            throw new IllegalArgumentException("선택한 지역을 찾을 수 없습니다.");
        }
        if (!clubMapper.existsActiveLeader(clubId, userId)) {
            throw new IllegalArgumentException("동호회장만 동호회 정보를 수정할 수 있습니다.");
        }
        if (club.getMaxMembers() < clubMapper.countActiveMembers(clubId)) {
            throw new IllegalArgumentException("모집 정원은 현재 동호회원 수보다 작을 수 없습니다.");
        }
        if (clubMapper.existsOtherActiveClubName(clubId, club.getClubName())) {
            throw new ClubNameExistException();
        }
        try {
            clubMapper.updateClub(club);
        } catch (DuplicateKeyException exception) {
            log.warn("동호회 이름 동시 수정 충돌: {}", club.getClubName());
            throw new ClubNameExistException();
        }
        clubMapper.deleteClubRegions(clubId);
        clubMapper.insertClubRegion(clubId, form.getRegionId());
        log.info("동호회 정보 수정 완료: clubId={}, userId={}", clubId, userId);
    }

    // 요일 번호(1~7)를 "1,3" 형태로 만듬
    private static String normalizeDays(List<Integer> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("정기 활동 요일을 하나 이상 선택해 주세요.");
        }
        TreeSet<Integer> sorted = new TreeSet<>();
        for (Integer day : days) {
            if (day == null || day < 1 || day > 7) {
                throw new IllegalArgumentException("활동 요일 값이 올바르지 않습니다.");
            }
            sorted.add(day);
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Integer day : sorted) {
            joiner.add(String.valueOf(day));
        }
        return joiner.toString();

    }

    // null도 빈 문자열로 바꿔서 빈 값 검사함
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    // 내 동호회 관리 조회
    @Override
    public ClubManageResponse findManagedClub(Long userId, Long clubId) {
        Long targetClubId = clubId;
        // 선택 없이 내 동호회 조회(동호회장인 곳 먼저, 최근 가입 순)
        if (targetClubId == null) {
            List<ClubSummary> myClubs = clubMapper.selectMyClubs(userId);
            if (myClubs.isEmpty()) {
                log.info("관리할 동호회 없음: userId={}", userId);
                return null;
            }
            targetClubId = myClubs.get(0).getClubId();
        }

        // 내 동호회가 아니면 거부
        if (!clubMapper.existsActiveMember(targetClubId, userId)) {
            throw new IllegalArgumentException("내가 속한 동호회만 관리할 수 있습니다.");
        }

        ClubSummary club = clubMapper.selectClubDetail(targetClubId);
        if (club == null) {
            log.info("없는 동호회 조회: clubId={}", targetClubId);
            return null;
        }
        List<ClubMember> members = clubMapper.selectClubMembers(targetClubId);
        // 가입 신청은 동호회장만 봄
        List<ClubJoinRequest> joinRequests = new ArrayList<>();
        if (clubMapper.existsActiveLeader(targetClubId, userId)) {
            joinRequests = clubMapper.selectPendingJoinRequests(targetClubId);
        }
        LocalDateTime now = LocalDateTime.now();
        List<ClubSchedule> upcomingSchedules = clubMapper.selectUpcomingSchedules(targetClubId, now);
        List<ClubSchedule> recentSchedules = clubMapper.selectRecentSchedules(targetClubId, now);
        return ClubManageResponse.from(club, members, userId, joinRequests, upcomingSchedules, recentSchedules);
    }

    // 내 동호회 선택 버튼 응답
    @Override
    public List<MyClubResponse> findMyClubs(Long userId, Long activeClubId) {
        List<ClubSummary> clubs = clubMapper.selectMyClubs(userId);

        List<MyClubResponse> result = new ArrayList<>();
        for (ClubSummary club : clubs) {
            result.add(MyClubResponse.from(club, activeClubId));
        }
        return result;
    }

    // 가입 신청
    @Override
    @Transactional
    public void applyJoin(Long userId, Long clubId, String message) {
        // 1.자기소개 정리
        String trimmedMessage = trim(message);
        if (trimmedMessage.length() > MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException("자기소개는 200자 이하로 입력해 주세요.");
        }

        // 2. 동호회 잠금
        ClubSummary club = getActiveClubForUpdate(clubId);

        // 3. 신청 가능검사
        if (clubMapper.existsActiveMember(clubId, userId)) {
            throw new IllegalArgumentException("이미 소속된 동호회입니다.");
        }
        if (clubMapper.selectMyPendingJoinRequestId(clubId, userId) != null) {
            throw new IllegalArgumentException("이미 가입 신청을 보냈습니다.");
        }
        if (!"모집중".equals(club.getRecruitStatus())) {
            throw new IllegalArgumentException("모집이 마감된 동호회입니다.");
        }
        if (clubMapper.countActiveMembers(clubId) >= club.getMaxMembers()) {
            throw new IllegalArgumentException("정원이 가득 찬 동호회입니다.");
        }

        // 4. 저장
        clubMapper.insertJoinRequest(clubId, userId, trimmedMessage.isEmpty() ? null : trimmedMessage);
        log.info("가입 신청 저장 완료: clubId={}, userId={}", clubId, userId);

        // 5. 커밋 후 동호회장 알림 (동호회원 번호가 아닌 회원 번호)
        Long joinRequestId = clubMapper.selectMyPendingJoinRequestId(clubId, userId);
        notificationDispatcher.notifyAfterCommit(clubMapper.selectActiveLeaderUserIds(clubId),
                "CLUB_JOIN_REQUESTED", "동호회 가입 신청이 접수되었습니다",
                club.getClubName() + " 팀에 새로운 가입 신청이 도착했습니다. 팀 관리에서 확인해 주세요.",
                null, null, joinRequestId);
    }

    // 내 가입 신청 목록
    @Override
    public List<MyJoinRequestResponse> findMyPendingJoinRequests(Long userId) {
        List<ClubJoinRequest> requests = clubMapper.selectMyPendingJoinRequests(userId);

        List<MyJoinRequestResponse> result = new ArrayList<>();
        for (ClubJoinRequest request : requests) {
            result.add(MyJoinRequestResponse.from(request));
        }
        return result;

    }

    // 가입 신청자 개인 이력: MAT ParticipantHistoryService.getHistory와 같은 흐름(검사/닉네임/종목만 동호회
    // 기준)
    @Override
    public Optional<ParticipantHistoryView> findApplicantHistory(Long clubId, Long joinRequestId, Long userId,
            LocalDateTime now) {

        ClubSummary club = clubMapper.selectClubDetail(clubId);
        if (club == null) {
            return Optional.empty();
        }
        if (!clubMapper.existsActiveLeader(clubId, userId)) {
            throw new AccessDeniedException("동호회장만 가입 신청자 이력을 볼 수 있습니다.");
        }
        // 이 동호회의 대기중 신청만(다른 동호회 신청 번호로 조회 못 하게)
        ClubJoinRequest request = clubMapper.selectPendingJoinRequestById(clubId, joinRequestId);
        if (request == null) {
            return Optional.empty();
        }
        Long applicantUserId = request.getUserId();

        SportRatingResponse rating = sportRatingService.findByUserIdAndSportId(applicantUserId, club.getSportId())
                .orElse(null);

        return Optional.of(new ParticipantHistoryView(request.getNickname(),
                rating == null ? NO_RATING_LABEL : rating.tierLabel(),
                rating == null ? 0 : rating.ratedMatchCount(),
                historyMapper.findRecentChanges(applicantUserId, club.getSportId(), RECENT_CHANGE_LIMIT).stream()
                        .map(change -> new ParticipantHistoryView.RecentChange(change.getCalculatedAt(),
                                change.getFinalDelta()))
                        .toList(),
                historyMapper.countExcludedMatches(applicantUserId, ParticipationStatus.CONFIRMED.dbValue(),
                        RatingStatus.EXCLUDED.name(), MatchStatus.MATCHED.dbValue(), MatchStatus.CLOSED.dbValue()),
                historyMapper.countReports(applicantUserId, REPORT_CONFIRMED),
                historyMapper.findActivePenalties(applicantUserId, PENALTY_ACTIVE, now).stream()
                        .map(penalty -> new ParticipantHistoryView.Penalty(penalty.getPenaltyType(),
                                penalty.getEndAt()))
                        .toList()));
    }

    @Override
    public Optional<ClubMemberHistoryView> findMemberHistory(Long clubId, Long clubMemberId, LocalDateTime now) {
        ClubSummary club = clubMapper.selectClubDetail(clubId);
        if (club == null) {
            return Optional.empty();
        }
        // 이 동호회 활성 회원만(동호회장 포함, 다른 동호회 회원 번호로 조회 못 하게)
        ClubMember member = clubMapper.selectActiveMemberWithLeaderById(clubId, clubMemberId);
        if (member == null) {
            return Optional.empty();
        }
        Long memberUserId = member.getUserId();

        SportRatingResponse rating = sportRatingService.findByUserIdAndSportId(memberUserId, club.getSportId())
                .orElse(null);
        return Optional.of(new ClubMemberHistoryView(
                member.getNickname(),
                rating == null ? NO_RATING_LABEL : rating.tierLabel(),
                clubMapper.countClubRatedMatches(clubId, memberUserId, club.getSportId()),
                clubMapper.selectClubRecentChanges(clubId, memberUserId, club.getSportId(), RECENT_CHANGE_LIMIT)
                        .stream()
                        .map(c -> new ParticipantHistoryView.RecentChange(c.getCalculatedAt(), c.getFinalDelta()))
                        .toList(),
                clubMapper.countClubExcludedMatches(clubId, memberUserId, ParticipationStatus.CONFIRMED.dbValue(),
                        RatingStatus.EXCLUDED.name(), MatchStatus.MATCHED.dbValue(), MatchStatus.CLOSED.dbValue()),
                clubMapper.selectLastClubMatchAt(clubId, memberUserId, ParticipationStatus.CONFIRMED.dbValue(),
                        MatchStatus.MATCHED.dbValue(), MatchStatus.CLOSED.dbValue(), now)));
    }

    // 가입 신청 승인: 잠금->동호회장->대기중->정원확인
    @Override
    @Transactional
    public void approveJoin(Long userId, Long clubId, Long joinRequestId) {
        ClubSummary club = getActiveClubForUpdate(clubId);
        if (!clubMapper.existsActiveLeader(clubId, userId)) {
            throw new IllegalArgumentException("동호회장만 가입 신청을 처리할 수 있습니다.");
        }
        ClubJoinRequest request = clubMapper.selectPendingJoinRequestById(clubId, joinRequestId);
        if (request == null) {
            throw new IllegalArgumentException("이미 처리된 가입 신청입니다.");
        }
        // 신청자 계정을 동호회 다음에 잠그고, 잠근 값으로 탈퇴를 확인한다(위임과 같은 순서)
        Member applicant = accountLockService.lockById(request.getUserId());
        if (applicant == null || applicant.getDeletedAt() != null) {
            throw new IllegalArgumentException("탈퇴한 회원의 신청이라 승인할 수 없습니다. 거절해 주세요.");
        }
        int memberCount = clubMapper.countActiveMembers(clubId);
        if (memberCount >= club.getMaxMembers()) {
            throw new IllegalArgumentException("정원이 가득 찬 동호회입니다.");
        }
        clubMapper.updateJoinRequestStatus(joinRequestId, "승인");
        clubMapper.insertJoinedMember(clubId, request.getUserId());
        // 이번 승인으로 정원이 차면 마감 저장, 다시 여는 건 동호회장이 수정 창에서
        if (memberCount + 1 >= club.getMaxMembers()) {
            clubMapper.updateClubRecruitClosed(clubId);
        }
        log.info("가입 신청 승인: clubId={}, joinRequestId={}", clubId, joinRequestId);

        notificationDispatcher.notifyAfterCommit(List.of(request.getUserId()),
                "CLUB_JOIN_APPROVED", "동호회 가입이 승인되었습니다",
                club.getClubName() + " 팀 가입이 승인되었습니다. 팀 상세에서 확인해 주세요.",
                null, null, joinRequestId);
    }

    // 가입신청 거절: 잠금-> 동호회장-> 상태확인
    @Override
    @Transactional
    public void rejectJoin(Long userId, Long clubId, Long joinRequestId) {
        ClubSummary club = getActiveClubForUpdate(clubId);
        if (!clubMapper.existsActiveLeader(clubId, userId)) {
            throw new IllegalArgumentException("동호회장만 가입 신청을 처리할 수 있습니다.");
        }
        ClubJoinRequest request = clubMapper.selectPendingJoinRequestById(clubId, joinRequestId);
        if (request == null) {
            throw new IllegalArgumentException("이미 처리된 가입 신청입니다.");
        }
        clubMapper.updateJoinRequestStatus(joinRequestId, "거절");
        log.info("가입 신청 거절: clubId={}, joinRequestId={}", clubId, joinRequestId);

        notificationDispatcher.notifyAfterCommit(List.of(request.getUserId()),
                "CLUB_JOIN_REJECTED", "동호회 가입이 거절되었습니다",
                club.getClubName() + " 팀 가입 신청이 거절되었습니다. 팀 상세에서 확인해 주세요.",
                null, null, joinRequestId);

    }

    // 가입 신청 취소: 내 대기중 신청만 취소
    @Override
    @Transactional
    public void cancelJoin(Long userId, Long clubId, Long joinRequestId) {
        getActiveClubForUpdate(clubId);
        if (clubMapper.cancelJoinRequest(clubId, joinRequestId, userId) == 0) {
            throw new IllegalArgumentException("이미 처리된 가입 신청입니다.");
        }
        log.info("가입 신청 취소: clubId={}, joinRequestId={}, userId={}", clubId, joinRequestId, userId);
    }

    // 쓰기 전에 동호회 잠금, 없거나 해체 됐으면 거부
    private ClubSummary getActiveClubForUpdate(Long clubId) {
        ClubSummary club = clubMapper.selectActiveClubForUpdate(clubId);
        if (club == null) {
            throw new IllegalArgumentException("동호회 정보를 찾을 수 없습니다.");
        }
        return club;
    }

    // 동호회장 위임
    @Override
    @Transactional
    public void transferLeader(Long userId, Long clubId, Long clubMemberId) {
        ClubSummary club = getActiveClubForUpdate(clubId);
        if (!clubMapper.existsActiveLeader(clubId, userId)) {
            throw new IllegalArgumentException("동호회장만 위임할 수 있습니다.");
        }
        // 위임 대상의 회원 행을 동호회 다음에 잠근다. 잠금 순서(동호회 → 계정)를 지키고,
        // 잠근 뒤 최신 값으로 탈퇴를 확인해야 탈퇴한 회원이 회장이 되는 일이 없다.
        ClubMember target = clubMapper.selectActiveMemberById(clubId, clubMemberId);
        if (target == null) {
            throw new IllegalArgumentException("위임할 수 없는 동호회원입니다.");
        }
        Member recipient = accountLockService.lockById(target.getUserId());
        if (recipient == null || recipient.getDeletedAt() != null) {
            throw new IllegalArgumentException("위임할 수 없는 동호회원입니다.");
        }
        if (clubMapper.updateRoleToLeader(clubId, clubMemberId) == 0) {
            throw new IllegalArgumentException("위임할 수 없는 동호회원입니다.");
        }
        clubMapper.updateRoleToMember(clubId, userId);
        log.info("동호회장 위임: clubId={}, clubMemberId={}, userId={}", clubId, clubMemberId, userId);

        // 알림 수신자는 동호회원 번호가 아닌 실제 회원 번호다.
        notificationDispatcher.notifyAfterCommit(List.of(target.getUserId()),
                "CLUB_LEADER_TRANSFERRED", "동호회장으로 위임되었습니다",
                club.getClubName() + " 팀의 동호회장 자리를 위임받았습니다. 팀 관리에서 확인해 주세요.",
                null, null, null, clubId);
    }

    // 동호회 탈퇴
    @Override
    @Transactional
    public void leaveClub(Long userId, Long clubId) {
        getActiveClubForUpdate(clubId);
        if (clubMapper.existsActiveLeader(clubId, userId)) {
            throw new IllegalArgumentException("동호회장은 탈퇴할 수 없습니다. 동호회장을 위임한 뒤 탈퇴해 주세요.");
        }
        if (clubMapper.existsRecruitingTeamMatch(clubId, userId)) {
            throw new IllegalArgumentException("모집 중인 팀 매치에 신청되어 있어 탈퇴할 수 없습니다.");
        }
        if (clubMapper.updateMemberLeave(clubId, userId) == 0) {
            throw new IllegalArgumentException("이미 탈퇴했거나 소속되지 않은 동호회입니다.");
        }
        log.info("동호회 탈퇴: clubId={}, userId={}", clubId, userId);
    }

    // 동호회원 추방
    @Override
    @Transactional
    public void kickMember(Long userId, Long clubId, Long clubMemberId, String kickReason) {
        String reason = trim(kickReason);
        if (reason.isEmpty() || reason.length() > KICK_REASON_MAX_LENGTH) {
            throw new IllegalArgumentException("추방 사유를 1~200자로 입력해 주세요.");
        }
        ClubSummary club = getActiveClubForUpdate(clubId);
        if (!clubMapper.existsActiveLeader(clubId, userId)) {
            throw new IllegalArgumentException("동호회장만 동호회원을 추방할 수 있습니다.");
        }
        ClubMember target = clubMapper.selectActiveMemberById(clubId, clubMemberId);
        if (target == null) {
            throw new IllegalArgumentException("추방할 수 없는 동호회원입니다.");
        }
        if (clubMapper.existsRecruitingTeamMatch(clubId, target.getUserId())) {
            throw new IllegalArgumentException("모집 중인 팀 매치에 신청된 동호회원은 추방할 수 없습니다.");
        }
        clubMapper.updateMemberKick(clubId, clubMemberId, reason);
        log.info("동호회원 추방: clubId={}, clubMemberId={}, userId={}", clubId, clubMemberId, userId);

        notificationDispatcher.notifyAfterCommit(List.of(target.getUserId()),
                "CLUB_MEMBER_KICKED", "동호회에서 내보내졌습니다",
                club.getClubName() + " 팀에서 내보내졌습니다.",
                null, null, null, clubId);
    }

    // 동호회 해체
    @Override
    @Transactional
    public void disbandClub(Long userId, Long clubId, String clubNameConfirmation) {
        ClubSummary club = getActiveClubForUpdate(clubId);
        if (!clubMapper.existsActiveLeader(clubId, userId)) {
            throw new IllegalArgumentException("동호회장만 동호회를 해체할 수 있습니다.");
        }
        // 잠근 행의 이름과 비교
        if (!trim(clubNameConfirmation).equals(club.getClubName())) {
            throw new IllegalArgumentException("입력한 동호회 이름이 일치하지 않습니다.");
        }
        if (clubMapper.countActiveMembers(clubId) > 1) {
            throw new IllegalArgumentException("동호회장 외 다른 동호회원이 남아 있어 해체할 수 없습니다. 동호회원을 먼저 정리해 주세요.");
        }
        // userId 없이 동호회 전체 확인
        if (clubMapper.existsRecruitingTeamMatch(clubId, null)) {
            throw new IllegalArgumentException("모집 중인 팀 매치에 신청되어 있어 해체할 수 없습니다.");
        }
        clubMapper.cancelPendingJoinRequests(clubId);
        clubMapper.updateLeaderDisband(clubId, userId);
        clubMapper.updateClubDisband(clubId);
        log.info("동호회 해체: clubId={}, userId={}", clubId, userId);
    }

    /**
     * 동호회 행을 잠그고 최신 값을 돌려준다. 없거나 해체됐으면 null.
     *
     * <p>
     * 팀 매치 신청처럼 다른 도메인이 동호회 행을 먼저 잠가야 할 때 쓴다. 쓰기 트랜잭션 전용이다.
     * </p>
     */
    @Override
    public ClubSummary lockActiveClub(Long clubId) {
        if (clubId == null || clubId <= 0) {
            return null;
        }
        return clubMapper.selectActiveClubForUpdate(clubId);
    }

    // 회원 탈퇴 차단 사유: 가입한 동호회(동호회장·동호회원)를 동호회별로 한 번씩 모은다.
    @Override
    public ClubWithdrawalBlocks findWithdrawalBlocks(Long userId) {
        if (userId == null || userId <= 0) {
            return ClubWithdrawalBlocks.empty();
        }
        return ClubWithdrawalBlocks.of(
                clubNames(clubMapper.selectLeaderClubBlocks(userId)));

    }

    /** 조회 결과를 화면 표시용 동호회 이름 목록으로 바꾼다. 조회가 id 기준으로 이미 고유하므로 그대로 둔다. */
    private static List<String> clubNames(List<ClubBlockRow> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        return blocks.stream()
                .map(ClubBlockRow::getClubName)
                .filter(Objects::nonNull)
                .toList();
    }
}
