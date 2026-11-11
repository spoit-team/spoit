package co.kr.spoit.payment.kakao;

/** 카카오페이 API 호출 실패. code는 카카오페이 error_code 또는 내부 코드(NETWORK, NOT_CONFIGURED 등)다. */
public class KakaoPayException extends RuntimeException {

    private final String code;

    public KakaoPayException(String code, String message) {
        super(message);
        this.code = code;
    }

    public KakaoPayException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
