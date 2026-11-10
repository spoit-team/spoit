package co.kr.spoit.payment.domain;

/**
 * payment.status 값.
 *
 * <p>READY(카카오페이 결제 준비) → PAID(최종 승인하고 신청 행을 만듦) → PARTIAL_REFUNDED / REFUNDED(환불 누적).
 * 승인하지 않고 끝난 주문은 FAILED(사용자 취소·결제 실패·정원 마감 등), 승인 직후 신청을 만들지 못해
 * 바로 취소한 주문은 READY에서 REFUNDED가 된다.</p>
 */
public enum PaymentStatus {
    READY,
    PAID,
    PARTIAL_REFUNDED,
    REFUNDED,
    FAILED
}
