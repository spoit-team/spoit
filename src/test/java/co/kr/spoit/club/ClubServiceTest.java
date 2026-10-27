package co.kr.spoit.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubDetailResponse;
import co.kr.spoit.club.mapper.ClubMapper;
import co.kr.spoit.club.service.ClubServiceImpl;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClubServiceTest {

    @Mock
    private ClubMapper clubMapper;

    @InjectMocks
    private ClubServiceImpl service;

    @Test
    void convertsRecruitingClubToCard() {
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
    void convertsClosedClubToCard() {
        ClubSummary club = club("마감", "6,7", 10, 12);
        when(clubMapper.selectClubSummaryList(null, null, null, false, "recent")).thenReturn(List.of(club));

        ClubCardResponse card = service.findClubCards().get(0);

        assertThat(card.isRecruiting()).isFalse();
        assertThat(card.getStatusLabel()).isEqualTo("모집 마감");
        assertThat(card.getTime()).isEqualTo("토, 일 10:00 - 12:00");
        assertThat(card.getFooterNote()).isEqualTo("모집이 마감된 팀입니다.");
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

    @Test
    void showsDefaultAreaWhenClubHasNoRegion() {
    ClubSummary club = club("모집중", "2,4", 19, 21);
    club.setAreaLabel(null);
    when(clubMapper.selectClubSummaryList(null, null, null, false, "recent")).thenReturn(List.of(club));

    ClubCardResponse card = service.findClubCards().get(0);

    assertThat(card.getAreaLabel()).isEqualTo("지역 미정");
}

    @Test
    void convertsClubToDetail() {
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
    void showsDefaultLeaderWhenNoLeader() {
        when(clubMapper.selectClubDetail(1L)).thenReturn(detailClub());
        when(clubMapper.selectClubMembers(1L)).thenReturn(List.of(member("윤드롭", "일반")));

        ClubDetailResponse detail = service.findClubDetail(1L);

        assertThat(detail.getLeaderName()).isEqualTo("미정");
        assertThat(detail.getLeaderInitials()).isEqualTo("미정");
    }

    @Test
    void returnsNullWhenClubNotFound() {
        when(clubMapper.selectClubDetail(999L)).thenReturn(null);

        assertThat(service.findClubDetail(999L)).isNull();
        verify(clubMapper, never()).selectClubMembers(999L);
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
}
