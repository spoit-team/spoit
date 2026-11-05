package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.ApplicationType;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.dto.MatchTitleRow;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.service.MemberAccountLockService;
import co.kr.spoit.notification.service.NotificationDispatcher;

/**
 * 탈퇴를 막는 개인 매치 신청 조회가 "진행 중" 상태만 넘기는지 검증한다.
 *
 * <p>"매칭 완료"는 경기 진행 중 상태이고, 경기 종료 후 스케줄러가 "종료"로 바꾼다.
 * 따라서 차단 상태는 모집 중·매칭 완료이며 종료·취소는 포함하지 않는다.</p>
 */
class MatchApplicationServiceWithdrawalBlockTest {

    private static final long USER_ID = 7L;

    private MatchApplicationMapper applicationMapper;
    private MatchApplicationService service;

    @BeforeEach
    void setUp() {
        applicationMapper = mock(MatchApplicationMapper.class);
        service = new MatchApplicationService(applicationMapper, mock(MemberMapper.class),
                mock(SportRatingMapper.class), mock(MatchService.class), mock(FacilityReservationService.class),
                mock(MemberAccountLockService.class), mock(ClubService.class),
                mock(NotificationDispatcher.class));
    }

    @Test
    @DisplayName("탈퇴 차단 조회는 매치 id로 중복을 접고 같은 제목의 다른 매치는 각각 남긴다")    void keepsDistinctMatchesWithSameTitle() {
        given(applicationMapper.findActiveIndividualMatchTitles(anyLong(), any(), any(), any()))
                .willReturn(List.of(matchTitle(11L, "풋살 매치"), matchTitle(12L, "풋살 매치")));

        List<String> titles = service.findActiveIndividualMatchTitles(USER_ID);

        assertThat(titles).containsExactly("풋살 매치", "풋살 매치");
    }

    @Test
    @DisplayName("차단 조회는 개인 신청의 대기·확정과 매칭방 모집 중·매칭 완료 상태로 제한한다")
    void queriesOnlyActiveIndividualApplicationsOfOngoingMatches() {
        given(applicationMapper.findActiveIndividualMatchTitles(anyLong(), any(), any(), any()))
                .willReturn(List.of(matchTitle(11L, "화요일 저녁 복식")));

        List<String> titles = service.findActiveIndividualMatchTitles(USER_ID);

        assertThat(titles).containsExactly("화요일 저녁 복식");

        ArgumentCaptor<List<String>> applicationStatuses = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> matchStatuses = ArgumentCaptor.forClass(List.class);
        verify(applicationMapper).findActiveIndividualMatchTitles(eq(USER_ID),
                eq(ApplicationType.INDIVIDUAL.dbValue()),
                applicationStatuses.capture(), matchStatuses.capture());

        assertThat(applicationStatuses.getValue())
                .containsExactly(ApplicationStatus.WAITING.dbValue(), ApplicationStatus.CONFIRMED.dbValue());
        // "종료"는 경기 종료 후 스케줄러가 기록하므로 차단 상태에 넣지 않는다.
        assertThat(matchStatuses.getValue())
                .containsExactly(MatchStatus.RECRUITING.dbValue(), MatchStatus.MATCHED.dbValue())
                .doesNotContain(MatchStatus.CLOSED.dbValue(), MatchStatus.CANCELLED.dbValue());
    }

    private static MatchTitleRow matchTitle(Long matchId, String title) {
        MatchTitleRow row = new MatchTitleRow();
        row.setMatchId(matchId);
        row.setTitle(title);
        return row;
    }
}
