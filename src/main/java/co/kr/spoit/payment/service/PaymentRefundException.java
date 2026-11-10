package co.kr.spoit.payment.service;

import co.kr.spoit.match.service.MatchApplyException;

/**
 * 카카오페이 환불(결제 취소)에 실패했다. 신청 취소·반려 트랜잭션을 함께 되돌리고, 메시지는 화면에 그대로 보여 준다.
 * 신청 화면·마이페이지·운영 화면이 이미 {@link MatchApplyException}을 안내로 바꾸므로 그 하위 타입으로 둔다.
 */
public class PaymentRefundException extends MatchApplyException {

    public PaymentRefundException(String message) {
        super(message);
    }
}
