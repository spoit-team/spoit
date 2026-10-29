package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.member.mapper.MemberMapper;

/** RES-05 신청 취소 규칙: 신청자 본인, 모집 중, 경기 시작 전, 활성 신청만 취소된다. */
class MatchApplicationServiceCancelTest {

    /** 서비스와 같은 기준 시각(한국 시간)으로 경기 시작 전·후를 만든다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long USER_ID = 7L;
    private static final long APPLICATION_ID = 11L;
    private static final long MATCH_ID = 3L;

    private MatchApplicationMapper mapper;
    private MatchApplicationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MatchApplicationMapper.class);
        service = new MatchApplicationService(mapper, mock(MemberMapper.class),
                mock(SportRatingMapper.class), mock(MatchService.class),
                mock(FacilityReservationService.class));
        given(mapper.findById(APPLICATION_ID)).willReturn(application(USER_ID));
    }

    @Test
    @DisplayName("본인 신청을 취소하면 신청과 연결된 참가자 행을 함께 취소한다")
    void cancelsApplicationAndParticipants() {
        given(mapper.lockMatch(MATCH_ID)).willReturn(match("모집 중", LocalDateTime.now(KST).plusDays(1)));
        given(mapper.markCancelled(eq(APPLICATION_ID), anyList(), eq("취소"), eq(USER_ID), any())).willReturn(1);

        service.cancel(APPLICATION_ID, USER_ID);

        verify(mapper).cancelParticipants(eq(APPLICATION_ID), anyList(), eq("취소"), any());
    }

    @Test
    @DisplayName("다른 회원의 신청은 취소할 수 없다")
    void rejectsOtherUsersApplication() {
        given(mapper.findById(APPLICATION_ID)).willReturn(application(99L));

        assertThatThrownBy(() -> service.cancel(APPLICATION_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("취소할 수 있는 신청이 아닙니다.");
        verify(mapper, never()).markCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("팀 배정이 끝난 매치는 취소할 수 없다")
    void rejectsAssignedMatch() {
        given(mapper.lockMatch(MATCH_ID)).willReturn(match("매칭 완료", LocalDateTime.now(KST).plusDays(1)));

        assertThatThrownBy(() -> service.cancel(APPLICATION_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessageContaining("팀 배정이 끝난 매치");
        verify(mapper, never()).markCancelled(anyLong(), anyList(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("이미 시작한 경기는 취소할 수 없다")
    void rejectsStartedMatch() {
        given(mapper.lockMatch(MATCH_ID)).willReturn(match("모집 중", LocalDateTime.now(KST).minusMinutes(1)));

        assertThatThrownBy(() -> service.cancel(APPLICATION_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 시작한 경기는 취소할 수 없습니다.");
    }

    @Test
    @DisplayName("이미 취소·반려된 신청이면 참가자 행을 건드리지 않는다")
    void rejectsInactiveApplication() {
        given(mapper.lockMatch(MATCH_ID)).willReturn(match("모집 중", LocalDateTime.now(KST).plusDays(1)));
        given(mapper.markCancelled(eq(APPLICATION_ID), anyList(), eq("취소"), eq(USER_ID), any())).willReturn(0);

        assertThatThrownBy(() -> service.cancel(APPLICATION_ID, USER_ID))
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 취소되었거나 처리된 신청입니다.");
        verify(mapper, never()).cancelParticipants(anyLong(), anyList(), anyString(), any());
    }

    private static MatchApplication application(long applicantUserId) {
        MatchApplication a = MatchApplication.individual(MATCH_ID, applicantUserId, "RCPT-TEST", LocalDateTime.now(KST));
        a.setApplicationId(APPLICATION_ID);
        return a;
    }

    private static MatchRow match(String status, LocalDateTime startAt) {
        MatchRow m = new MatchRow();
        m.setMatchId(MATCH_ID);
        m.setStatus(status);
        m.setStartAt(startAt);
        m.setEndAt(startAt.plusHours(2));
        m.setMaxParticipants(10);
        return m;
    }
}
