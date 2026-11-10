package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.mapper.MatchApplicationMapper;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.service.MemberAccountLockService;
import co.kr.spoit.notification.service.NotificationDispatcher;
import co.kr.spoit.payment.service.PaymentLedgerService;
import co.kr.spoit.payment.domain.Payment;

/** RES-05 신청 취소 규칙: 신청자 본인, 모집 중, 경기 시작 전, 활성 신청만 취소된다. */
class MatchApplicationServiceCancelTest {

    /** 서비스와 같은 기준 시각(한국 시간)으로 경기 시작 전·후를 만든다. */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final long USER_ID = 7L;
    private static final long APPLICATION_ID = 11L;
    private static final long MATCH_ID = 3L;

    private MatchApplicationMapper mapper;
    private NotificationDispatcher notificationDispatcher;
    private MatchApplicationService service;
    private final PaymentLedgerService paymentLedger = mock(PaymentLedgerService.class);

    @BeforeEach
    void setUp() {
        mapper = mock(MatchApplicationMapper.class);
        notificationDispatcher = mock(NotificationDispatcher.class);
        service = new MatchApplicationService(mapper, mock(MemberMapper.class),
                mock(SportRatingMapper.class), mock(MatchService.class),
                mock(FacilityReservationService.class), mock(MemberAccountLockService.class),
                mock(ClubService.class), notificationDispatcher, paymentLedger);
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
    @DisplayName("본인 취소는 담당 시설 관리자에게 알리고 본인에게는 보내지 않는다")
    void notifiesFacilityManagersOnly() {
        given(mapper.lockMatch(MATCH_ID)).willReturn(match("모집 중", LocalDateTime.now(KST).plusDays(1)));
        given(mapper.markCancelled(eq(APPLICATION_ID), anyList(), eq("취소"), eq(USER_ID), any())).willReturn(1);
        given(mapper.findFacilityManagerUserIds(MATCH_ID)).willReturn(List.of(30L, 31L));

        service.cancel(APPLICATION_ID, USER_ID);

        verify(notificationDispatcher).notifyAfterCommit(List.of(30L, 31L), "MATCH_APPLICATION_CANCELLED",
                "매칭 신청이 취소되었습니다", "매칭방 신청이 취소되었습니다. 신청 관리에서 확인해 주세요.",
                MATCH_ID, APPLICATION_ID, null);
        verify(notificationDispatcher, never()).notifyAfterCommit(argThat(ids -> ids.contains(USER_ID)),
                anyString(), anyString(), anyString(), any(), any(), any());
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

    /* ---------------- 참가비 환불 ---------------- */

    @Test
    @DisplayName("본인 취소는 신청 행을 환불 요청한다 (무료 신청이면 원장이 건너뛴다)")
    void cancelRequestsRefund() {
        given(mapper.lockMatch(MATCH_ID)).willReturn(match("모집 중", LocalDateTime.now(KST).plusDays(1)));
        given(mapper.markCancelled(eq(APPLICATION_ID), anyList(), eq("취소"), eq(USER_ID), any())).willReturn(1);

        service.cancel(APPLICATION_ID, USER_ID);

        verify(paymentLedger).refundApplications(eq(List.of(APPLICATION_ID)), eq("신청 취소"), any());
    }

    @Test
    @DisplayName("취소할 수 없는 신청은 환불도 요청하지 않는다")
    void rejectedCancelDoesNotRefund() {
        given(mapper.lockMatch(MATCH_ID)).willReturn(match("매칭 완료", LocalDateTime.now(KST).plusDays(1)));

        assertThatThrownBy(() -> service.cancel(APPLICATION_ID, USER_ID)).isInstanceOf(MatchApplyException.class);

        verify(paymentLedger, never()).refundApplications(anyList(), anyString(), any());
    }

    @Test
    @DisplayName("관리자 반려는 그 신청 행을 환불 요청하고, 결제한 신청이면 신청자와 결제자에게 환불 안내를 보낸다")
    void rejectRefundsAndNotifies() {
        MatchApplication teamMember = application(21L);
        teamMember.setPaymentId("pay-1");
        given(mapper.findById(APPLICATION_ID)).willReturn(teamMember);
        given(mapper.countManagedApplication(APPLICATION_ID, 50L)).willReturn(1);
        given(mapper.lockMatch(MATCH_ID)).willReturn(match("모집 중", LocalDateTime.now(KST).plusDays(1)));
        given(mapper.markRejected(APPLICATION_ID, "대기", "반려")).willReturn(1);
        Payment payment = new Payment();
        payment.setPayerUserId(USER_ID);
        given(paymentLedger.findPayment("pay-1")).willReturn(payment);

        service.reject(APPLICATION_ID, 50L);

        verify(paymentLedger).refundApplications(eq(List.of(APPLICATION_ID)), eq("신청 반려"), any());
        verify(notificationDispatcher).notifyAfterCommit(eq(List.of(21L, USER_ID)), eq("MATCH_APPLICATION_REJECTED"),
                anyString(), argThat(body -> body.contains("환불")), eq(MATCH_ID), eq(APPLICATION_ID), any());
    }

    @Test
    @DisplayName("경기 시작 뒤 남은 대기 신청은 모두 반려하고 환불 요청한다")
    void rejectsUnconfirmedAfterStart() {
        LocalDateTime now = LocalDateTime.now(KST);
        given(mapper.lockMatch(MATCH_ID)).willReturn(match("매칭 완료", now.minusMinutes(1)));
        MatchApplication first = application(USER_ID);
        MatchApplication second = application(8L);
        second.setApplicationId(12L);
        given(mapper.findWaitingApplications(MATCH_ID, "대기")).willReturn(List.of(first, second));
        given(mapper.markRejected(anyLong(), eq("대기"), eq("반려"))).willReturn(1);

        int rejected = service.rejectUnconfirmed(MATCH_ID, now);

        assertThat(rejected).isEqualTo(2);
        verify(paymentLedger).refundApplications(eq(List.of(APPLICATION_ID, 12L)), eq("경기 시작 전 미확정"), eq(now));
        verify(notificationDispatcher, times(2)).notifyAfterCommit(anyList(), eq("MATCH_APPLICATION_REJECTED"),
                anyString(), anyString(), eq(MATCH_ID), any(), any());
    }

    @Test
    @DisplayName("시작 전이거나 취소된 매칭방의 대기 신청은 건드리지 않는다")
    void keepsWaitingBeforeStartOrCancelled() {
        LocalDateTime now = LocalDateTime.now(KST);
        given(mapper.lockMatch(MATCH_ID)).willReturn(match("모집 중", now.plusMinutes(5)));
        assertThat(service.rejectUnconfirmed(MATCH_ID, now)).isZero();

        given(mapper.lockMatch(MATCH_ID)).willReturn(match("취소", now.minusMinutes(5)));
        assertThat(service.rejectUnconfirmed(MATCH_ID, now)).isZero();

        verify(mapper, never()).markRejected(anyLong(), anyString(), anyString());
        verify(paymentLedger, never()).refundApplications(anyList(), anyString(), any());
    }
}
