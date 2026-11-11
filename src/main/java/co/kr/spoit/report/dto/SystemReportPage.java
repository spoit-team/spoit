package co.kr.spoit.report.dto;

import java.util.List;

/**
 * 시스템 관리자 신고 접수함 화면 모델이다.
 *
 * <p>목록은 offset 페이지이며 0부터 시작하고, {@code hasNextReports}는 pageSize+1 조회로 판정한 다음
 * 페이지 존재 여부다. 목록에는 신고 원문이 없고, 요청으로 고른 한 건만 {@code selectedReport}에
 * 원문·처리 사유를 담는다.</p>
 */
public class SystemReportPage {

    private final List<ReportListItem> reports;
    private final ReportDetail selectedReport;
    private final String statusFilter;
    private final int reportPage;
    private final boolean hasNextReports;

    public SystemReportPage(List<ReportListItem> reports, ReportDetail selectedReport, String statusFilter,
            int reportPage, boolean hasNextReports) {
        this.reports = List.copyOf(reports);
        this.selectedReport = selectedReport;
        this.statusFilter = statusFilter;
        this.reportPage = reportPage;
        this.hasNextReports = hasNextReports;
    }

    /** 페이지 정보가 없는 빈 화면 모델이다. */
    public static SystemReportPage empty() {
        return new SystemReportPage(List.of(), null, null, 0, false);
    }

    public List<ReportListItem> getReports() {
        return reports;
    }

    public ReportDetail getSelectedReport() {
        return selectedReport;
    }

    public String getStatusFilter() {
        return statusFilter;
    }

    public int getReportPage() {
        return reportPage;
    }

    public boolean isHasNextReports() {
        return hasNextReports;
    }
}
