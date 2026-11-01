package co.kr.spoit.match.service;

/** 신청할 수 없는 상태. 메시지는 화면에 그대로 보여 준다. */
public class MatchApplyException extends RuntimeException {

    public MatchApplyException(String message) {
        super(message);
    }
}
