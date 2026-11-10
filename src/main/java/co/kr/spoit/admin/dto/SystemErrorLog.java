package co.kr.spoit.admin.dto;

/**
 * 서버·오류 로그 화면 한 행의 모델이다.
 *
 * <p>같은 서비스·로그 레벨·코드·요약을 가진 원본 기록을 하나로 묶어 횟수와 마지막 발생 시각만
 * 남긴다. 메시지·상세는 서버가 마스킹한 값이며 화면은 {@code th:text}로만 출력한다.</p>
 */
public class SystemErrorLog {

    private final String time;
    private final String severity;
    private final String severityLabel;
    private final String severityTone;
    private final String service;
    private final String code;
    private final String summary;
    private final int count;
    private final String detail;

    public SystemErrorLog(String time, String severity, String severityLabel, String severityTone,
            String service, String code, String summary, int count, String detail) {
        this.time = time;
        this.severity = severity;
        this.severityLabel = severityLabel;
        this.severityTone = severityTone;
        this.service = service;
        this.code = code;
        this.summary = summary;
        this.count = count;
        this.detail = detail;
    }

    public String getTime() {
        return time;
    }

    /** 필터 비교용 원본 심각도 코드({@code ERROR}/{@code WARN})다. */
    public String getSeverity() {
        return severity;
    }

    public String getSeverityLabel() {
        return severityLabel;
    }

    public String getSeverityTone() {
        return severityTone;
    }

    public String getService() {
        return service;
    }

    public String getCode() {
        return code;
    }

    public String getSummary() {
        return summary;
    }

    public int getCount() {
        return count;
    }

    public String getDetail() {
        return detail;
    }
}
