package co.kr.spoit.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.kr.spoit.club.domain.ClubSummary;
import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.mapper.ClubMapper;
import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.club.service.ClubServiceImpl;

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
}
