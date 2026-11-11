package co.kr.spoit.report.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 요청한 신고를 찾을 수 없거나 접근 권한이 없을 때 던진다. 화면은 404로 응답한다.
 *
 * <p>권한 밖 신고는 존재 여부를 알리지 않도록 403 대신 404로 처리한다.</p>
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ReportNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ReportNotFoundException(String message) {
        super(message);
    }
}
