package co.kr.spoit.payment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.payment.domain.Payment;
import co.kr.spoit.payment.domain.PaymentStatus;
import co.kr.spoit.payment.domain.RefundableApplicationRow;
import co.kr.spoit.payment.kakao.KakaoPayClient;
import co.kr.spoit.payment.kakao.KakaoPayClient.ApproveResult;
import co.kr.spoit.payment.kakao.KakaoPayClient.OrderResult;
import co.kr.spoit.payment.kakao.KakaoPayException;
import co.kr.spoit.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 결제 승인과 환불. 신청 접수·취소·반려 트랜잭션 안에서 불린다.
 *
 * <p>환불은 그 자리에서 카카오페이 취소를 호출한다. 실패하면 {@link PaymentRefundException}으로 신청 취소도 되돌린다.
 * 같은 트랜잭션의 DB 변경을 먼저 하고 카카오페이 취소를 마지막에 부르며, 호출 전에 카카오페이 주문을 조회해
 * 앞선 시도(되돌려진 트랜잭션)에서 이미 취소된 금액이면 다시 취소하지 않는다.</p>
 *
 * <p>잠금 순서: 매칭방 → 결제(결제 ID 오름차순). 신청 상태를 바꾸는 경로는 모두 매칭방을 먼저 잠그므로
 * 같은 신청 행을 두 번 환불하지 않는다(refunded_at이 비어 있는 행만 환불한다).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentLedgerService {

    static final String REFUND_FAILED = "환불 요청에 실패했습니다. 잠시 후 다시 시도해 주세요.";

    /** 결제를 승인하지 못하고 끝난 주문을 정리한 결과 */
    public enum Unapplied {
        /** 승인되지 않아 결제가 없다 */
        NOT_PAID,
        /** 승인됐던 결제를 전액 취소했다 */
        REFUNDED,
        /** 카카오페이 상태를 확인하지 못했다 */
        UNKNOWN
    }

    private final PaymentMapper paymentMapper;
    private final KakaoPayClient kakaoPayClient;

    public Payment findPayment(String paymentId) {
        return paymentId == null ? null : paymentMapper.findById(paymentId);
    }

    /**
     * 카카오페이 최종 승인. 신청 접수 트랜잭션에서 정원·중복 확인을 마친 뒤, 신청 행을 만들기 직전에 부른다.
     *
     * @return 승인 시각
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LocalDateTime approve(String paymentId, String pgToken, LocalDateTime now) {
        Payment payment = paymentMapper.lockById(paymentId);
        if (payment == null || !payment.hasStatus(PaymentStatus.READY) || payment.getTid() == null) {
            throw new MatchApplyException("이미 처리된 결제입니다.");
        }
        ApproveResult approved;
        try {
            approved = kakaoPayClient.approve(payment.getTid(), paymentId, String.valueOf(payment.getPayerUserId()),
                    pgToken);
        } catch (KakaoPayException e) {
            log.warn("카카오페이 승인 실패: paymentId={}, code={}, message={}", paymentId, e.getCode(), e.getMessage());
            throw new MatchApplyException("카카오페이 결제 승인에 실패했습니다.");
        }
        if (approved.totalAmount() != payment.getTotalAmount().longValue()) {
            // 승인 금액이 주문과 다르면 신청을 만들지 않는다. 취소는 호출 측의 정리(compensateUnapplied)가 한다.
            throw new MatchApplyException("결제 금액이 주문 금액과 달라 신청하지 않았습니다.");
        }
        return approved.approvedAt() == null ? now : approved.approvedAt();
    }

    /** 승인 뒤 만든 신청 행을 결제에 연결하고 READY → PAID로 바꾼다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void markPaid(String paymentId, List<Long> applicationIds, LocalDateTime paidAt, LocalDateTime now) {
        if (paymentMapper.markPaid(paymentId, paidAt, now) != 1
                || paymentMapper.linkApplications(paymentId, applicationIds) != applicationIds.size()) {
            throw new IllegalStateException("결제와 신청 행을 연결하지 못했습니다. paymentId=" + paymentId);
        }
    }

    /** 고른 신청 행 중 결제가 있고 아직 환불하지 않은 행을 1인 참가비씩 환불한다. 무료 신청은 건너뛴다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundApplications(Collection<Long> applicationIds, String reason, LocalDateTime now) {
        if (applicationIds == null || applicationIds.isEmpty()) {
            return;
        }
        refund(paymentMapper.findRefundableApplications(applicationIds), reason, now);
    }

    /** 매칭방의 활성(대기·확정) 결제 신청을 모두 환불한다. 매칭방 취소·경기 자동 취소에서 쓴다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundActiveApplicationsOfMatch(long matchId, String reason, LocalDateTime now) {
        refund(paymentMapper.findRefundableApplicationsOfMatch(matchId, ApplicationStatus.activeValues()), reason, now);
    }

    /**
     * 신청을 만들지 못한 주문 정리. 카카오페이에서 승인된 결제면 남은 금액을 전액 취소하고, 아니면 실패로 닫는다.
     * 신청 접수 트랜잭션이 되돌려진 뒤 새 트랜잭션에서 부른다.
     */
    @Transactional
    public Unapplied compensateUnapplied(String paymentId, String reason, LocalDateTime now) {
        Payment payment = paymentMapper.lockById(paymentId);
        if (payment == null || !payment.hasStatus(PaymentStatus.READY)) {
            return Unapplied.NOT_PAID;
        }
        String failReason = truncate(reason, 300);
        if (payment.getTid() == null) {
            paymentMapper.markFailed(paymentId, failReason, now);
            return Unapplied.NOT_PAID;
        }
        try {
            OrderResult order = kakaoPayClient.order(payment.getTid());
            if (!order.isApproved()) {
                paymentMapper.markFailed(paymentId, failReason, now);
                return Unapplied.NOT_PAID;
            }
            long remaining = order.totalAmount() - order.canceledAmount();
            if (remaining > 0) {
                kakaoPayClient.cancel(payment.getTid(), remaining, remaining);
            }
            paymentMapper.markRefundedUnapplied(paymentId, failReason, now);
            return Unapplied.REFUNDED;
        } catch (KakaoPayException e) {
            log.error("승인 후 신청을 만들지 못한 결제의 정리 실패 (수동 확인 필요): paymentId={}, code={}, message={}",
                    paymentId, e.getCode(), e.getMessage());
            return Unapplied.UNKNOWN;
        }
    }

    private void refund(List<RefundableApplicationRow> rows, String reason, LocalDateTime now) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // 결제 ID 순으로 잠가 여러 결제를 함께 환불할 때 잠금 순서를 고정한다.
        Map<String, List<Long>> byPayment = new TreeMap<>();
        for (RefundableApplicationRow row : rows) {
            byPayment.computeIfAbsent(row.getPaymentId(), k -> new ArrayList<>()).add(row.getApplicationId());
        }
        for (Map.Entry<String, List<Long>> entry : byPayment.entrySet()) {
            String paymentId = entry.getKey();
            List<Long> ids = entry.getValue();
            Payment payment = paymentMapper.lockById(paymentId);
            if (payment == null) {
                throw new IllegalStateException("결제 정보를 찾을 수 없습니다. paymentId=" + paymentId);
            }
            long before = payment.getRefundedAmount() == null ? 0 : payment.getRefundedAmount().longValue();
            BigDecimal amount = payment.getUnitAmount().multiply(BigDecimal.valueOf(ids.size()));
            if (paymentMapper.addRefunded(paymentId, amount, now) != 1
                    || paymentMapper.markApplicationsRefunded(ids, now) != ids.size()) {
                throw new IllegalStateException("환불 금액을 기록하지 못했습니다. paymentId=" + paymentId);
            }
            cancelAtKakaoPay(payment, before, amount.longValue());
            log.info("참가비 환불: paymentId={}, 인원={}, 금액={}, 사유={}", paymentId, ids.size(), amount, reason);
        }
    }

    /**
     * 카카오페이 취소. 되돌려진 앞선 시도가 이미 취소했으면(카카오페이 누적 취소 = 기록 + 이번 금액) 다시 취소하지 않는다.
     */
    private void cancelAtKakaoPay(Payment payment, long refundedBefore, long amount) {
        try {
            OrderResult order = kakaoPayClient.order(payment.getTid());
            if (order.canceledAmount() == refundedBefore + amount) {
                log.info("이미 카카오페이에서 취소된 환불을 기록: paymentId={}, amount={}", payment.getPaymentId(), amount);
                return;
            }
            if (order.canceledAmount() != refundedBefore) {
                log.error("카카오페이 누적 취소 금액({})이 기록({})과 다름: paymentId={}", order.canceledAmount(),
                        refundedBefore, payment.getPaymentId());
                throw new PaymentRefundException("환불 금액이 결제 기록과 달라 처리를 멈췄습니다. 시설에 문의해 주세요.");
            }
            kakaoPayClient.cancel(payment.getTid(), amount, payment.getTotalAmount().longValue() - refundedBefore);
        } catch (KakaoPayException e) {
            log.warn("카카오페이 취소 실패: paymentId={}, code={}, message={}", payment.getPaymentId(), e.getCode(),
                    e.getMessage());
            throw new PaymentRefundException(REFUND_FAILED);
        }
    }

    static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
