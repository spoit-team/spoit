package co.kr.spoit.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.kr.spoit.club.domain.Club;
import co.kr.spoit.club.domain.ClubJoinRequest;
import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubSportCount;
import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubCreateForm;
import co.kr.spoit.club.dto.ClubDetailResponse;
import co.kr.spoit.club.dto.ClubManageResponse;
import co.kr.spoit.club.dto.ClubSearchForm;
import co.kr.spoit.club.dto.ClubSportTabResponse;
import co.kr.spoit.club.dto.MyClubResponse;
import co.kr.spoit.club.mapper.ClubMapper;
import co.kr.spoit.club.service.ClubServiceImpl;
import co.kr.spoit.club.service.ClubService.ClubNameExistException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClubServiceTest {

    @Mock
    private ClubMapper clubMapper;

    @InjectMocks
    private ClubServiceImpl service;

    // 목록 조회
    @Test
    @DisplayName("모집 중인 동호회는 모집 중 카드로 바꾼다")
    void 모집중_동호회를_카드로_바꾼다() {
        ClubSummary club = club("모집중", "2,4", 19, 21);
        when(clubMapper.selectClubs(any(ClubSearchForm.class))).thenReturn(List.of(club));

        ClubCardResponse card = service.findClubCards(new ClubSearchForm()).get(0);

        assertThat(card.getSportKey()).isEqualTo("tennis");
        assertThat(card.isRecruiting()).isTrue();
        assertThat(card.getStatusLabel()).isEqualTo("팀원 모집 중");
        assertThat(card.getTime()).isEqualTo("화, 목 19:00 - 21:00");
        assertThat(card.getFooterNote()).isEqualTo("모집 자리: 7명");
    }

    @Test
    @DisplayName("종목 탭은 전체가 맨 앞이고 고른 종목만 켜진다")
    void 종목_탭은_전체가_맨_앞이고_고른_종목만_켜진다() {
        // 가짜 DB 결과: 테니스 3개, 족구 0개
        when(clubMapper.selectClubSportCounts())
                .thenReturn(List.of(sportCount(1L, "테니스", 3), sportCount(3L, "족구", 0)));

        // 테니스(1번)를 고른 상태로 탭 만들기
        List<ClubSportTabResponse> tabs = service.findSportTabs(1L);

        assertThat(tabs).hasSize(3); // 전체 + 종목 2개
        assertThat(tabs.get(0).getName()).isEqualTo("전체");
        assertThat(tabs.get(0).getCount()).isEqualTo(3); // 3 + 0
        assertThat(tabs.get(0).isActive()).isFalse(); // 종목을 골랐으니 전체는 꺼짐
        assertThat(tabs.get(1).isActive()).isTrue(); // 고른 테니스만 켜짐
        assertThat(tabs.get(1).getUrl()).isEqualTo("/teams?sportId=1");
        assertThat(tabs.get(2).getCount()).isEqualTo(0); // 동호회 없는 종목도 탭은 있음
        assertThat(tabs.get(2).isActive()).isFalse();
    }

    @Test
    @DisplayName("마감된 동호회는 모집 마감 카드로 바꾼다")
    void 마감된_동호회를_카드로_바꾼다() {
        ClubSummary club = club("마감", "6,7", 10, 12);
        when(clubMapper.selectClubs(any(ClubSearchForm.class))).thenReturn(List.of(club));

        ClubCardResponse card = service.findClubCards(new ClubSearchForm()).get(0);

        assertThat(card.isRecruiting()).isFalse();
        assertThat(card.getStatusLabel()).isEqualTo("모집 마감");
        assertThat(card.getTime()).isEqualTo("토, 일 10:00 - 12:00");
        assertThat(card.getFooterNote()).isEqualTo("모집이 마감된 팀입니다.");
    }

    @Test
    @DisplayName("지역이 없으면 지역 미정으로 표시한다")
    void 지역이_없으면_지역_미정으로_표시한다() {
        ClubSummary club = club("모집중", "2,4", 19, 21);
        club.setAreaLabel(null);
        when(clubMapper.selectClubs(any(ClubSearchForm.class))).thenReturn(List.of(club));

        ClubCardResponse card = service.findClubCards(new ClubSearchForm()).get(0);

        assertThat(card.getAreaLabel()).isEqualTo("지역 미정");
    }

    // 상세조회
    @Test
    @DisplayName("동호회 상세 정보와 동호회장을 화면 값으로 바꾼다")
    void 동호회를_상세_화면으로_바꾼다() {
        ClubSummary club = detailClub();
        club.setIntro("주말 농구 모임");
        when(clubMapper.selectClubDetail(1L)).thenReturn(club);
        when(clubMapper.selectClubMembers(1L)).thenReturn(List.of(
                member("윤드롭", "동호회원"),
                member("이슛터", "동호회장")));

        ClubDetailResponse detail = service.findClubDetail(1L, 7L);

        assertThat(detail.getMarkTone()).isEqualTo(2);
        assertThat(detail.getAreaLabel()).isEqualTo("강남구");
        assertThat(detail.getFounded()).isEqualTo("2026.10.20");
        assertThat(detail.getIntro()).isEqualTo("주말 농구 모임");
        assertThat(detail.getLeaderName()).isEqualTo("이슛터");
        assertThat(detail.getLeaderInitials()).isEqualTo("이슛");
        assertThat(detail.getMembers()).hasSize(2);
        assertThat(detail.getFooterNote()).isEqualTo("모집 자리: 7명");
    }

    @Test
    @DisplayName("없는 동호회를 조회하면 null을 돌려주고 조회하지 않는다")
    void 없는_동호회면_null을_돌려준다() {
        when(clubMapper.selectClubDetail(999L)).thenReturn(null);

        assertThat(service.findClubDetail(999L, 7L)).isNull();
        verify(clubMapper, never()).selectClubMembers(999L);
    }

    // 동호회 만들기
    @Test
    @DisplayName("동호회를 만들면 동호회, 지역, 동호회장 순서로 저장하고 번호 저장")
    void 동호회_만들기_성공() throws Exception {
        when(clubMapper.existsSport(1L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.existsActiveClubName("강남 농구 크루")).thenReturn(false);
        doAnswer(invocation -> {
            Club club = invocation.getArgument(0);
            ReflectionTestUtils.setField(club, "clubId", 42L);
            return null;
        }).when(clubMapper).insertClub(any(Club.class));

        Long clubId = service.createClub(7L, validForm());

        assertThat(clubId).isEqualTo(42L);
        InOrder order = inOrder(clubMapper);
        order.verify(clubMapper).insertClub(any(Club.class));
        order.verify(clubMapper).insertClubRegion(42L, 3L);
        order.verify(clubMapper).insertClubLeader(42L, 7L);
    }

    @Test
    @DisplayName("동호회 이름이 공백뿐이면 저장하지 않는다")
    void 동호회_이름이_비면_거부한다() {
        ClubCreateForm form = validForm();
        form.setClubName("    ");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.createClub(7L, form));

        assertThat(e.getMessage()).isEqualTo("팀 이름은 1~30자로 입력해 주세요.");
        verify(clubMapper, never()).insertClub(any(Club.class));
    }

    @Test
    @DisplayName("활성 동호회 이름이 이미 있으면 저장하지 않는다")
    void 동호회_이름이_중복이면_거부한다() {
        when(clubMapper.existsSport(1L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.existsActiveClubName("강남 농구 크루")).thenReturn(true);

        assertThrows(ClubNameExistException.class, () -> service.createClub(7L, validForm()));

        verify(clubMapper, never()).insertClub(any(Club.class));
    }

    @Test
    @DisplayName("동시에 같은 이름이 저장되면 이름 중복 예외로 바꾼다 ")
    void 동시_저장_충돌이면_이름_중복_예외로_바꾼다() {
        when(clubMapper.existsSport(1L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.existsActiveClubName("강남 농구 크루")).thenReturn(false);
        doThrow(new DuplicateKeyException("uk_club_name"))
                .when(clubMapper).insertClub(any(Club.class));

        assertThrows(ClubNameExistException.class, () -> service.createClub(7L, validForm()));

        verify(clubMapper, never()).insertClubRegion(any(), any());
        verify(clubMapper, never()).insertClubLeader(any(), any());
    }

    @Test
    @DisplayName("속한 동호회가 없으면 null을 돌려주고 더 조회하지 않는다")
    void 관리할_동호회가_없으면_null을_돌려준다() {
        when(clubMapper.selectMyClubs(7L)).thenReturn(List.of());

        assertThat(service.findManagedClub(7L, null)).isNull();
        verify(clubMapper, never()).existsActiveMember(any(), any());
    }

    @Test
    @DisplayName("번호 없이 들어오면 내 동호회 목록 맨 앞을 보여주고 나와 동호회장을 표시한다")
    void 번호가_없으면_첫_동호회를_보여준다() {
        ClubSummary second = club("모집중", "2,4", 19, 21);
        second.setClubId(2L);
        when(clubMapper.selectMyClubs(7L)).thenReturn(List.of(club("모집중", "2,4", 19, 21), second));
        when(clubMapper.existsActiveMember(1L, 7L)).thenReturn(true);
        when(clubMapper.selectClubDetail(1L)).thenReturn(detailClub());
        // 로그인한 사람(7)이 동호회장
        ClubMember me = member("이슛터", "동호회장");
        me.setUserId(7L);
        ClubMember other = member("윤드롭", "동호회원");
        other.setUserId(8L);
        when(clubMapper.selectClubMembers(1L)).thenReturn(List.of(me, other));

        ClubManageResponse managed = service.findManagedClub(7L, null);

        assertThat(managed.getClubId()).isEqualTo(1L);
        assertThat(managed.isLeader()).isTrue();
        assertThat(managed.getLeaderName()).isEqualTo("이슛터");
        assertThat(managed.getMemberCount()).isEqualTo(3);
        assertThat(managed.getMaxMembers()).isEqualTo(10);
        assertThat(managed.getMembers().get(0).isCurrent()).isTrue();
        assertThat(managed.getMembers().get(1).isCurrent()).isFalse();
        assertThat(managed.getMembers().get(0).getJoined()).isEqualTo("2026.10.20");
    }

    @Test
    @DisplayName("모집중이어도 정원이 차면 관리 화면에 정원이 찼다고 표시한다")
    void 정원이_찬_동호회는_관리_화면에_정원_마감으로_표시한다() {
        ClubSummary club = detailClub();
        // 정원 10명 = 현재 인원 10명
        club.setMemberCount(10);
        when(clubMapper.existsActiveMember(1L, 7L)).thenReturn(true);
        when(clubMapper.selectClubDetail(1L)).thenReturn(club);
        when(clubMapper.selectClubMembers(1L)).thenReturn(List.of(member("이슛터", "동호회장")));

        ClubManageResponse managed = service.findManagedClub(7L, 1L);

        assertThat(managed.getRecruitLabel()).isEqualTo("모집 마감 (정원이 찼어요)");
    }

    @Test
    @DisplayName("내 동호회 목록에서 선택된 동호회만 강조한다")
    void 선택된_동호회만_강조한다() {
        ClubSummary second = club("모집중", "2,4", 19, 21);
        second.setClubId(2L);
        when(clubMapper.selectMyClubs(7L)).thenReturn(List.of(club("모집중", "2,4", 19, 21), second));

        List<MyClubResponse> clubs = service.findMyClubs(7L, 2L);

        assertThat(clubs).hasSize(2);
        assertThat(clubs.get(0).isActive()).isFalse();
        assertThat(clubs.get(1).isActive()).isTrue();
    }

    @Test
    @DisplayName("가입 신청하면 빈 자기소개는 NULL로 저장한다")
    void 가입_신청_성공() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.selectMyPendingJoinRequestId(1L, 7L)).thenReturn(null);
        when(clubMapper.countActiveMembers(1L)).thenReturn(3);

        service.applyJoin(7L, 1L, "   ");

        verify(clubMapper).insertJoinRequest(1L, 7L, null);
    }

    @Test
    @DisplayName("이미 대기중인 신청이 있으면 저장하지 않는다")
    void 대기중_신청이_있으면_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.selectMyPendingJoinRequestId(1L, 7L)).thenReturn(5L);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.applyJoin(7L, 1L, "안녕하세요"));

        assertThat(exception.getMessage()).isEqualTo("이미 가입 신청을 보냈습니다.");
        verify(clubMapper, never()).insertJoinRequest(any(), any(), any());
    }

    @Test
    @DisplayName("가입 신청을 승인하면 상태를 바꾼 뒤 동호회원으로 추가한다.")
    void 가입_신청_승인_성공() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectPendingJoinRequestById(1L, 5L)).thenReturn(pendingRequest(8L));
        when(clubMapper.countActiveMembers(1L)).thenReturn(3);

        service.approveJoin(7L, 1L, 5L);

        InOrder order = inOrder(clubMapper);
        order.verify(clubMapper).updateJoinRequestStatus(5L, "승인");
        order.verify(clubMapper).insertJoinedMember(1L, 8L);
    }

    @Test
    @DisplayName("동호회장이 아니면 가입 신청을 처리하지 않는다")
    void 동호회장이_아니면_승인을_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveJoin(7L, 1L, 5L));

        assertThat(exception.getMessage()).isEqualTo("팀장만 가입 신청을 처리할 수 있습니다.");
        verify(clubMapper, never()).updateJoinRequestStatus(any(), any());
    }

    @Test
    @DisplayName("정원이 가득 차면 승인하지 않는다")
    void 정원이_가득_차면_승인을_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectPendingJoinRequestById(1L, 5L)).thenReturn(pendingRequest(8L));
        when(clubMapper.countActiveMembers(1L)).thenReturn(10);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveJoin(7L, 1L, 5L));

        assertThat(exception.getMessage()).isEqualTo("정원이 가득 찬 팀입니다.");
        verify(clubMapper, never()).updateJoinRequestStatus(any(), any());
        verify(clubMapper, never()).insertJoinedMember(any(), any());
    }

    @Test
    @DisplayName("가입 신청을 거절하면 상태만 바꾸고 동호회원으로 추가하지 않는다")
    void 가입_신청_거절_성공() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectPendingJoinRequestById(1L, 5L)).thenReturn(pendingRequest(8L));

        service.rejectJoin(7L, 1L, 5L);

        verify(clubMapper).updateJoinRequestStatus(5L, "거절");
        verify(clubMapper, never()).insertJoinedMember(any(), any());
    }

    @Test
    @DisplayName("내 대기중 신청이 아니면 취소하지 못한다")
    void 처리된_신청은_취소를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.cancelJoinRequest(1L, 5L, 8L)).thenReturn(0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.cancelJoin(8L, 1L, 5L));

        assertThat(exception.getMessage()).isEqualTo("이미 처리된 가입 신청입니다.");
    }

    @Test
    @DisplayName("동호회장을 위임하면 받을 동호회원을 동호회장으로 바꾼 뒤 나를 동호회원으로 바꾼다")
    void 동호회장_위임_성공() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.updateRoleToLeader(1L, 3L)).thenReturn(1);

        service.transferLeader(7L, 1L, 3L);

        InOrder order = inOrder(clubMapper);
        order.verify(clubMapper).updateRoleToLeader(1L, 3L);
        order.verify(clubMapper).updateRoleToMember(1L, 7L);
    }

    @Test
    @DisplayName("동호회장이 아니면 위임하지 않는다")
    void 동호회장이_아니면_위임을_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transferLeader(7L, 1L, 3L));

        assertThat(exception.getMessage()).isEqualTo("팀장만 위임할 수 있습니다.");
        verify(clubMapper, never()).updateRoleToLeader(any(), any());
        verify(clubMapper, never()).updateRoleToMember(any(), any());
    }

    @Test
    @DisplayName("이 동호회의 활성 동호회원이 아니면 위임하지 않는다")
    void 받을_동호회원이_없으면_위임을_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.updateRoleToLeader(1L, 99L)).thenReturn(0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transferLeader(7L, 1L, 99L));

        assertThat(exception.getMessage()).isEqualTo("위임할 수 없는 팀원입니다.");
        verify(clubMapper, never()).updateRoleToMember(any(), any());
    }

    @Test
    @DisplayName("없거나 해체된 동호회면 위임하지 않는다")
    void 해체된_동호회는_위임을_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transferLeader(7L, 1L, 3L));

        assertThat(exception.getMessage()).isEqualTo("팀 정보를 찾을 수 없습니다.");
        verify(clubMapper, never()).updateRoleToLeader(any(), any());
    }

    @Test
    @DisplayName("동호회원이 탈퇴하면 탈퇴로 기록한다")
    void 동호회_탈퇴_성공() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.updateMemberLeave(1L, 7L)).thenReturn(1);

        service.leaveClub(7L, 1L);

        verify(clubMapper).updateMemberLeave(1L, 7L);
    }

    @Test
    @DisplayName("동호회장은 탈퇴하지 못한다")
    void 동호회장은_탈퇴를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.leaveClub(7L, 1L));

        assertThat(exception.getMessage()).isEqualTo("팀장은 탈퇴할 수 없습니다. 팀장을 위임한 뒤 탈퇴해 주세요.");
        verify(clubMapper, never()).updateMemberLeave(any(), any());
    }

    @Test
    @DisplayName("모집 중인 팀 매치에 신청되어 있으면 탈퇴하지 못한다")
    void 팀_매치_신청중이면_탈퇴를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsRecruitingTeamMatch(1L, 7L)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.leaveClub(7L, 1L));

        assertThat(exception.getMessage()).isEqualTo("모집 중인 팀 매치에 신청되어 있어 탈퇴할 수 없습니다.");
        verify(clubMapper, never()).updateMemberLeave(any(), any());
    }

    @Test
    @DisplayName("팀원을 추방하면 앞뒤 공백을 지운 사유로 기록한다")
    void 동호회원_내보내기_성공() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectActiveMemberById(1L, 3L)).thenReturn(kickTarget(8L));

        service.kickMember(7L, 1L, 3L, "  연락 없이 3회 불참  ");

        verify(clubMapper).updateMemberKick(1L, 3L, "연락 없이 3회 불참");
    }

    @Test
    @DisplayName("추방 사유가 공백뿐이면 잠그기 전에 거부한다")
    void 공백_사유는_내보내기를_거부한다() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.kickMember(7L, 1L, 3L, "   "));

        assertThat(exception.getMessage()).isEqualTo("추방 사유를 1~200자로 입력해 주세요.");
        verify(clubMapper, never()).selectActiveClubForUpdate(any());
        verify(clubMapper, never()).updateMemberKick(any(), any(), any());
    }

    @Test
    @DisplayName("동호회장이 아니면 팀원을 추방하지 못한다")
    void 동호회장이_아니면_내보내기를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.kickMember(7L, 1L, 3L, "불참"));

        assertThat(exception.getMessage()).isEqualTo("팀장만 팀원을 추방할 수 있습니다.");
        verify(clubMapper, never()).updateMemberKick(any(), any(), any());
    }

    @Test
    @DisplayName("모집 중인 팀 매치에 신청된 팀원은 추방하지 못한다")
    void 팀_매치_신청중인_팀원은_내보내기를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectActiveMemberById(1L, 3L)).thenReturn(kickTarget(8L));
        when(clubMapper.existsRecruitingTeamMatch(1L, 8L)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.kickMember(7L, 1L, 3L, "불참"));

        assertThat(exception.getMessage()).isEqualTo("모집 중인 팀 매치에 신청된 팀원은 추방할 수 없습니다.");
        verify(clubMapper, never()).updateMemberKick(any(), any(), any());
    }

    @Test
    @DisplayName("동호회장 혼자면 대기 신청을 취소하고 해체로 기록한다")
    void 동호회_해체_성공() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(disbandTarget());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.countActiveMembers(1L)).thenReturn(1);

        service.disbandClub(7L, 1L, "  노원 농구회  ");

        verify(clubMapper).cancelPendingJoinRequests(1L);
        verify(clubMapper).updateLeaderDisband(1L, 7L);
        verify(clubMapper).updateClubDisband(1L);
    }

    @Test
    @DisplayName("동호회장이 아니면 해체하지 않는다")
    void 동호회장이_아니면_해체를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(disbandTarget());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.disbandClub(7L, 1L, "노원 농구회"));

        assertThat(exception.getMessage()).isEqualTo("팀장만 팀을 해체할 수 있습니다.");
        verify(clubMapper, never()).updateClubDisband(any());
    }

    @Test
    @DisplayName("입력한 이름이 다르면 해체하지 않는다")
    void 이름이_다르면_해체를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(disbandTarget());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.disbandClub(7L, 1L, "노원농구회"));

        assertThat(exception.getMessage()).isEqualTo("입력한 팀 이름이 일치하지 않습니다.");
        verify(clubMapper, never()).updateClubDisband(any());
    }

    @Test
    @DisplayName("동호회장 외 팀원이 남아 있으면 해체하지 않는다")
    void 팀원이_남아_있으면_해체를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(disbandTarget());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.countActiveMembers(1L)).thenReturn(2);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.disbandClub(7L, 1L, "노원 농구회"));

        assertThat(exception.getMessage()).isEqualTo("팀장 외 다른 팀원이 남아 있어 해체할 수 없습니다. 팀원을 먼저 정리해 주세요.");
        verify(clubMapper, never()).updateClubDisband(any());
    }

    @Test
    @DisplayName("모집 중인 팀 매치가 있으면 해체하지 않는다")
    void 팀_매치_신청중이면_해체를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(disbandTarget());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.countActiveMembers(1L)).thenReturn(1);
        when(clubMapper.existsRecruitingTeamMatch(1L, null)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.disbandClub(7L, 1L, "노원 농구회"));

        assertThat(exception.getMessage()).isEqualTo("모집 중인 팀 매치에 신청되어 있어 해체할 수 없습니다.");
        verify(clubMapper, never()).updateClubDisband(any());
    }

    @Test
    @DisplayName("정원이 찬 모집중 동호회는 모집 마감 카드로 바꾼다")
    void 정원이_찬_모집중_동호회는_모집_마감으로_바꾼다() {
        ClubSummary club = club("모집중", "2,4", 19, 21);
        club.setMemberCount(10);
        when(clubMapper.selectClubs(any(ClubSearchForm.class))).thenReturn(List.of(club));

        ClubCardResponse card = service.findClubCards(new ClubSearchForm()).get(0);

        assertThat(card.isRecruiting()).isFalse();
        assertThat(card.getStatusLabel()).isEqualTo("모집 마감");
        assertThat(card.getFooterNote()).isEqualTo("모집이 마감된 팀입니다.");
    }

    // 동호회 정보 수정
    @Test
    @DisplayName("동호회 정보를 수정하면 동호회를 고치고 지역을 지운 뒤 다시 넣는다")
    void 동호회_정보_수정_성공() throws Exception {
        when(clubMapper.existsSport(1L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClubForEdit());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);

        service.updateClub(7L, 1L, editForm());

        InOrder order = inOrder(clubMapper);
        order.verify(clubMapper).updateClub(any(Club.class));
        order.verify(clubMapper).deleteClubRegions(1L);
        order.verify(clubMapper).insertClubRegion(1L, 3L);
        // 종목을 안 바꾸면 팀 매치는 확인하지 않음
        verify(clubMapper, never()).existsRecruitingTeamMatch(any(), any());
    }

    @Test
    @DisplayName("동호회장이 아니면 수정하지 않는다")
    void 동호회장이_아니면_수정을_거부한다() {
        when(clubMapper.existsSport(1L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClubForEdit());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(false);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateClub(7L, 1L, editForm()));

        assertThat(e.getMessage()).isEqualTo("팀장만 팀 정보를 수정할 수 있습니다.");
        verify(clubMapper, never()).updateClub(any(Club.class));
    }

    @Test
    @DisplayName("최대 인원을 현재 팀원 수보다 적게 바꾸면 수정하지 않는다")
    void 정원이_현재_인원보다_적으면_수정을_거부한다() {
        when(clubMapper.existsSport(1L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClubForEdit());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        // 폼 정원 10명 < 현재 12명
        when(clubMapper.countActiveMembers(1L)).thenReturn(12);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateClub(7L, 1L, editForm()));

        assertThat(e.getMessage()).isEqualTo("최대 팀원 수는 현재 팀원 수보다 작을 수 없습니다.");
        verify(clubMapper, never()).updateClub(any(Club.class));
    }

    @Test
    @DisplayName("다른 활성 동호회와 이름이 같으면 수정하지 않는다")
    void 다른_동호회와_이름이_같으면_수정을_거부한다() {
        when(clubMapper.existsSport(1L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClubForEdit());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.existsOtherActiveClubName(1L, "강남 농구 크루")).thenReturn(true);

        assertThrows(ClubNameExistException.class, () -> service.updateClub(7L, 1L, editForm()));

        verify(clubMapper, never()).updateClub(any(Club.class));
    }

    @Test
    @DisplayName("모집 상태가 모집중, 마감이 아니면 잠그기 전에 거부한다")
    void 모집_상태_값이_틀리면_수정을_거부한다() {
        when(clubMapper.existsSport(1L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        ClubCreateForm form = editForm();
        form.setRecruitStatus("recruiting");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateClub(7L, 1L, form));

        assertThat(e.getMessage()).isEqualTo("모집 상태 값이 올바르지 않습니다.");
        verify(clubMapper, never()).selectActiveClubForUpdate(any());
    }

    @Test
    @DisplayName("모집 중인 팀 매치가 있으면 종목을 바꾸지 않는다")
    void 팀_매치가_있으면_종목_변경을_거부한다() {
        ClubCreateForm form = editForm();
        // 종목 1번 -> 2번
        form.setSportId(2L);
        when(clubMapper.existsSport(2L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClubForEdit());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.existsRecruitingTeamMatch(1L, null)).thenReturn(true);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateClub(7L, 1L, form));

        assertThat(e.getMessage()).isEqualTo("모집 중인 팀 매치가 있어 종목을 변경할 수 없습니다.");
        verify(clubMapper, never()).updateClub(any(Club.class));
    }

    @Test
    @DisplayName("팀 평균 MMR로 실력 등급을 정하고, 기록이 없으면 배치 중")
    void 팀_평균_MMR로_실력_등급을_정한다() {
        ClubSummary none = club("모집중", "2,4", 19, 21);
        ClubSummary gold = club("모집중", "2,4", 19, 21);
        gold.setAverageMmr(1200);
        ClubSummary silver = club("모집중", "2,4", 19, 21);
        silver.setAverageMmr(1199);
        when(clubMapper.selectClubs(any(ClubSearchForm.class))).thenReturn(List.of(none, gold, silver));

        List<ClubCardResponse> cards = service.findClubCards(new ClubSearchForm());

        assertThat(cards.get(0).getTierLabel()).isEqualTo("배치 중");
        assertThat(cards.get(1).getTierLabel()).isEqualTo("골드");
        assertThat(cards.get(2).getTierLabel()).isEqualTo("실버");
        assertThat(cards.get(0).getTierKey()).isEqualTo("none");
        assertThat(cards.get(1).getTierKey()).isEqualTo("gold");
        assertThat(cards.get(2).getTierKey()).isEqualTo("silver");

    }

    // 추방할 동호회원(동호회원 번호 3, 회원 번호만)
    private ClubMember kickTarget(Long userId) {
        ClubMember member = new ClubMember();
        member.setClubMemberId(3L);
        member.setUserId(userId);
        return member;
    }

    private ClubSportCount sportCount(Long sportId, String name, int clubCount) {
        ClubSportCount sport = new ClubSportCount();
        sport.setSportId(sportId);
        sport.setName(name);
        sport.setClubCount(clubCount);
        return sport;
    }

    private ClubSummary club(String recruitStatus, String activityDays, int startHour, int endHour) {
        ClubSummary club = new ClubSummary();
        club.setClubId(1L);
        club.setClubName("강남 농구 크루");
        club.setSportId(1L);
        club.setSportName("농구");
        club.setAreaLabel("강남구");
        club.setRecruitStatus(recruitStatus);
        club.setActivityDays(activityDays);
        club.setActivityStartTime(LocalTime.of(startHour, 0));
        club.setActivityEndTime(LocalTime.of(endHour, 0));
        club.setMaxMembers(10);
        club.setMemberCount(3);
        return club;
    }

    private ClubSummary detailClub() {
        ClubSummary club = club("모집중", "2,4", 19, 21);
        club.setCreatedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        return club;
    }

    private ClubMember member(String nickname, String role) {
        ClubMember member = new ClubMember();
        member.setNickname(nickname);
        member.setRole(role);
        member.setJoinedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        return member;
    }

    private ClubCreateForm validForm() {
        ClubCreateForm form = new ClubCreateForm();
        form.setClubName("강남 농구 크루");
        form.setIntro("주말 농구 모임");
        form.setMaxMembers(10);
        form.setActivityDays(List.of(4, 2, 2));
        form.setActivityStartTime(LocalTime.of(19, 0));
        form.setActivityEndTime(LocalTime.of(21, 0));
        form.setSportId(1L);
        form.setRegionId(3L);
        return form;
    }

    // 잠금 SQL이 돌려주는 동호회(번호·모집 상태·최대 인원만)
    private ClubSummary lockedClub(String recruitStatus, int maxMembers) {
        ClubSummary club = new ClubSummary();
        club.setClubId(1L);
        club.setRecruitStatus(recruitStatus);
        club.setMaxMembers(maxMembers);
        return club;
    }

    // 처리할 대기중 신청(신청 번호5, 신청자 번호만)
    private ClubJoinRequest pendingRequest(Long userId) {
        ClubJoinRequest request = new ClubJoinRequest();
        request.setJoinRequestId(5L);
        request.setUserId(userId);
        return request;
    }

    // 해체할 동호회(잠근 행에 이름까지)
    private ClubSummary disbandTarget() {
        ClubSummary club = lockedClub("모집중", 10);
        club.setClubName("노원 농구회");
        return club;
    }

    // 수정 폼: 만들기 폼 + 모집 상태
    private ClubCreateForm editForm() {
        ClubCreateForm form = validForm();
        form.setRecruitStatus("모집중");
        return form;
    }

    // 잠금 SQL이 돌려주는 수정 전 동호회(종목 1번)
    private ClubSummary lockedClubForEdit() {
        ClubSummary club = lockedClub("모집중", 10);
        club.setClubName("강남 농구 크루");
        club.setSportId(1L);
        return club;
    }

}
