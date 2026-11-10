package co.kr.spoit.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.payment.domain.Payment;
import co.kr.spoit.payment.domain.RefundableApplicationRow;
import co.kr.spoit.payment.kakao.KakaoPayClient;
import co.kr.spoit.payment.kakao.KakaoPayClient.ApproveResult;
import co.kr.spoit.payment.kakao.KakaoPayClient.OrderResult;
import co.kr.spoit.payment.kakao.KakaoPayException;
import co.kr.spoit.payment.mapper.PaymentMapper;
import co.kr.spoit.payment.service.PaymentLedgerService.Unapplied;

/** 카카오페이 승인과 즉시 환불: 인원만큼 부분 취소, 실패하면 신청 취소도 되돌리고, 이미 취소된 금액은 다시 취소하지 않는다. */
class PaymentLedgerServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);

    private PaymentMapper mapper;
    private KakaoPayClient kakao;
    private PaymentLedgerService ledger;

    @BeforeEach
    void setUp() {
        mapper = mock(PaymentMapper.class);
        kakao = mock(KakaoPayClient.class);
        ledger = new PaymentLedgerService(mapper, kakao);
    }

    /* ---------------- 승인 ---------------- */

    @Test
    @DisplayName("승인은 저장한 tid와 pg_token으로 카카오페이 최종 승인을 호출하고 승인 시각을 돌려준다")
    void approveCallsKakaoPay() {
        given(mapper.lockById("pay-1")).willReturn(payment("pay-1", "READY", 9000, 2, 0));
        LocalDateTime approvedAt = LocalDateTime.of(2026, 11, 10, 11, 59);
        given(kakao.approve("T-pay-1", "pay-1", "7", "pg")).willReturn(new ApproveResult(18000, approvedAt));

        assertThat(ledger.approve("pay-1", "pg", NOW)).isEqualTo(approvedAt);
    }

    @Test
    @DisplayName("이미 처리된 주문은 승인하지 않는다")
    void approveRejectsProcessedOrder() {
        given(mapper.lockById("pay-1")).willReturn(payment("pay-1", "PAID", 9000, 1, 0));

        assertThatThrownBy(() -> ledger.approve("pay-1", "pg", NOW)).isInstanceOf(MatchApplyException.class)
                .hasMessage("이미 처리된 결제입니다.");
        verify(kakao, never()).approve(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("승인 실패·금액 불일치면 신청을 만들지 않도록 예외를 던진다")
    void approveFailures() {
        given(mapper.lockById("pay-1")).willReturn(payment("pay-1", "READY", 9000, 1, 0));
        given(kakao.approve(anyString(), anyString(), anyString(), anyString()))
                .willThrow(new KakaoPayException("-702", "pg_token invalid"));
        assertThatThrownBy(() -> ledger.approve("pay-1", "pg", NOW)).isInstanceOf(MatchApplyException.class)
                .hasMessage("카카오페이 결제 승인에 실패했습니다.");

        org.mockito.BDDMockito.willReturn(new ApproveResult(100, NOW))
                .given(kakao).approve(anyString(), anyString(), anyString(), anyString());
        assertThatThrownBy(() -> ledger.approve("pay-1", "pg", NOW)).isInstanceOf(MatchApplyException.class)
                .hasMessageContaining("금액");
    }

    /* ---------------- 환불 ---------------- */

    @Test
    @DisplayName("팀원 2명 취소는 대표 결제에서 9,000원 × 2 = 18,000원을 부분 취소한다")
    void refundsPartialTeamCancellation() {
        given(mapper.findRefundableApplications(List.of(21L, 22L)))
                .willReturn(List.of(row(21L, "pay-1"), row(22L, "pay-1")));
        given(mapper.lockById("pay-1")).willReturn(payment("pay-1", "PAID", 9000, 5, 0));
        given(mapper.addRefunded(eq("pay-1"), any(), eq(NOW))).willReturn(1);
        given(mapper.markApplicationsRefunded(List.of(21L, 22L), NOW)).willReturn(2);
        given(kakao.order("T-pay-1")).willReturn(new OrderResult("SUCCESS_PAYMENT", 45000, 0));

        ledger.refundApplications(List.of(21L, 22L), "팀원 일부 취소", NOW);

        ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
        verify(mapper).addRefunded(eq("pay-1"), amount.capture(), eq(NOW));
        assertThat(amount.getValue()).isEqualByComparingTo("18000");
        verify(kakao).cancel("T-pay-1", 18000, 45000);
    }

    @Test
    @DisplayName("앞서 일부 환불한 결제는 남은 취소 가능 금액을 확인값으로 넘긴다")
    void passesRemainingCancellableAmount() {
        given(mapper.findRefundableApplications(List.of(23L))).willReturn(List.of(row(23L, "pay-1")));
        given(mapper.lockById("pay-1")).willReturn(payment("pay-1", "PARTIAL_REFUNDED", 9000, 5, 18000));
        given(mapper.addRefunded(anyString(), any(), any())).willReturn(1);
        given(mapper.markApplicationsRefunded(anyList(), any())).willReturn(1);
        given(kakao.order("T-pay-1")).willReturn(new OrderResult("PART_CANCEL_PAYMENT", 45000, 18000));

        ledger.refundApplications(List.of(23L), "신청 반려", NOW);

        verify(kakao).cancel("T-pay-1", 9000, 27000);
    }

    @Test
    @DisplayName("되돌려진 앞선 시도가 이미 카카오페이에서 취소했으면 다시 취소하지 않는다")
    void skipsAlreadyCancelledAmount() {
        given(mapper.findRefundableApplications(List.of(21L))).willReturn(List.of(row(21L, "pay-1")));
        given(mapper.lockById("pay-1")).willReturn(payment("pay-1", "PAID", 9000, 1, 0));
        given(mapper.addRefunded(anyString(), any(), any())).willReturn(1);
        given(mapper.markApplicationsRefunded(anyList(), any())).willReturn(1);
        given(kakao.order("T-pay-1")).willReturn(new OrderResult("CANCEL_PAYMENT", 9000, 9000));

        ledger.refundApplications(List.of(21L), "신청 취소", NOW);

        verify(kakao, never()).cancel(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("카카오페이 취소가 실패하면 환불 실패 안내로 신청 취소를 되돌린다")
    void refundFailureRollsBack() {
        given(mapper.findRefundableApplications(List.of(21L))).willReturn(List.of(row(21L, "pay-1")));
        given(mapper.lockById("pay-1")).willReturn(payment("pay-1", "PAID", 9000, 1, 0));
        given(mapper.addRefunded(anyString(), any(), any())).willReturn(1);
        given(mapper.markApplicationsRefunded(anyList(), any())).willReturn(1);
        given(kakao.order("T-pay-1")).willReturn(new OrderResult("SUCCESS_PAYMENT", 9000, 0));
        willThrow(new KakaoPayException("NETWORK", "down")).given(kakao).cancel(anyString(), anyLong(), anyLong());

        assertThatThrownBy(() -> ledger.refundApplications(List.of(21L), "신청 취소", NOW))
                .isInstanceOf(PaymentRefundException.class)
                .isInstanceOf(MatchApplyException.class)
                .hasMessage("환불 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    @DisplayName("카카오페이 누적 취소 금액이 기록과 다르면 자동 환불을 멈춘다")
    void stopsOnMismatch() {
        given(mapper.findRefundableApplications(List.of(21L))).willReturn(List.of(row(21L, "pay-1")));
        given(mapper.lockById("pay-1")).willReturn(payment("pay-1", "PAID", 9000, 2, 0));
        given(mapper.addRefunded(anyString(), any(), any())).willReturn(1);
        given(mapper.markApplicationsRefunded(anyList(), any())).willReturn(1);
        given(kakao.order("T-pay-1")).willReturn(new OrderResult("PART_CANCEL_PAYMENT", 18000, 5000));

        assertThatThrownBy(() -> ledger.refundApplications(List.of(21L), "신청 취소", NOW))
                .isInstanceOf(PaymentRefundException.class);
        verify(kakao, never()).cancel(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("결제가 여러 건이면 결제 ID 순으로 잠그고 결제마다 한 번씩 취소한다")
    void locksPaymentsInIdOrder() {
        given(mapper.findRefundableApplicationsOfMatch(eq(3L), anyList()))
                .willReturn(List.of(row(31L, "pay-b"), row(32L, "pay-a")));
        given(mapper.lockById("pay-a")).willReturn(payment("pay-a", "PAID", 5000, 1, 0));
        given(mapper.lockById("pay-b")).willReturn(payment("pay-b", "PAID", 5000, 1, 0));
        given(mapper.addRefunded(anyString(), any(), any())).willReturn(1);
        given(mapper.markApplicationsRefunded(anyList(), any())).willReturn(1);
        given(kakao.order(anyString())).willReturn(new OrderResult("SUCCESS_PAYMENT", 5000, 0));

        ledger.refundActiveApplicationsOfMatch(3L, "매칭방 취소", NOW);

        InOrder order = inOrder(mapper, kakao);
        order.verify(mapper).lockById("pay-a");
        order.verify(kakao).cancel("T-pay-a", 5000, 5000);
        order.verify(mapper).lockById("pay-b");
        order.verify(kakao).cancel("T-pay-b", 5000, 5000);
    }

    @Test
    @DisplayName("결제 없는 무료 신청만 있으면 카카오페이를 부르지 않는다")
    void skipsFreeApplications() {
        given(mapper.findRefundableApplications(List.of(1L))).willReturn(List.of());

        ledger.refundApplications(List.of(1L), "신청 취소", NOW);

        verify(mapper, never()).lockById(anyString());
        verify(kakao, never()).order(anyString());
    }

    /* ---------------- 신청을 만들지 못한 주문 정리 ---------------- */

    @Test
    @DisplayName("승인 전에 끝났으면 실패로 닫고, 승인된 결제면 남은 금액을 전액 취소한다")
    void compensatesUnapplied() {
        given(mapper.lockById("pay-1")).willReturn(payment("pay-1", "READY", 9000, 1, 0));
        given(kakao.order("T-pay-1")).willReturn(new OrderResult("QUIT_PAYMENT", 9000, 0));
        assertThat(ledger.compensateUnapplied("pay-1", "정원이 모두 찼습니다.", NOW)).isEqualTo(Unapplied.NOT_PAID);
        verify(mapper).markFailed("pay-1", "정원이 모두 찼습니다.", NOW);

        given(kakao.order("T-pay-1")).willReturn(new OrderResult("SUCCESS_PAYMENT", 9000, 0));
        assertThat(ledger.compensateUnapplied("pay-1", "DB 오류", NOW)).isEqualTo(Unapplied.REFUNDED);
        verify(kakao).cancel("T-pay-1", 9000, 9000);
        verify(mapper).markRefundedUnapplied("pay-1", "DB 오류", NOW);
    }

    @Test
    @DisplayName("카카오페이 상태를 확인하지 못하면 주문을 그대로 두고 UNKNOWN을 돌려준다")
    void compensateUnknown() {
        given(mapper.lockById("pay-1")).willReturn(payment("pay-1", "READY", 9000, 1, 0));
        given(kakao.order("T-pay-1")).willThrow(new KakaoPayException("NETWORK", "down"));

        assertThat(ledger.compensateUnapplied("pay-1", "사유", NOW)).isEqualTo(Unapplied.UNKNOWN);
        verify(mapper, never()).markFailed(anyString(), anyString(), any());
        verify(mapper, never()).markRefundedUnapplied(anyString(), anyString(), any());
    }

    private static RefundableApplicationRow row(long applicationId, String paymentId) {
        RefundableApplicationRow row = new RefundableApplicationRow();
        row.setApplicationId(applicationId);
        row.setPaymentId(paymentId);
        return row;
    }

    private static Payment payment(String id, String status, long unit, int seats, long refunded) {
        Payment p = new Payment();
        p.setPaymentId(id);
        p.setPayerUserId(7L);
        p.setTid("T-" + id);
        p.setStatus(status);
        p.setUnitAmount(BigDecimal.valueOf(unit));
        p.setSeatCount(seats);
        p.setTotalAmount(BigDecimal.valueOf(unit * seats));
        p.setRefundedAmount(BigDecimal.valueOf(refunded));
        return p;
    }
}
