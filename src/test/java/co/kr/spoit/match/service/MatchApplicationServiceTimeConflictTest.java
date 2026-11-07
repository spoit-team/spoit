package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.dto.JoinPage;
import co.kr.spoit.match.dto.JoinPage.ConflictMatch;
import co.kr.spoit.match.dto.JoinPage.ConflictPerson;
import co.kr.spoit.match.dto.MatchDetailPage;
import co.kr.spoit.match.dto.MatchDetailRow;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.TeamClubRow;
import co.kr.spoit.match.dto.TeamMemberRow;
import co.kr.spoit.match.dto.TimeConflictRow;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.service.MemberAccountLockService;
import co.kr.spoit.notification.service.NotificationDispatcher;

/**
 * 이미 참가 신청한 다른 매치와 시간이 겹치면 개인·팀 신청을 막는다.
 * 팀 신청은 대표와 고른 팀원 모두를 확인한다.
 */
class MatchApplicationServiceTimeConflictTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long USER_ID = 7L;
    private static final long MATCH_ID = 3L;
    private static final long FACILITY_ID = 11L;
    private static final long CLUB_ID = 5L;
    private static final long SPORT_ID = 2L;

    private MatchApplicationMapper mapper;
    private MemberAccountLockService accountLockService;
    private MatchService matchService;
    private MatchApplicationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MatchApplicationMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        accountLockService = mock(MemberAccountLockService.class);
        ClubService clubService = mock(ClubService.class);
        matchService = mock(MatchService.class);
        service = new MatchApplicationService(mapper, memberMapper, mock(SportRatingMapper.class),
                matchService, mock(FacilityReservationService.class), accountLockService, clubService,
                mock(NotificationDispatcher.class));

        Member me = account(USER_ID);
        given(memberMapper.findById(USER_ID)).willReturn(Optional.of(me));
        given(accountLockService.lockById(USER_ID)).willReturn(me);
        given(clubService.lockActiveClub(CLUB_ID)).willReturn(new ClubSummary());
        given(mapper.findFacilityId(MATCH_ID)).willReturn(FACILITY_ID);
        given(mapper.lockMatch(MATCH_ID)).willReturn(match());
        given(mapper.countOccupiedSeats(anyLong(), anyList(), anyString())).willReturn(0);
        given(mapper.findTeamClub(CLUB_ID, USER_ID)).willReturn(club());
        given(mapper.findTeamMembers(eq(List.of(CLUB_ID)), eq(MATCH_ID), eq(SPORT_ID), anyList()))
                .willReturn(List.of(teamMember(100L, USER_ID, "박민서"), teamMember(101L, 21L, "박도윤"),
                        teamMember(102L, 22L, "김지우")));
    }

    /* ---------------- 접수 ---------------- */

    @Test
    @DisplayName("개인 신청: 계정을 잠근 뒤 겹치는 매치가 있으면 신청 행을 만들지 않는다")
    void individualConflictBlocksApply() {
        given(mapper.findTimeConflicts(eq(MATCH_ID), eq(List.of(USER_ID)), anyList(), eq("취소")))
                .willReturn(List.of(conflict(USER_ID, "박민서", 9L, "확정")));

        assertThatThrownBy(() -> service.applyIndividual(MATCH_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 참가 신청한 다른 매치와 시간이 겹칩니다.");

        InOrder order = inOrder(mapper, accountLockService);
        order.verify(accountLockService).lockById(USER_ID);
        order.verify(mapper).findTimeConflicts(eq(MATCH_ID), eq(List.of(USER_ID)), anyList(), eq("취소"));
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("개인 신청: 겹치는 매치가 없으면 접수한다")
    void individualWithoutConflictApplies() {
        String receiptNo = service.applyIndividual(MATCH_ID, USER_ID);

        assertThat(receiptNo).startsWith("RCPT-");
        verify(mapper).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("팀 신청: 대표와 팀원의 계정을 잠근 뒤 시간이 겹치는 팀원의 이름을 안내한다")
    void teamMemberConflictBlocksApply() {
        given(mapper.lockClubMemberUserIds(List.of(101L, 102L))).willReturn(List.of(21L, 22L));
        List<Long> applicantIds = List.of(USER_ID, 21L, 22L);
        given(accountLockService.lockAllById(applicantIds))
                .willReturn(applicantIds.stream().map(MatchApplicationServiceTimeConflictTest::account).toList());
        given(mapper.findTimeConflicts(eq(MATCH_ID), eq(applicantIds), anyList(), eq("취소")))
                .willReturn(List.of(conflict(22L, "김지우", 9L, "대기")));

        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, USER_ID, CLUB_ID, List.of(101L, 102L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("김지우님이 이미 참가 신청한 다른 매치와 시간이 겹칩니다.");

        InOrder order = inOrder(mapper, accountLockService);
        order.verify(accountLockService).lockAllById(applicantIds);
        order.verify(mapper).findTimeConflicts(eq(MATCH_ID), eq(applicantIds), anyList(), eq("취소"));
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    /* ---------------- 신청 내용 화면 ---------------- */

    @Test
    @DisplayName("신청 화면: 겹치면 신청을 막고, 같은 매치의 신청·참가 행은 한 번만 확정 상태로 보여 준다")
    void prepareIndividualListsConflicts() {
        given(matchService.findDetail(MATCH_ID, USER_ID)).willReturn(Optional.of(detail()));
        given(mapper.findTimeConflicts(eq(MATCH_ID), eq(List.of(USER_ID)), anyList(), eq("취소")))
                .willReturn(List.of(conflict(USER_ID, "박민서", 9L, "대기"), conflict(USER_ID, "박민서", 9L, "확정")));

        JoinPage page = service.prepareIndividual(MATCH_ID, USER_ID).orElseThrow();

        assertThat(page.isCanReserve()).isFalse();
        assertThat(page.getCapacityNotice()).isEqualTo("이미 참가 신청한 다른 매치와 시간이 겹칩니다.");
        assertThat(page.getTimeConflicts()).extracting(ConflictPerson::getNickname, ConflictPerson::isMe)
                .containsExactly(tuple("박민서", true));
        assertThat(page.getTimeConflicts().get(0).getMatches())
                .extracting(ConflictMatch::getTitle, ConflictMatch::getTimeLabel,
                        ConflictMatch::getFacilityName, ConflictMatch::getStateLabel)
                .containsExactly(tuple("토요일 저녁 3:3 풋살", "11/7(토) 18:00~20:00", "강남 스포츠센터", "참가 확정"));
    }

    @Test
    @DisplayName("신청 화면: 겹치지 않으면 그대로 신청할 수 있다")
    void prepareIndividualWithoutConflict() {
        given(matchService.findDetail(MATCH_ID, USER_ID)).willReturn(Optional.of(detail()));

        JoinPage page = service.prepareIndividual(MATCH_ID, USER_ID).orElseThrow();

        assertThat(page.isCanReserve()).isTrue();
        assertThat(page.getTimeConflicts()).isEmpty();
    }

    @Test
    @DisplayName("팀 신청 화면: 대표와 팀원이 함께 겹치면 대표부터 순서대로 보여 준다")
    void prepareTeamListsConflictsLeaderFirst() {
        given(matchService.findDetail(MATCH_ID, USER_ID)).willReturn(Optional.of(detail()));
        given(mapper.findTimeConflicts(eq(MATCH_ID), eq(List.of(USER_ID, 21L)), anyList(), eq("취소")))
                .willReturn(List.of(conflict(21L, "박도윤", 9L, "확정"), conflict(USER_ID, "박민서", 10L, "대기")));

        JoinPage page = service.prepareTeam(MATCH_ID, USER_ID, CLUB_ID, List.of(101L)).orElseThrow();

        assertThat(page.isCanReserve()).isFalse();
        assertThat(page.getCapacityNotice()).isEqualTo("회원님 외 1명이 이미 참가 신청한 다른 매치와 시간이 겹칩니다.");
        assertThat(page.getTimeConflicts()).extracting(ConflictPerson::getNickname, ConflictPerson::isMe)
                .containsExactly(tuple("박민서", true), tuple("박도윤", false));
    }

    /* ---------------- fixtures ---------------- */

    private static Member account(long userId) {
        Member member = new Member();
        member.setUserId(userId);
        return member;
    }

    private static MatchRow match() {
        LocalDateTime startAt = LocalDateTime.now(KST).plusDays(1);
        MatchRow m = new MatchRow();
        m.setMatchId(MATCH_ID);
        m.setFacilityId(FACILITY_ID);
        m.setSportId(SPORT_ID);
        m.setStatus("모집 중");
        m.setStartAt(startAt);
        m.setEndAt(startAt.plusHours(2));
        m.setMaxParticipants(10);
        return m;
    }

    private static TeamClubRow club() {
        TeamClubRow c = new TeamClubRow();
        c.setClubId(CLUB_ID);
        c.setClubName("성동FC");
        c.setSportId(SPORT_ID);
        return c;
    }

    private static TeamMemberRow teamMember(long clubMemberId, long userId, String nickname) {
        TeamMemberRow m = new TeamMemberRow();
        m.setClubId(CLUB_ID);
        m.setClubMemberId(clubMemberId);
        m.setUserId(userId);
        m.setNickname(nickname);
        m.setRole("동호회원");
        return m;
    }

    private static TimeConflictRow conflict(long userId, String nickname, long matchId, String status) {
        TimeConflictRow r = new TimeConflictRow();
        r.setUserId(userId);
        r.setNickname(nickname);
        r.setMatchId(matchId);
        r.setTitle("토요일 저녁 3:3 풋살");
        r.setFacilityName("강남 스포츠센터");
        r.setStartAt(LocalDateTime.of(2026, 11, 7, 18, 0));
        r.setEndAt(LocalDateTime.of(2026, 11, 7, 20, 0));
        r.setStatus(status);
        return r;
    }

    private static MatchDetailPage detail() {
        LocalDateTime now = LocalDateTime.now(KST);
        MatchDetailRow row = new MatchDetailRow();
        row.setMatchId(MATCH_ID);
        row.setTitle("성동 저녁 풋살");
        row.setSportId(SPORT_ID);
        row.setSportName("풋살");
        row.setFacilityName("성동 구장");
        row.setRegionName("성동구");
        row.setAddress("서울");
        row.setStartAt(now.plusDays(1));
        row.setEndAt(now.plusDays(1).plusHours(2));
        row.setMinParticipants(4);
        row.setMaxParticipants(10);
        row.setFeePerPerson(new BigDecimal("5000"));
        row.setRequiredSkill("초급");
        row.setStatus("모집 중");
        row.setTeamACapacity(5);
        row.setTeamBCapacity(5);

        MatchDetailAssembler assembler = new MatchDetailAssembler();
        return assembler.toPage(row, List.of(), USER_ID, now, now.toLocalDate(), assembler.toTeamOptions(List.of(club()),
                List.of(teamMember(100L, USER_ID, "박민서"), teamMember(101L, 21L, "박도윤"),
                        teamMember(102L, 22L, "김지우")),
                USER_ID));
    }
}
