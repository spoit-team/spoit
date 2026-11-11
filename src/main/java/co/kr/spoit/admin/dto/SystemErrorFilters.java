package co.kr.spoit.admin.dto;

/**
 * 서버·오류 로그 화면의 검색 조건이다.
 *
 * <p>{@code severity}는 {@code error}·{@code warn}·{@code all}만 의미가 있고, {@code period}는
 * 시간 단위(24·168)다. 알 수 없는 값은 전체·기본 기간으로 되돌린다.</p>
 */
public class SystemErrorFilters {

    private final String q;
    private final String severity;
    private final int period;

    public SystemErrorFilters(String q, String severity, int period) {
        this.q = q;
        this.severity = severity;
        this.period = period;
    }

    public String getQ() {
        return q;
    }

    public String getSeverity() {
        return severity;
    }

    public int getPeriod() {
        return period;
    }
}
