package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.dto.GroupMemberRow;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.TeamClubRow;
import co.kr.spoit.match.dto.TeamMemberRow;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;

/**
 * RES-04 팀 단위 신청: 대표가 고른 팀원마다 대기 신청 행을 만들고 대표 행 id로 묶는다.
 * 확정은 시설 관리자 승인으로만 묶음 전체를, 취소는 대표만 묶음 전체를 한다.
 */
class MatchApplicationServiceTeamTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long LEADER_ID = 7L;
    private static final long MATCH_ID = 3L;
    private static final long FACILITY_ID = 11L;
    private static final long CLUB_ID = 5L;
    private static final long SPORT_ID = 2L;

    private MatchApplicationMapper mapper;
    private MemberMapper memberMapper;
    private SportRatingMapper sportRatingMapper;
    private MatchApplicationService service;

    private final List<MatchApplication> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mapper = mock(MatchApplicationMapper.class);
        memberMapper = mock(MemberMapper.class);
        sportRatingMapper = mock(SportRatingMapper.class);
        service = new MatchApplicationService(mapper, memberMapper, sportRatingMapper,
                mock(MatchService.class), mock(FacilityReservationService.class));

        Member leader = new Member();
        leader.setUserId(LEADER_ID);
        given(memberMapper.findById(LEADER_ID)).willReturn(Optional.of(leader));
        given(mapper.findFacilityId(MATCH_ID)).willReturn(FACILITY_ID);
        given(mapper.lockMatch(MATCH_ID)).willReturn(match(10));
        given(mapper.findTeamClub(CLUB_ID, LEADER_ID)).willReturn(club(SPORT_ID));
        given(mapper.findTeamMembers(eq(List.of(CLUB_ID)), eq(MATCH_ID), eq(SPORT_ID), anyList()))
                .willReturn(List.of(member(100L, LEADER_ID, "대표"), member(101L, 21L, "박민수"),
                        member(102L, 22L, "이서연")));
        given(sportRatingMapper.findByUserIdAndSportId(anyLong(), anyLong())).willReturn(Optional.empty());

        AtomicLong ids = new AtomicLong(500);
        willAnswer(invocation -> {
            MatchApplication a = invocation.getArgument(0);
            a.setApplicationId(ids.incrementAndGet());
            inserted.add(a);
            return 1;
        }).given(mapper).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("대표와 고른 팀원마다 대기 신청 행을 만들고 대표 행 id로 묶는다")
    void createsOneRowPerMemberGroupedByLeader() {
        given(mapper.countOccupiedSeats(anyLong(), anyList(), anyString())).willReturn(0);

        String receiptNo = service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L, 102L));

        assertThat(inserted).hasSize(3);
        MatchApplication leaderRow = inserted.get(0);
        assertThat(receiptNo).isEqualTo(leaderRow.getReceiptNo());
        assertThat(inserted).extracting(MatchApplication::getApplicantUserId).containsExactly(LEADER_ID, 21L, 22L);
        assertThat(inserted).extracting(MatchApplication::getApplicationType).containsOnly("동호회");
        assertThat(inserted).extracting(MatchApplication::getStatus).containsOnly("대기");
        assertThat(inserted).extracting(MatchApplication::getClubId).containsOnly(CLUB_ID);
        assertThat(inserted.subList(1, 3)).extracting(MatchApplication::getGroupApplicationId)
                .containsOnly(leaderRow.getApplicationId());
        assertThat(inserted).extracting(MatchApplication::getReceiptNo).doesNotHaveDuplicates();
        verify(mapper).markGroupLeader(leaderRow.getApplicationId());
        verify(mapper, never()).markConfirmed(anyLong(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("팀원을 한 명도 고르지 않으면 신청하지 않는다")
    void rejectsEmptyMembers() {
        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of()))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("함께 참가할 팀원을 한 명 이상 선택해 주세요.");
        verify(mapper, never()).lockMatch(anyLong());
    }

    @Test
    @DisplayName("소속되지 않았거나 해체된 동호회로는 신청할 수 없다")
    void rejectsUnknownClub() {
        given(mapper.findTeamClub(CLUB_ID, LEADER_ID)).willReturn(null);

        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("팀으로 신청할 수 있는 동호회가 아닙니다.");
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("동호회 종목과 매치 종목이 다르면 신청할 수 없다")
    void rejectsOtherSportClub() {
        given(mapper.findTeamClub(CLUB_ID, LEADER_ID)).willReturn(club(SPORT_ID + 1));

        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("동호회 종목과 매치 종목이 다릅니다.");
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("다른 동호회 회원이나 대표 본인을 팀원으로 넘기면 거부한다")
    void rejectsForeignOrSelfMember() {
        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L, 999L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessageContaining("선택한 팀원 정보를 확인할 수 없습니다");
        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(100L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessageContaining("선택한 팀원 정보를 확인할 수 없습니다");
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("이미 이 매치에 들어간 팀원이 있으면 신청하지 않는다")
    void rejectsAlreadyAppliedMember() {
        TeamMemberRow applied = member(101L, 21L, "박민수");
        applied.setAlreadyApplied(true);
        given(mapper.findTeamMembers(eq(List.of(CLUB_ID)), eq(MATCH_ID), eq(SPORT_ID), anyList()))
                .willReturn(List.of(member(100L, LEADER_ID, "대표"), applied));

        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("박민수님은 이미 이 매칭방에 신청했습니다.");
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("대표 본인이 이미 신청했으면 신청하지 않는다")
    void rejectsLeaderAlreadyApplied() {
        given(mapper.existsActiveEntry(eq(MATCH_ID), eq(LEADER_ID), anyList())).willReturn(true);

        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 참가 신청한 매칭방입니다.");
    }

    @Test
    @DisplayName("대표를 포함한 신청 인원이 남은 자리보다 많으면 신청하지 않는다")
    void rejectsOverCapacity() {
        given(mapper.countOccupiedSeats(anyLong(), anyList(), anyString())).willReturn(8);

        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L, 102L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("남은 자리 2명보다 신청 인원 3명이 많습니다.");
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("신청으로 정원이 다 차도 확정하지 않고 대기로 둔다 (확정은 시설 관리자 승인)")
    void fullMatchStaysWaiting() {
        given(mapper.countOccupiedSeats(anyLong(), anyList(), anyString())).willReturn(7);

        service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L, 102L));

        assertThat(inserted).extracting(MatchApplication::getStatus).containsOnly("대기");
        verify(mapper, never()).markConfirmed(anyLong(), anyString(), anyString(), any());
        verify(mapper, never()).insertParticipant(anyLong(), anyLong(), anyLong(), anyString(), any(), any());
    }

    /* ---------------- 확정 (시설 관리자 승인) ---------------- */

    @Test
    @DisplayName("팀 신청 행을 확정하면 묶음의 대기 행을 모두 확정하고 팀원마다 참가자 행을 만든다")
    void confirmsWholeGroup() {
        given(mapper.findById(501L)).willReturn(groupRow(501L, 21L, 500L));
        given(mapper.findGroupMembers(500L)).willReturn(List.of(
                group(500L, LEADER_ID, "대기"), group(501L, 21L, "대기"), group(502L, 22L, "취소")));
        given(mapper.markConfirmed(anyLong(), eq("대기"), eq("확정"), any())).willReturn(1);

        service.confirm(501L);

        ArgumentCaptor<Long> users = ArgumentCaptor.forClass(Long.class);
        verify(mapper, times(2)).insertParticipant(anyLong(), eq(MATCH_ID), users.capture(), eq("확정"), any(), any());
        assertThat(users.getAllValues()).containsExactly(LEADER_ID, 21L);
        verify(mapper, never()).markConfirmed(eq(502L), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("개인 신청 확정은 그 신청 한 건만 참가자 행으로 만든다")
    void confirmsIndividual() {
        MatchApplication individual = MatchApplication.individual(MATCH_ID, LEADER_ID, "RCPT-1", LocalDateTime.now(KST));
        individual.setApplicationId(600L);
        given(mapper.findById(600L)).willReturn(individual);
        given(mapper.markConfirmed(eq(600L), eq("대기"), eq("확정"), any())).willReturn(1);

        service.confirm(600L);

        verify(mapper).insertParticipant(eq(600L), eq(MATCH_ID), eq(LEADER_ID), eq("확정"), any(), any());
        verify(mapper, never()).findGroupMembers(anyLong());
    }

    @Test
    @DisplayName("묶음에 대기 행이 없으면 이미 처리된 신청으로 거절한다")
    void rejectsProcessedGroup() {
        given(mapper.findById(500L)).willReturn(groupRow(500L, LEADER_ID, 500L));
        given(mapper.findGroupMembers(500L)).willReturn(List.of(group(500L, LEADER_ID, "확정")));

        assertThatThrownBy(() -> service.confirm(500L))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 처리된 신청입니다.");
    }

    /* ---------------- 취소 ---------------- */

    @Test
    @DisplayName("대표가 취소하면 묶음 전체와 연결된 참가자 행을 함께 취소한다")
    void leaderCancelsWholeGroup() {
        given(mapper.findById(500L)).willReturn(groupRow(500L, LEADER_ID, 500L));
        given(mapper.markGroupCancelled(eq(500L), anyList(), eq("취소"), eq(LEADER_ID), any())).willReturn(3);

        service.cancel(500L, LEADER_ID);

        verify(mapper).cancelGroupParticipants(eq(500L), anyList(), eq("취소"), any());
        verify(mapper, never()).markCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("팀원은 자기 행이라도 팀 신청을 취소할 수 없다")
    void memberCannotCancel() {
        given(mapper.findById(501L)).willReturn(groupRow(501L, 21L, 500L));
        given(mapper.findById(500L)).willReturn(groupRow(500L, LEADER_ID, 500L));

        assertThatThrownBy(() -> service.cancel(501L, 21L))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("팀 신청은 신청한 대표만 취소할 수 있습니다.");
        verify(mapper, never()).lockMatch(anyLong());
        verify(mapper, never()).markGroupCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("대표는 팀원 행 번호로 취소해도 묶음 전체를 취소한다")
    void leaderCancelsViaMemberRow() {
        given(mapper.findById(501L)).willReturn(groupRow(501L, 21L, 500L));
        given(mapper.findById(500L)).willReturn(groupRow(500L, LEADER_ID, 500L));
        given(mapper.markGroupCancelled(eq(500L), anyList(), eq("취소"), eq(LEADER_ID), any())).willReturn(3);

        service.cancel(501L, LEADER_ID);

        verify(mapper).cancelGroupParticipants(eq(500L), anyList(), eq("취소"), any());
    }

    @Test
    @DisplayName("이미 모두 취소된 팀 신청이면 참가자 행을 건드리지 않는다")
    void rejectsAlreadyCancelledGroup() {
        given(mapper.findById(500L)).willReturn(groupRow(500L, LEADER_ID, 500L));
        given(mapper.markGroupCancelled(eq(500L), anyList(), eq("취소"), eq(LEADER_ID), any())).willReturn(0);

        assertThatThrownBy(() -> service.cancel(500L, LEADER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 취소되었거나 처리된 신청입니다.");
        verify(mapper, never()).cancelGroupParticipants(anyLong(), anyList(), anyString(), any());
    }

    private static MatchRow match(int maxParticipants) {
        LocalDateTime startAt = LocalDateTime.now(KST).plusDays(1);
        MatchRow m = new MatchRow();
        m.setMatchId(MATCH_ID);
        m.setFacilityId(FACILITY_ID);
        m.setSportId(SPORT_ID);
        m.setStatus("모집 중");
        m.setStartAt(startAt);
        m.setEndAt(startAt.plusHours(2));
        m.setMaxParticipants(maxParticipants);
        return m;
    }

    private static TeamClubRow club(long sportId) {
        TeamClubRow c = new TeamClubRow();
        c.setClubId(CLUB_ID);
        c.setClubName("스매시클럽");
        c.setSportId(sportId);
        return c;
    }

    private static TeamMemberRow member(long clubMemberId, long userId, String nickname) {
        TeamMemberRow m = new TeamMemberRow();
        m.setClubId(CLUB_ID);
        m.setClubMemberId(clubMemberId);
        m.setUserId(userId);
        m.setNickname(nickname);
        m.setRole("동호회원");
        return m;
    }

    private static GroupMemberRow group(long applicationId, long userId, String status) {
        GroupMemberRow g = new GroupMemberRow();
        g.setApplicationId(applicationId);
        g.setUserId(userId);
        g.setNickname("회원" + userId);
        g.setStatus(status);
        return g;
    }

    private static MatchApplication groupRow(long applicationId, long userId, long groupId) {
        MatchApplication a = MatchApplication.teamMember(MATCH_ID, userId, CLUB_ID, groupId,
                "RCPT-" + applicationId, LocalDateTime.now(KST));
        a.setApplicationId(applicationId);
        return a;
    }
}
