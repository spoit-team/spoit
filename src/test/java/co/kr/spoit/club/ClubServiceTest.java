package co.kr.spoit.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.kr.spoit.club.domain.Club;
import co.kr.spoit.club.domain.ClubJoinRequest;
import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubSchedule;
import co.kr.spoit.club.domain.ClubSportCount;
import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.club.dto.ClubBlockRow;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubCreateForm;
import co.kr.spoit.club.dto.ClubDetailResponse;
import co.kr.spoit.club.dto.ClubManageResponse;
import co.kr.spoit.club.dto.ClubMemberHistoryView;
import co.kr.spoit.club.dto.ClubPageResponse;
import co.kr.spoit.club.dto.ClubWithdrawalBlocks;
import co.kr.spoit.club.dto.ClubSearchForm;
import co.kr.spoit.club.dto.ClubSportTabResponse;
import co.kr.spoit.club.dto.MyClubResponse;
import co.kr.spoit.club.dto.MyJoinRequestResponse;
import co.kr.spoit.club.mapper.ClubMapper;
import co.kr.spoit.club.service.ClubServiceImpl;
import co.kr.spoit.mat.dto.ParticipantHistoryView;
import co.kr.spoit.mat.mapper.ParticipantHistoryMapper;
import co.kr.spoit.mat.service.SportRatingService;
import co.kr.spoit.club.service.ClubService.ClubNameExistException;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.service.MemberAccountLockService;
import co.kr.spoit.notification.service.NotificationDispatcher;

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
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class ClubServiceTest {

    @Mock
    private ClubMapper clubMapper;

    @Mock
    private MemberAccountLockService accountLockService;

    @Mock
    private SportRatingService sportRatingService;

    @Mock
    private ParticipantHistoryMapper historyMapper;

    @Mock
    private NotificationDispatcher notificationDispatcher;

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
        assertThat(card.getStatusLabel()).isEqualTo("모집 중");
        assertThat(card.getTime()).isEqualTo("화, 목 19:00 - 21:00");

    }

    @Test
    @DisplayName("목록 첫 화면은 전체 개수로 마지막 장을 정하고, 그린 장은 마지막 장을 넘지 않는다")
    void 목록_첫_화면_마지막_장() {
        ClubSummary club = club("모집중", "2,4", 19, 21);
        when(clubMapper.selectClubs(any(ClubSearchForm.class))).thenReturn(List.of(club));
        when(clubMapper.countClubs(any(ClubSearchForm.class))).thenReturn(25);
        ClubSearchForm filters = new ClubSearchForm();
        filters.setPage(9);

        ClubPageResponse page = service.findClubPage(filters);

        assertThat(page.getClubs()).hasSize(1);
        assertThat(page.getTotalCount()).isEqualTo(25);
        assertThat(page.getLastPage()).isEqualTo(3);
        assertThat(page.getPage()).isEqualTo(3);
    }

    @Test
    @DisplayName("동호회가 0개여도 마지막 장은 1장이다")
    void 목록_0개면_1장() {
        when(clubMapper.selectClubs(any(ClubSearchForm.class))).thenReturn(List.of());
        when(clubMapper.countClubs(any(ClubSearchForm.class))).thenReturn(0);

        ClubPageResponse page = service.findClubPage(new ClubSearchForm());

        assertThat(page.getClubs()).isEmpty();
        assertThat(page.getLastPage()).isEqualTo(1);
        assertThat(page.getPage()).isEqualTo(1);
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
        assertThat(detail.getMembers()).hasSize(2);
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
        when(accountLockService.lockById(7L)).thenReturn(account(7L));
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

        assertThat(e.getMessage()).isEqualTo("동호회 이름은 1~30자로 입력해 주세요.");
        verify(clubMapper, never()).insertClub(any(Club.class));
    }

    @Test
    @DisplayName("동호회 소개가 500자를 넘으면 저장하지 않는다")
    void 동호회_소개가_500자를_넘으면_거부한다() {
        ClubCreateForm form = validForm();
        form.setIntro("가".repeat(501));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.createClub(7L, form));

        assertThat(e.getMessage()).isEqualTo("동호회 소개는 1~500자로 입력해 주세요.");
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
    @DisplayName("모집중이어도 정원이 차면 관리 화면에 모집 마감으로 표시한다")
    void 정원이_찬_동호회는_관리_화면에_정원_마감으로_표시한다() {
        ClubSummary club = detailClub();
        // 정원 10명 = 현재 인원 10명
        club.setMemberCount(10);
        when(clubMapper.existsActiveMember(1L, 7L)).thenReturn(true);
        when(clubMapper.selectClubDetail(1L)).thenReturn(club);
        when(clubMapper.selectClubMembers(1L)).thenReturn(List.of(member("이슛터", "동호회장")));

        ClubManageResponse managed = service.findManagedClub(7L, 1L);

        assertThat(managed.getRecruitLabel()).isEqualTo("모집 마감");
    }

    @Test
    @DisplayName("관리 화면에도 카드와 같은 팀 실력 등급을 표시한다")
    void 관리_화면에_팀_실력_등급을_표시한다() {
        ClubSummary club = detailClub();
        club.setAverageMmr(1200);
        when(clubMapper.existsActiveMember(1L, 7L)).thenReturn(true);
        when(clubMapper.selectClubDetail(1L)).thenReturn(club);
        when(clubMapper.selectClubMembers(1L)).thenReturn(List.of(member("이슛터", "동호회장")));

        ClubManageResponse managed = service.findManagedClub(7L, 1L);

        assertThat(managed.getTierLabel()).isEqualTo("골드");
    }

    @Test
    @DisplayName("예정 일정은 날짜·시간·장소를 화면 값으로 바꾸고 자정을 넘기면 익일로 표시한다")
    void 예정_일정을_화면_값으로_바꾼다() {
        when(clubMapper.existsActiveMember(1L, 7L)).thenReturn(true);
        when(clubMapper.selectClubDetail(1L)).thenReturn(detailClub());
        when(clubMapper.selectClubMembers(1L)).thenReturn(List.of(member("이슛터", "동호회장")));
        // 저녁 경기 1개 + 자정을 넘기는 경기 1개
        when(clubMapper.selectUpcomingSchedules(any(), any())).thenReturn(List.of(
                schedule(LocalDateTime.of(2026, 11, 7, 18, 0), LocalDateTime.of(2026, 11, 7, 20, 0)),
                schedule(LocalDateTime.of(2026, 11, 7, 23, 0), LocalDateTime.of(2026, 11, 8, 1, 0))));

        ClubManageResponse managed = service.findManagedClub(7L, 1L);

        assertThat(managed.getUpcomingSchedules()).hasSize(2);
        assertThat(managed.getUpcomingSchedules().get(0).getDate()).isEqualTo("2026-11-07");
        assertThat(managed.getUpcomingSchedules().get(0).getDateLabel()).isEqualTo("11.07 (토)");
        assertThat(managed.getUpcomingSchedules().get(0).getTime()).isEqualTo("18:00 - 20:00");
        assertThat(managed.getUpcomingSchedules().get(0).getPlace()).isEqualTo("성동 풋살장");
        assertThat(managed.getUpcomingSchedules().get(1).getTime()).isEqualTo("23:00 - 익일 01:00");
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
    @DisplayName("내 대기중 가입 신청을 동호회 이름과 신청일로 바꾼다")
    void 내_가입_신청을_화면용으로_바꾼다() {
        ClubJoinRequest request = new ClubJoinRequest();
        request.setJoinRequestId(5L);
        request.setClubId(1L);
        request.setClubName("강남 풋살 클럽");
        request.setRequestedAt(LocalDateTime.of(2026, 10, 20, 20, 10));
        when(clubMapper.selectMyPendingJoinRequests(16L)).thenReturn(List.of(request));

        List<MyJoinRequestResponse> requests = service.findMyPendingJoinRequests(16L);

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getJoinRequestId()).isEqualTo(5L);
        assertThat(requests.get(0).getClubId()).isEqualTo(1L);
        assertThat(requests.get(0).getClubName()).isEqualTo("강남 풋살 클럽");
        assertThat(requests.get(0).getDate()).isEqualTo("2026.10.20");
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
        when(accountLockService.lockById(8L)).thenReturn(account(8L));
        when(clubMapper.selectPendingJoinRequestById(1L, 5L)).thenReturn(pendingRequest(8L));
        when(clubMapper.countActiveMembers(1L)).thenReturn(3);

        service.approveJoin(7L, 1L, 5L);

        InOrder order = inOrder(clubMapper);
        order.verify(clubMapper).updateJoinRequestStatus(5L, "승인");
        order.verify(clubMapper).insertJoinedMember(1L, 8L);
        verify(clubMapper, never()).updateClubRecruitClosed(any());
    }

    @Test
    @DisplayName("승인으로 정원이 차면 회원 추가 뒤 모집 마감으로 저장한다")
    void 승인으로_정원이_차면_모집을_마감한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(accountLockService.lockById(8L)).thenReturn(account(8L));
        when(clubMapper.selectPendingJoinRequestById(1L, 5L)).thenReturn(pendingRequest(8L));
        // 정원 10명, 현재 9명 + 이번 승인 1명
        when(clubMapper.countActiveMembers(1L)).thenReturn(9);

        service.approveJoin(7L, 1L, 5L);

        InOrder order = inOrder(clubMapper);
        order.verify(clubMapper).insertJoinedMember(1L, 8L);
        order.verify(clubMapper).updateClubRecruitClosed(1L);
    }

    // 알림: 수신자는 동호회원 번호가 아닌 회원 번호로 확정한다
    @Test
    @DisplayName("가입 신청이 접수되면 동호회장 회원 번호로 알림을 등록한다")
    void 가입_신청_접수는_동호회장에게_알린다() {
        ClubSummary club = lockedClub("모집중", 10);
        club.setClubName("강남 농구 크루");
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(club);
        when(clubMapper.selectMyPendingJoinRequestId(1L, 7L)).thenReturn(null, 5L);
        when(clubMapper.countActiveMembers(1L)).thenReturn(3);
        when(clubMapper.selectActiveLeaderUserIds(1L)).thenReturn(List.of(9L, 10L));

        service.applyJoin(7L, 1L, "안녕하세요");

        verify(notificationDispatcher).notifyAfterCommit(eq(List.of(9L, 10L)), eq("CLUB_JOIN_REQUESTED"),
                any(), contains("강남 농구 크루"), isNull(), isNull(), eq(5L));
    }

    @Test
    @DisplayName("가입 승인·거절은 신청자 회원 번호로 알린다")
    void 가입_승인_거절은_신청자에게_알린다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(accountLockService.lockById(8L)).thenReturn(account(8L));
        when(clubMapper.selectPendingJoinRequestById(1L, 5L)).thenReturn(pendingRequest(8L));
        when(clubMapper.countActiveMembers(1L)).thenReturn(3);

        service.approveJoin(7L, 1L, 5L);
        service.rejectJoin(7L, 1L, 5L);

        verify(notificationDispatcher).notifyAfterCommit(eq(List.of(8L)), eq("CLUB_JOIN_APPROVED"),
                any(), any(), isNull(), isNull(), eq(5L));
        verify(notificationDispatcher).notifyAfterCommit(eq(List.of(8L)), eq("CLUB_JOIN_REJECTED"),
                any(), any(), isNull(), isNull(), eq(5L));
    }

    @Test
    @DisplayName("회장 위임은 동호회원 번호가 아니라 실제 새 회장 회원 번호로 알린다")
    void 회장_위임은_실제_회원에게_알린다() {
        ClubSummary club = lockedClub("모집중", 10);
        club.setClubName("강남 농구 크루");
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(club);
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.updateRoleToLeader(1L, 3L)).thenReturn(1);
        when(clubMapper.selectActiveMemberById(1L, 3L)).thenReturn(kickTarget(8L));
        when(accountLockService.lockById(8L)).thenReturn(account(8L));

        service.transferLeader(7L, 1L, 3L);

        verify(notificationDispatcher).notifyAfterCommit(eq(List.of(8L)), eq("CLUB_LEADER_TRANSFERRED"),
                any(), contains("강남 농구 크루"), isNull(), isNull(), isNull(), eq(1L));
    }

    @Test
    @DisplayName("추방 알림은 추방된 동호회원의 실제 회원 번호로 보낸다")
    void 추방은_대상_회원에게_알린다() {
        ClubSummary club = lockedClub("모집중", 10);
        club.setClubName("강남 농구 크루");
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(club);
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectActiveMemberById(1L, 3L)).thenReturn(kickTarget(8L));

        service.kickMember(7L, 1L, 3L, "불참");

        verify(notificationDispatcher).notifyAfterCommit(eq(List.of(8L)), eq("CLUB_MEMBER_KICKED"),
                any(), contains("강남 농구 크루"), isNull(), isNull(), isNull(), eq(1L));
    }

    @Test
    @DisplayName("동호회장이 아니면 가입 신청을 처리하지 않는다")
    void 동호회장이_아니면_승인을_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveJoin(7L, 1L, 5L));

        assertThat(exception.getMessage()).isEqualTo("동호회장만 가입 신청을 처리할 수 있습니다.");
        verify(clubMapper, never()).updateJoinRequestStatus(any(), any());
    }

    @Test
    @DisplayName("정원이 가득 차면 승인하지 않는다")
    void 정원이_가득_차면_승인을_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectPendingJoinRequestById(1L, 5L)).thenReturn(pendingRequest(8L));
        when(accountLockService.lockById(8L)).thenReturn(account(8L));
        when(clubMapper.countActiveMembers(1L)).thenReturn(10);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveJoin(7L, 1L, 5L));

        assertThat(exception.getMessage()).isEqualTo("정원이 가득 찬 동호회입니다.");
        verify(clubMapper, never()).updateJoinRequestStatus(any(), any());
        verify(clubMapper, never()).insertJoinedMember(any(), any());
    }

    @Test
    @DisplayName("신청자가 탈퇴했으면 승인하지 않는다")
    void 탈퇴한_회원의_신청은_승인하지_않는다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectPendingJoinRequestById(1L, 5L)).thenReturn(pendingRequest(8L));
        Member withdrawn = account(8L);
        withdrawn.setDeletedAt(java.time.LocalDateTime.now());
        when(accountLockService.lockById(8L)).thenReturn(withdrawn);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.approveJoin(7L, 1L, 5L));

        assertThat(exception.getMessage()).isEqualTo("탈퇴한 회원의 신청이라 승인할 수 없습니다. 거절해 주세요.");
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
        when(clubMapper.selectActiveMemberById(1L, 3L)).thenReturn(kickTarget(8L));
        when(accountLockService.lockById(8L)).thenReturn(account(8L));
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

        assertThat(exception.getMessage()).isEqualTo("동호회장만 위임할 수 있습니다.");
        verify(clubMapper, never()).updateRoleToLeader(any(), any());
        verify(clubMapper, never()).updateRoleToMember(any(), any());
    }

    @Test
    @DisplayName("이 동호회의 활성 동호회원이 아니면 위임하지 않는다")
    void 받을_동호회원이_없으면_위임을_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectActiveMemberById(1L, 99L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transferLeader(7L, 1L, 99L));

        assertThat(exception.getMessage()).isEqualTo("위임할 수 없는 동호회원입니다.");
        verify(clubMapper, never()).updateRoleToLeader(any(), any());
        verify(clubMapper, never()).updateRoleToMember(any(), any());
    }

    @Test
    @DisplayName("없거나 해체된 동호회면 위임하지 않는다")
    void 해체된_동호회는_위임을_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transferLeader(7L, 1L, 3L));

        assertThat(exception.getMessage()).isEqualTo("동호회 정보를 찾을 수 없습니다.");
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

        assertThat(exception.getMessage()).isEqualTo("동호회장은 탈퇴할 수 없습니다. 동호회장을 위임한 뒤 탈퇴해 주세요.");
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
    @DisplayName("동호회원을 추방하면 앞뒤 공백을 지운 사유로 기록한다")
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
    @DisplayName("동호회장이 아니면 동호회원을 추방하지 못한다")
    void 동호회장이_아니면_내보내기를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.kickMember(7L, 1L, 3L, "불참"));

        assertThat(exception.getMessage()).isEqualTo("동호회장만 동호회원을 추방할 수 있습니다.");
        verify(clubMapper, never()).updateMemberKick(any(), any(), any());
    }

    @Test
    @DisplayName("모집 중인 팀 매치에 신청된 동호회원은 추방하지 못한다")
    void 팀_매치_신청중인_동호회원은_내보내기를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectActiveMemberById(1L, 3L)).thenReturn(kickTarget(8L));
        when(clubMapper.existsRecruitingTeamMatch(1L, 8L)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.kickMember(7L, 1L, 3L, "불참"));

        assertThat(exception.getMessage()).isEqualTo("모집 중인 팀 매치에 신청된 동호회원은 추방할 수 없습니다.");
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

        assertThat(exception.getMessage()).isEqualTo("동호회장만 동호회를 해체할 수 있습니다.");
        verify(clubMapper, never()).updateClubDisband(any());
    }

    @Test
    @DisplayName("입력한 이름이 다르면 해체하지 않는다")
    void 이름이_다르면_해체를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(disbandTarget());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.disbandClub(7L, 1L, "노원농구회"));

        assertThat(exception.getMessage()).isEqualTo("입력한 동호회 이름이 일치하지 않습니다.");
        verify(clubMapper, never()).updateClubDisband(any());
    }

    @Test
    @DisplayName("동호회장 외 동호회원이 남아 있으면 해체하지 않는다")
    void 동호회원이_남아_있으면_해체를_거부한다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(disbandTarget());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.countActiveMembers(1L)).thenReturn(2);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.disbandClub(7L, 1L, "노원 농구회"));

        assertThat(exception.getMessage()).isEqualTo("동호회장 외 다른 동호회원이 남아 있어 해체할 수 없습니다. 동호회원을 먼저 정리해 주세요.");
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

    }

    // 동호회 정보 수정
    @Test
    @DisplayName("동호회 정보를 수정하면 동호회를 고치고 지역을 지운 뒤 다시 넣는다")
    void 동호회_정보_수정_성공() throws Exception {
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClubForEdit());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);

        service.updateClub(7L, 1L, editForm());

        InOrder order = inOrder(clubMapper);
        order.verify(clubMapper).selectActiveClubForUpdate(1L);
        order.verify(clubMapper).existsRegion(3L);
        order.verify(clubMapper).updateClub(any(Club.class));
        order.verify(clubMapper).deleteClubRegions(1L);
        order.verify(clubMapper).insertClubRegion(1L, 3L);
        verify(clubMapper, never()).updateClubRecruitClosed(any());
    }

    @Test
    @DisplayName("수정으로 정원이 현재 인원과 같아지면 모집중을 골라도 모집 마감으로 저장한다")
    void 수정으로_정원이_차면_모집을_마감한다() throws Exception {
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClubForEdit());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        // 폼 정원 10명 = 현재 10명, 폼 모집 상태는 모집중
        when(clubMapper.countActiveMembers(1L)).thenReturn(10);

        service.updateClub(7L, 1L, editForm());

        InOrder order = inOrder(clubMapper);
        order.verify(clubMapper).updateClub(any(Club.class));
        order.verify(clubMapper).updateClubRecruitClosed(1L);
    }

    @Test
    @DisplayName("동호회장이 아니면 수정하지 않는다")
    void 동호회장이_아니면_수정을_거부한다() {
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClubForEdit());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(false);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateClub(7L, 1L, editForm()));

        assertThat(e.getMessage()).isEqualTo("동호회장만 동호회 정보를 수정할 수 있습니다.");
        verify(clubMapper, never()).updateClub(any(Club.class));
    }

    @Test
    @DisplayName("모집 정원을 현재 동호회원 수보다 적게 바꾸면 수정하지 않는다")
    void 정원이_현재_인원보다_적으면_수정을_거부한다() {
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClubForEdit());
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        // 폼 정원 10명 < 현재 12명
        when(clubMapper.countActiveMembers(1L)).thenReturn(12);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateClub(7L, 1L, editForm()));

        assertThat(e.getMessage()).isEqualTo("모집 정원은 현재 동호회원 수보다 작을 수 없습니다.");
        verify(clubMapper, never()).updateClub(any(Club.class));
    }

    @Test
    @DisplayName("다른 활성 동호회와 이름이 같으면 수정하지 않는다")
    void 다른_동호회와_이름이_같으면_수정을_거부한다() {
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
        ClubCreateForm form = editForm();
        form.setRecruitStatus("recruiting");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.updateClub(7L, 1L, form));

        assertThat(e.getMessage()).isEqualTo("모집 상태 값이 올바르지 않습니다.");
        verify(clubMapper, never()).selectActiveClubForUpdate(any());
    }

    @Test
    @DisplayName("탈퇴 차단 사유는 가입한 동호회 목록을 그대로 담는다")
    void 탈퇴_차단_사유에_가입한_동호회를_담는다() {
        when(clubMapper.selectLeaderClubBlocks(7L)).thenReturn(List.of(
                clubBlock(1L, "강서 테니스회"), clubBlock(2L, "노원 농구회")));

        ClubWithdrawalBlocks blocks = service.findWithdrawalBlocks(7L);

        assertThat(blocks.getLeaderClubNames()).containsExactly("강서 테니스회", "노원 농구회");
        assertThat(blocks.isBlocked()).isTrue();
    }

    @Test
    @DisplayName("이름이 같은 서로 다른 동호회는 id별로 각각 한 번씩 유지한다")
    void 이름이_같은_다른_동호회를_각각_유지한다() {
        when(clubMapper.selectLeaderClubBlocks(7L)).thenReturn(List.of(
                clubBlock(1L, "풋살 클럽"), clubBlock(2L, "풋살 클럽")));

        ClubWithdrawalBlocks blocks = service.findWithdrawalBlocks(7L);

        assertThat(blocks.getLeaderClubNames()).containsExactly("풋살 클럽", "풋살 클럽");
    }

    @Test
    @DisplayName("차단 사유가 없으면 비어 있는 목록과 blocked=false를 돌려준다")
    void 탈퇴_차단_사유가_없으면_빈_목록이다() {
        when(clubMapper.selectLeaderClubBlocks(7L)).thenReturn(List.of());

        ClubWithdrawalBlocks blocks = service.findWithdrawalBlocks(7L);

        assertThat(blocks.getLeaderClubNames()).isEmpty();
        assertThat(blocks.isBlocked()).isFalse();
    }

    @Test
    @DisplayName("회원 번호가 없으면 탈퇴 차단 사유를 조회하지 않는다")
    void 회원_번호가_없으면_탈퇴_차단_사유를_조회하지_않는다() {
        ClubWithdrawalBlocks blocks = service.findWithdrawalBlocks(null);

        assertThat(blocks.isBlocked()).isFalse();
        verify(clubMapper, never()).selectLeaderClubBlocks(any());
    }

    private ClubBlockRow clubBlock(Long clubId, String clubName) {
        ClubBlockRow row = new ClubBlockRow();
        row.setClubId(clubId);
        row.setClubName(clubName);
        return row;
    }

    private Member account(Long userId) {
        Member member = new Member();
        member.setUserId(userId);
        return member;
    }

    @Test
    @DisplayName("동호회장 위임은 동호회를 잠근 뒤 받을 회원 계정을 잠그고, 잠근 값으로 탈퇴를 확인한다")
    void 동호회장_위임은_동호회_다음에_계정을_잠근다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectActiveMemberById(1L, 3L)).thenReturn(kickTarget(8L));
        when(accountLockService.lockById(8L)).thenReturn(account(8L));
        when(clubMapper.updateRoleToLeader(1L, 3L)).thenReturn(1);

        service.transferLeader(7L, 1L, 3L);

        InOrder order = inOrder(clubMapper, accountLockService);
        order.verify(clubMapper).selectActiveClubForUpdate(1L);
        order.verify(accountLockService).lockById(8L);
        order.verify(clubMapper).updateRoleToLeader(1L, 3L);
        order.verify(clubMapper).updateRoleToMember(1L, 7L);
    }

    @Test
    @DisplayName("받을 회원이 그 사이 탈퇴했으면 동호회장으로 바꾸지 않는다")
    void 탈퇴한_회원에게는_위임하지_않는다() {
        when(clubMapper.selectActiveClubForUpdate(1L)).thenReturn(lockedClub("모집중", 10));
        when(clubMapper.existsActiveLeader(1L, 7L)).thenReturn(true);
        when(clubMapper.selectActiveMemberById(1L, 3L)).thenReturn(kickTarget(8L));
        Member withdrawn = account(8L);
        withdrawn.setDeletedAt(java.time.LocalDateTime.now());
        when(accountLockService.lockById(8L)).thenReturn(withdrawn);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.transferLeader(7L, 1L, 3L));

        assertThat(exception.getMessage()).isEqualTo("위임할 수 없는 동호회원입니다.");
        verify(clubMapper, never()).updateRoleToLeader(any(), any());
        verify(clubMapper, never()).updateRoleToMember(any(), any());
    }

    @Test
    @DisplayName("동호회를 만들면 동호회장 등록 전에 생성자 계정을 잠근다")
    void 동호회_만들기는_회장_등록_전에_계정을_잠근다() throws Exception {
        when(clubMapper.existsSport(1L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.existsActiveClubName("강남 농구 크루")).thenReturn(false);
        when(accountLockService.lockById(7L)).thenReturn(account(7L));
        doAnswer(invocation -> {
            Club club = invocation.getArgument(0);
            ReflectionTestUtils.setField(club, "clubId", 42L);
            return null;
        }).when(clubMapper).insertClub(any(Club.class));

        service.createClub(7L, validForm());

        InOrder order = inOrder(clubMapper, accountLockService);
        order.verify(clubMapper).insertClub(any(Club.class));
        order.verify(accountLockService).lockById(7L);
        order.verify(clubMapper).insertClubLeader(42L, 7L);
    }

    @Test
    @DisplayName("생성자가 그 사이 탈퇴했으면 잠근 값으로 판단해 동호회장으로 등록하지 않는다")
    void 동호회_만들기는_탈퇴한_생성자를_회장으로_등록하지_않는다() throws Exception {
        when(clubMapper.existsSport(1L)).thenReturn(true);
        when(clubMapper.existsRegion(3L)).thenReturn(true);
        when(clubMapper.existsActiveClubName("강남 농구 크루")).thenReturn(false);
        Member withdrawn = account(7L);
        withdrawn.setDeletedAt(java.time.LocalDateTime.now());
        when(accountLockService.lockById(7L)).thenReturn(withdrawn);
        doAnswer(invocation -> {
            Club club = invocation.getArgument(0);
            ReflectionTestUtils.setField(club, "clubId", 42L);
            return null;
        }).when(clubMapper).insertClub(any(Club.class));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.createClub(7L, validForm()));

        assertThat(exception.getMessage()).isEqualTo("로그인이 필요합니다.");
        verify(clubMapper, never()).insertClubLeader(any(), any());
    }

    @Test
    @DisplayName("동호회 평균 MMR로 실력 등급을 정하고, 배치 마친 동호회원이 없으면 배치 중으로 표시")
    void 동호회_평균_MMR로_실력_등급을_정한다() {
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

    // 동호회 이력 창
    @Test
    @DisplayName("동호회장이 아니면 가입 신청자 이력을 볼 수 없다")
    void 동호회장이_아니면_신청자_이력_거부() {
        when(clubMapper.selectClubDetail(1L)).thenReturn(detailClub());
        when(clubMapper.existsActiveLeader(1L, 4L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.findApplicantHistory(1L, 10L, 4L, LocalDateTime.now()));
    }

    @Test
    @DisplayName("이 동호회의 대기중 신청이 아니면 빈 결과")
    void 대기중_신청이_아니면_빈_결과() {
        when(clubMapper.selectClubDetail(1L)).thenReturn(detailClub());
        when(clubMapper.existsActiveLeader(1L, 1L)).thenReturn(true);
        when(clubMapper.selectPendingJoinRequestById(1L, 10L)).thenReturn(null);

        assertThat(service.findApplicantHistory(1L, 10L, 1L, LocalDateTime.now())).isEmpty();
    }

    @Test
    @DisplayName("가입 신청자 이력은 동호회장이 아니라 신청자 기준으로 동호회 종목을 조회한다")
    void 신청자_기준으로_조회() {
        ClubJoinRequest request = new ClubJoinRequest();
        request.setUserId(7L);
        request.setNickname("서은지");
        when(clubMapper.selectClubDetail(1L)).thenReturn(detailClub());
        when(clubMapper.existsActiveLeader(1L, 1L)).thenReturn(true);
        when(clubMapper.selectPendingJoinRequestById(1L, 10L)).thenReturn(request);

        ParticipantHistoryView view = service.findApplicantHistory(1L, 10L, 1L, LocalDateTime.now()).orElseThrow();

        assertThat(view.nickname()).isEqualTo("서은지");
        assertThat(view.tierLabel()).isEqualTo("기록 없음");
        verify(sportRatingService).findByUserIdAndSportId(7L, 1L);
        verify(historyMapper).countReports(7L, "처리 완료");
    }

    @Test
    @DisplayName("이 동호회 활성 회원이 아니면 동호회원 이력은 빈 결과")
    void 활성_회원이_아니면_빈_결과() {
        when(clubMapper.selectClubDetail(1L)).thenReturn(detailClub());
        when(clubMapper.selectActiveMemberWithLeaderById(1L, 20L)).thenReturn(null);

        assertThat(service.findMemberHistory(1L, 20L, LocalDateTime.now())).isEmpty();
    }

    @Test
    @DisplayName("동호회원 이력은 이 동호회 경기 기준으로 센다")
    void 동호회원_이력은_동호회_경기_기준() {
        ClubMember member = member("박도윤", "동호회장");
        member.setUserId(4L);
        LocalDateTime lastMatch = LocalDateTime.of(2026, 10, 30, 19, 0);
        when(clubMapper.selectClubDetail(1L)).thenReturn(detailClub());
        when(clubMapper.selectActiveMemberWithLeaderById(1L, 20L)).thenReturn(member);
        when(clubMapper.countClubRatedMatches(1L, 4L, 1L)).thenReturn(3);
        when(clubMapper.selectLastClubMatchAt(any(), any(), any(), any(), any(), any())).thenReturn(lastMatch);

        ClubMemberHistoryView view = service.findMemberHistory(1L, 20L, LocalDateTime.now()).orElseThrow();

        assertThat(view.nickname()).isEqualTo("박도윤");
        assertThat(view.ratedMatchCount()).isEqualTo(3);
        assertThat(view.lastClubMatchAt()).isEqualTo(lastMatch);
        verify(clubMapper).selectClubRecentChanges(1L, 4L, 1L, 5);
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

    // 예정 일정(시작·종료 시각만 바꿔 씀)
    private ClubSchedule schedule(LocalDateTime startAt, LocalDateTime endAt) {
        ClubSchedule schedule = new ClubSchedule();
        schedule.setMatchId(19L);
        schedule.setTitle("토요일 저녁 3:3 풋살");
        schedule.setStartAt(startAt);
        schedule.setEndAt(endAt);
        schedule.setFacilityName("성동 풋살장");
        return schedule;
    }

}
