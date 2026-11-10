package co.kr.spoit.admin.dto;

import java.util.List;

/** 서버·오류 로그 화면 모델(지표·검색 조건·로그 행)이다. */
public class SystemErrorLogPage {

    private final SystemErrorMetrics metrics;
    private final SystemErrorFilters filters;
    private final List<SystemErrorLog> errors;

    public SystemErrorLogPage(SystemErrorMetrics metrics, SystemErrorFilters filters,
            List<SystemErrorLog> errors) {
        this.metrics = metrics;
        this.filters = filters;
        this.errors = List.copyOf(errors);
    }

    public SystemErrorMetrics getMetrics() {
        return metrics;
    }

    public SystemErrorFilters getFilters() {
        return filters;
    }

    public List<SystemErrorLog> getErrors() {
        return errors;
    }
}
