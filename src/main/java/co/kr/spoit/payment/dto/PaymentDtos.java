package co.kr.spoit.payment.dto;

import java.util.List;

/** 결제 화면·API에서 주고받는 값 */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * 결제 준비 요청 (신청 화면 → 서버).
     *
     * @param applicationType individual 또는 team
     */
    public record PrepareCommand(String applicationType, Long clubId, List<Long> clubMemberIds,
                                 boolean agreeRules, boolean agreePayment) {
    }

    /**
     * 결제 승인 결과.
     *
     * @param receiptNo 신청을 만들었으면 접수 번호, 아니면 null
     */
    public record CompletionResult(boolean success, Long matchId, String receiptNo, String message) {

        public static CompletionResult ok(Long matchId, String receiptNo) {
            return new CompletionResult(true, matchId, receiptNo, null);
        }

        public static CompletionResult fail(Long matchId, String message) {
            return new CompletionResult(false, matchId, null, message);
        }
    }
}
