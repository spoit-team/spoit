package co.kr.spoit.report.service;

/**
 * 신고 처리 상태가 요청한 전이와 맞지 않을 때 던진다.
 *
 * <p>다른 관리자가 먼저 처리해 {@code expectedStatus}가 바뀐 경우와, 끝난 건을 다시 처리하려는
 * 경우를 같은 예외로 안내한다.</p>
 */
public class ReportStateConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReportStateConflictException(String message) {
        super(message);
    }
}
