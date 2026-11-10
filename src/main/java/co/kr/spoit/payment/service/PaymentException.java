package co.kr.spoit.payment.service;

/** 결제를 시작할 수 없을 때 화면에 그대로 보여 줄 안내 */
public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }
}
