package co.kr.spoit.report.dto;

/** 페널티 관리 화면의 검색 조건이다. {@code status}는 {@code all}·{@code active}·{@code done}·{@code released}. */
public class PenaltyFilters {

    private final String q;
    private final String status;
    private final int page;

    public PenaltyFilters(String q, String status, int page) {
        this.q = q;
        this.status = status;
        this.page = page;
    }

    public String getQ() {
        return q;
    }

    public String getStatus() {
        return status;
    }

    public int getPage() {
        return page;
    }
}
