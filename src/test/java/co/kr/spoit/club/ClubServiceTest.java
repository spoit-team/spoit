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
import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubCreateForm;
import co.kr.spoit.club.dto.ClubDetailResponse;
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
        when(clubMapper.selectClubSummaryList(null, null, null, false, "recent")).thenReturn(List.of(club));

        ClubCardResponse card = service.findClubCards().get(0);

        assertThat(card.getInitials()).isEqualTo("강남");
        assertThat(card.isRecruiting()).isTrue();
        assertThat(card.getStatusLabel()).isEqualTo("팀원 모집 중");
        assertThat(card.getTime()).isEqualTo("화, 목 19:00 - 21:00");
        assertThat(card.getFooterNote()).isEqualTo("모집 자리: 7명");
    }

    @Test
    @DisplayName("마감된 동호회는 모집 마감 카드로 바꾼다")
    void 마감된_동호회를_카드로_바꾼다() {
        ClubSummary club = club("마감", "6,7", 10, 12);
        when(clubMapper.selectClubSummaryList(null, null, null, false, "recent")).thenReturn(List.of(club));

        ClubCardResponse card = service.findClubCards().get(0);

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
        when(clubMapper.selectClubSummaryList(null, null, null, false, "recent")).thenReturn(List.of(club));

        ClubCardResponse card = service.findClubCards().get(0);

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
                member("윤드롭", "일반"),
                member("이슛터", "동호회장")));

        ClubDetailResponse detail = service.findClubDetail(1L);

        assertThat(detail.getInitials()).isEqualTo("강남");
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

        assertThat(service.findClubDetail(999L)).isNull();
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
        order.verify(clubMapper).insertClubMember(42L, 7L);
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
        verify(clubMapper, never()).insertClubMember(any(), any());
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

}
