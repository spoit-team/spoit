package co.kr.spoit.payment.kakao;

import java.time.LocalDateTime;

/**
 * 카카오페이 단건 결제 API 중 이 서비스가 쓰는 부분 (결제 준비 → 승인, 주문 조회, 취소).
 * 실패하면 {@link KakaoPayException}을 던진다. 테스트는 가짜 구현으로 바꾼다.
 */
public interface KakaoPayClient {

    /**
     * 결제 준비. 사용자는 돌려받은 주소에서 결제하고, 카카오페이가 approvalUrl로 pg_token을 붙여 돌려보낸다.
     *
     * @param partnerOrderId 가맹점 주문 번호 (payment.payment_id)
     * @param partnerUserId  가맹점 회원 ID
     */
    ReadyResult ready(String partnerOrderId, String partnerUserId, String itemName, int quantity, long totalAmount,
                      String approvalUrl, String cancelUrl, String failUrl);

    /** 최종 승인. 이 호출이 성공해야 실제로 결제된다. */
    ApproveResult approve(String tid, String partnerOrderId, String partnerUserId, String pgToken);

    /** 주문 조회. 승인 여부와 누적 취소 금액을 확인한다. */
    OrderResult order(String tid);

    /**
     * 결제 취소 (부분 취소 포함).
     *
     * @param cancelAvailableAmount 이 요청 전 취소 가능 금액. 카카오페이 쪽 잔액과 다르면 취소하지 않는다.
     */
    void cancel(String tid, long cancelAmount, long cancelAvailableAmount);

    record ReadyResult(String tid, String pcRedirectUrl, String mobileRedirectUrl) {
    }

    record ApproveResult(long totalAmount, LocalDateTime approvedAt) {
    }

    /**
     * @param status         SUCCESS_PAYMENT, PART_CANCEL_PAYMENT, CANCEL_PAYMENT, READY, QUIT_PAYMENT, FAIL_PAYMENT 등
     * @param canceledAmount 누적 취소 금액
     */
    record OrderResult(String status, long totalAmount, long canceledAmount) {

        /** 최종 승인된 결제(일부 취소 포함)인지 */
        public boolean isApproved() {
            return "SUCCESS_PAYMENT".equals(status) || "PART_CANCEL_PAYMENT".equals(status)
                    || "CANCEL_PAYMENT".equals(status);
        }
    }
}
