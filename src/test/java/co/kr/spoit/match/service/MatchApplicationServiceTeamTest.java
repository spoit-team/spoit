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
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;

import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.match.dto.GroupMemberRow;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.TeamClubRow;
import co.kr.spoit.match.dto.TeamMemberRow;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.service.MemberAccountLockService;

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
    private MemberAccountLockService accountLockService;
    private ClubService clubService;
    private MatchApplicationService service;

    private final List<MatchApplication> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mapper = mock(MatchApplicationMapper.class);
        memberMapper = mock(MemberMapper.class);
        sportRatingMapper = mock(SportRatingMapper.class);
        accountLockService = mock(MemberAccountLockService.class);
        clubService = mock(ClubService.class);
        service = new MatchApplicationService(mapper, memberMapper, sportRatingMapper,
                mock(MatchService.class), mock(FacilityReservationService.class), accountLockService,
                clubService);

        Member leader = new Member();
        leader.setUserId(LEADER_ID);
        given(memberMapper.findById(LEADER_ID)).willReturn(Optional.of(leader));
        given(accountLockService.lockById(anyLong())).willReturn(leader);
        given(clubService.lockActiveClub(CLUB_ID)).willReturn(activeClub());
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
        // 계정 잠금은 기본적으로 대표·팀원 모두 미탈퇴·무정지 상태를 돌려준다.
        // (재스텁 시 Mockito가 null 인자로 호출하므로 null을 먼저 걸러낸다.)
        given(accountLockService.lockAllById(any()))
                .willAnswer(invocation -> {
                    List<Long> requested = invocation.getArgument(0);
                    if (requested == null) {
                        return List.of();
                    }
                    return requested.stream().map(MatchApplicationServiceTeamTest::account).toList();
                });
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

    @Test
    @DisplayName("시설 → 매칭방 → 동호회 → 포함 계정 전체(식별자 오름차순) 순으로 잠근다")
    void locksFacilityMatchClubThenAllIncludedAccounts() {
        given(mapper.countOccupiedSeats(anyLong(), anyList(), anyString())).willReturn(0);
        given(accountLockService.lockAllById(any()))
                .willReturn(List.of(account(LEADER_ID), account(21L), account(22L)));

        service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L, 102L));

        ArgumentCaptor<List<Long>> lockedIds = ArgumentCaptor.forClass(List.class);
        verify(accountLockService).lockAllById(lockedIds.capture());
        // 대표와 선택한 팀원이 모두 잠금 대상이다(대표 본인 포함).
        assertThat(lockedIds.getValue()).containsExactlyInAnyOrder(LEADER_ID, 21L, 22L);

        InOrder order = inOrder(mapper, clubService, accountLockService);
        order.verify(mapper).lockMatch(MATCH_ID);
        // 동호회 행 잠금이 계정 잠금보다 먼저여야 회장 위임(동호회 → 계정)과 순서가 같아 교착이 없다.
        order.verify(clubService).lockActiveClub(CLUB_ID);
        order.verify(accountLockService).lockAllById(any());
        order.verify(mapper).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("팀 매치 신청은 매칭방을 잠근 뒤 동호회를 잠가 매칭방 → 동호회 역순이 생기지 않는다")
    void doesNotLockClubBeforeMatch() {
        given(mapper.countOccupiedSeats(anyLong(), anyList(), anyString())).willReturn(0);

        service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L));

        InOrder order = inOrder(mapper, clubService);
        order.verify(mapper).lockMatch(MATCH_ID);
        order.verify(clubService).lockActiveClub(CLUB_ID);
    }

    @Test
    @DisplayName("잠근 동호회가 없거나 해체됐으면 팀 신청을 만들지 않는다")
    void rejectsDissolvedClubAtLock() {
        given(clubService.lockActiveClub(CLUB_ID)).willReturn(null);

        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("팀으로 신청할 수 있는 동호회가 아닙니다.");
        verify(accountLockService, never()).lockAllById(any());
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("동호회를 잠근 뒤 소속·종목을 다시 확인하고, 탈퇴한 동호회원은 신청하지 않는다")
    void revalidatesMembershipAfterClubLock() {
        given(mapper.findTeamClub(CLUB_ID, LEADER_ID)).willReturn(null);

        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("팀으로 신청할 수 있는 동호회가 아닙니다.");
        verify(clubService).lockActiveClub(CLUB_ID);
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("포함된 팀원이 그 사이 탈퇴했으면 잠근 값으로 판단해 팀 신청 행을 만들지 않는다")
    void rejectsMemberWithdrawnBeforeAccountLock() {
        given(mapper.countOccupiedSeats(anyLong(), anyList(), anyString())).willReturn(0);
        Member withdrawn = account(21L);
        withdrawn.setDeletedAt(LocalDateTime.now(KST));
        given(accountLockService.lockAllById(any()))
                .willReturn(List.of(account(LEADER_ID), withdrawn, account(22L)));

        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L, 102L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("선택한 팀원 정보를 확인할 수 없습니다. 매치 상세에서 다시 선택해 주세요.");
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    @Test
    @DisplayName("대표 본인이 그 사이 탈퇴했으면 잠근 값으로 판단해 팀 신청 행을 만들지 않는다")
    void rejectsLeaderWithdrawnBeforeAccountLock() {
        given(mapper.countOccupiedSeats(anyLong(), anyList(), anyString())).willReturn(0);
        Member withdrawn = account(LEADER_ID);
        withdrawn.setDeletedAt(LocalDateTime.now(KST));
        given(accountLockService.lockAllById(any()))
                .willReturn(List.of(withdrawn, account(21L), account(22L)));

        assertThatThrownBy(() -> service.applyTeam(MATCH_ID, LEADER_ID, CLUB_ID, List.of(101L, 102L)))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("탈퇴한 회원은 신청할 수 없습니다.");
        verify(mapper, never()).insert(any(MatchApplication.class));
    }

    /* ---------------- 승인·반려 (시설 관리자) ---------------- */

    private static final long MANAGER_ID = 7L;

    @Test
    @DisplayName("팀 신청 행을 승인하면 그 팀원 한 명만 확정하고 참가자 행을 만든다")
    void confirmsOnlyOneTeamMember() {
        given(mapper.findById(501L)).willReturn(groupRow(501L, 21L, 500L));
        given(mapper.countManagedApplication(501L, MANAGER_ID)).willReturn(1);
        given(mapper.markConfirmed(eq(501L), eq("대기"), eq("확정"), any())).willReturn(1);

        service.confirm(501L, MANAGER_ID);

        verify(mapper).insertParticipant(eq(501L), eq(MATCH_ID), eq(21L), eq("확정"), any(), any());
        verify(mapper, times(1)).markConfirmed(anyLong(), anyString(), anyString(), any());
        verify(mapper, never()).findGroupMembers(anyLong());
    }

    @Test
    @DisplayName("개인 신청 승인은 그 신청 한 건만 참가자 행으로 만든다")
    void confirmsIndividual() {
        MatchApplication individual = MatchApplication.individual(MATCH_ID, LEADER_ID, "RCPT-1", LocalDateTime.now(KST));
        individual.setApplicationId(600L);
        given(mapper.findById(600L)).willReturn(individual);
        given(mapper.countManagedApplication(600L, MANAGER_ID)).willReturn(1);
        given(mapper.markConfirmed(eq(600L), eq("대기"), eq("확정"), any())).willReturn(1);

        service.confirm(600L, MANAGER_ID);

        verify(mapper).insertParticipant(eq(600L), eq(MATCH_ID), eq(LEADER_ID), eq("확정"), any(), any());
    }

    @Test
    @DisplayName("이미 처리된 신청은 승인하지 않는다")
    void rejectsProcessedConfirm() {
        given(mapper.findById(500L)).willReturn(groupRow(500L, LEADER_ID, 500L));
        given(mapper.countManagedApplication(500L, MANAGER_ID)).willReturn(1);
        given(mapper.markConfirmed(eq(500L), eq("대기"), eq("확정"), any())).willReturn(0);

        assertThatThrownBy(() -> service.confirm(500L, MANAGER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 처리된 신청입니다.");
        verify(mapper, never()).insertParticipant(anyLong(), anyLong(), anyLong(), anyString(), any(), any());
    }

    @Test
    @DisplayName("담당 시설이 아닌 신청은 없는 신청과 같이 거절하고 매칭방을 잠그지 않는다")
    void rejectsForeignManager() {
        given(mapper.findById(501L)).willReturn(groupRow(501L, 21L, 500L));
        given(mapper.countManagedApplication(501L, MANAGER_ID)).willReturn(0);

        assertThatThrownBy(() -> service.confirm(501L, MANAGER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("신청 정보를 찾을 수 없습니다.");
        assertThatThrownBy(() -> service.reject(501L, MANAGER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("신청 정보를 찾을 수 없습니다.");
        verify(mapper, never()).lockMatch(anyLong());
    }

    @Test
    @DisplayName("모집 중이 아닌 경기(팀 배정 완료 등)의 신청은 승인·반려하지 않는다")
    void rejectsDecisionWhenNotRecruiting() {
        MatchRow matched = match(10);
        matched.setStatus("매칭 완료");
        given(mapper.lockMatch(MATCH_ID)).willReturn(matched);
        given(mapper.findById(501L)).willReturn(groupRow(501L, 21L, 500L));
        given(mapper.countManagedApplication(501L, MANAGER_ID)).willReturn(1);

        assertThatThrownBy(() -> service.confirm(501L, MANAGER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("모집 중인 경기의 신청만 승인하거나 반려할 수 있습니다.");
        assertThatThrownBy(() -> service.reject(501L, MANAGER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("모집 중인 경기의 신청만 승인하거나 반려할 수 있습니다.");
        verify(mapper, never()).markConfirmed(anyLong(), anyString(), anyString(), any());
        verify(mapper, never()).markRejected(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("팀 신청 행을 반려하면 그 팀원 한 명만 반려하고 참가자 행은 만들지 않는다")
    void rejectsOnlyOneTeamMember() {
        given(mapper.findById(502L)).willReturn(groupRow(502L, 22L, 500L));
        given(mapper.countManagedApplication(502L, MANAGER_ID)).willReturn(1);
        given(mapper.markRejected(502L, "대기", "반려")).willReturn(1);

        service.reject(502L, MANAGER_ID);

        verify(mapper, times(1)).markRejected(anyLong(), anyString(), anyString());
        verify(mapper, never()).insertParticipant(anyLong(), anyLong(), anyLong(), anyString(), any(), any());
        verify(mapper, never()).findGroupMembers(anyLong());
    }

    @Test
    @DisplayName("이미 처리된 신청은 반려하지 않는다")
    void rejectsProcessedReject() {
        given(mapper.findById(502L)).willReturn(groupRow(502L, 22L, 500L));
        given(mapper.countManagedApplication(502L, MANAGER_ID)).willReturn(1);
        given(mapper.markRejected(502L, "대기", "반려")).willReturn(0);

        assertThatThrownBy(() -> service.reject(502L, MANAGER_ID))
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

    /* ---------------- 팀원 일부 취소 ---------------- */

    /** 대표 500(LEADER_ID), 팀원 501(21), 502(22) 모두 확정인 묶음 */
    private void givenActiveGroup() {
        given(mapper.findById(500L)).willReturn(groupRow(500L, LEADER_ID, 500L));
        given(mapper.lockGroupMembers(500L)).willReturn(List.of(
                group(500L, LEADER_ID, "확정"), group(501L, 21L, "확정"), group(502L, 22L, "확정")));
        given(mapper.markCancelled(anyLong(), anyList(), eq("취소"), eq(LEADER_ID), any())).willReturn(1);
    }

    @Test
    @DisplayName("대표가 고른 팀원만 취소하고 묶음과 대표는 그대로 둔다")
    void leaderCancelsSelectedMembers() {
        givenActiveGroup();

        service.cancel(500L, LEADER_ID, List.of(502L), null);

        verify(mapper).markCancelled(eq(502L), anyList(), eq("취소"), eq(LEADER_ID), any());
        verify(mapper).cancelParticipants(eq(502L), anyList(), eq("취소"), any());
        verify(mapper, times(1)).markCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
        verify(mapper, never()).markGroupCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
        verify(mapper, never()).moveGroup(anyList(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("대표가 빠지면 본인과 고른 팀원을 취소하고 남은 팀원을 새 대표 묶음으로 옮긴다")
    void leaderLeavesWithNewLeader() {
        givenActiveGroup();
        given(mapper.moveGroup(List.of(501L), 500L, 501L)).willReturn(1);

        service.cancel(500L, LEADER_ID, List.of(500L, 502L), 501L);

        verify(mapper).markCancelled(eq(500L), anyList(), eq("취소"), eq(LEADER_ID), any());
        verify(mapper).markCancelled(eq(502L), anyList(), eq("취소"), eq(LEADER_ID), any());
        verify(mapper).cancelParticipants(eq(500L), anyList(), eq("취소"), any());
        verify(mapper).moveGroup(List.of(501L), 500L, 501L);
        verify(mapper, never()).markGroupCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("대표가 빠지는데 새 대표를 고르지 않았거나 남는 팀원이 아니면 취소하지 않는다")
    void leaderLeavingRequiresRemainingNewLeader() {
        givenActiveGroup();

        assertThatThrownBy(() -> service.cancel(500L, LEADER_ID, List.of(500L), null))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("대표가 빠지면 남는 팀원 중 새 대표를 선택해 주세요.");
        assertThatThrownBy(() -> service.cancel(500L, LEADER_ID, List.of(500L, 502L), 502L))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("대표가 빠지면 남는 팀원 중 새 대표를 선택해 주세요.");
        verify(mapper, never()).markCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("활성 팀원을 모두 고르면 팀 전체 취소로 처리한다")
    void selectingEveryoneCancelsWholeGroup() {
        givenActiveGroup();
        given(mapper.markGroupCancelled(eq(500L), anyList(), eq("취소"), eq(LEADER_ID), any())).willReturn(3);

        service.cancel(500L, LEADER_ID, List.of(500L, 501L, 502L), null);

        verify(mapper).cancelGroupParticipants(eq(500L), anyList(), eq("취소"), any());
        verify(mapper, never()).markCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("이미 취소됐거나 다른 묶음의 팀원이 섞여 있으면 취소하지 않는다")
    void rejectsInactiveOrForeignMember() {
        given(mapper.findById(500L)).willReturn(groupRow(500L, LEADER_ID, 500L));
        given(mapper.lockGroupMembers(500L)).willReturn(List.of(
                group(500L, LEADER_ID, "확정"), group(501L, 21L, "취소"), group(502L, 22L, "확정")));

        assertThatThrownBy(() -> service.cancel(500L, LEADER_ID, List.of(501L), null))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 취소되었거나 이 팀 신청에 없는 팀원이 포함되어 있습니다.");
        assertThatThrownBy(() -> service.cancel(500L, LEADER_ID, List.of(999L), null))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 취소되었거나 이 팀 신청에 없는 팀원이 포함되어 있습니다.");
        verify(mapper, never()).markCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("이미 대표를 넘기고 빠진 이전 대표는 잠금 뒤 다시 확인해 막는다")
    void formerLeaderCannotCancelAfterHandover() {
        given(mapper.findById(500L)).willReturn(groupRow(500L, LEADER_ID, 500L));
        given(mapper.lockGroupMembers(500L)).willReturn(List.of(group(500L, LEADER_ID, "취소")));

        assertThatThrownBy(() -> service.cancel(500L, LEADER_ID, List.of(501L), null))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 취소되었거나 처리된 신청입니다.");
        verify(mapper, never()).markCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("팀원은 팀원을 골라도 취소할 수 없다")
    void memberCannotCancelSelectedMembers() {
        given(mapper.findById(501L)).willReturn(groupRow(501L, 21L, 500L));
        given(mapper.findById(500L)).willReturn(groupRow(500L, LEADER_ID, 500L));

        assertThatThrownBy(() -> service.cancel(501L, 21L, List.of(501L), null))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("팀 신청은 신청한 대표만 취소할 수 있습니다.");
        verify(mapper, never()).lockMatch(anyLong());
    }

    @Test
    @DisplayName("남은 팀원을 새 묶음으로 다 옮기지 못하면 실패시켜 되돌린다")
    void rejectsPartialGroupMove() {
        givenActiveGroup();
        given(mapper.moveGroup(List.of(501L, 502L), 500L, 501L)).willReturn(1);

        assertThatThrownBy(() -> service.cancel(500L, LEADER_ID, List.of(500L), 501L))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("팀 신청 정보가 바뀌었습니다. 다시 시도해 주세요.");
    }

    @Test
    @DisplayName("개인 신청에는 팀원을 골라 취소할 수 없다")
    void individualApplicationRejectsMemberSelection() {
        MatchApplication individual = groupRow(600L, LEADER_ID, 600L);
        individual.setGroupApplicationId(null);
        given(mapper.findById(600L)).willReturn(individual);

        assertThatThrownBy(() -> service.cancel(600L, LEADER_ID, List.of(600L), null))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("취소할 수 있는 신청이 아닙니다.");
        verify(mapper, never()).lockMatch(anyLong());
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

    private static Member account(long userId) {
        Member m = new Member();
        m.setUserId(userId);
        return m;
    }

    private static ClubSummary activeClub() {
        ClubSummary club = new ClubSummary();
        club.setClubId(CLUB_ID);
        club.setSportId(SPORT_ID);
        club.setRecruitStatus("모집중");
        return club;
    }
}
