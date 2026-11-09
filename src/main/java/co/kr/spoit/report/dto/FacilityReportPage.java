package co.kr.spoit.report.dto;

import java.util.List;

/**
 * 시설 관리자 신고 화면 모델이다. 컨트롤러가 조회 결과를 이 계약 이름 그대로 모델에 옮긴다.
 *
 * <p>경기 선택지·확정 참가자·본인이 접수한 신고 목록과, 요청으로 선택된 경기·대상·신고를 담는다.
 * 경기·신고 목록은 offset 페이지이며 0부터 시작한다. {@code hasNextMatches}/{@code hasNextReports}는
 * pageSize+1 조회로 판정한 다음 페이지 존재 여부다. 신고 원문은 {@code selectedReport} 한 건에만 있다.</p>
 */
public class FacilityReportPage {

    private final List<ReportMatchOption> matches;
    private final List<ReportParticipantOption> participants;
    private final List<ReportListItem> reports;
    private final Long selectedMatchId;
    private final Long selectedTargetUserId;
    private final ReportDetail selectedReport;
    private final int matchPage;
    private final boolean hasNextMatches;
    private final int reportPage;
    private final boolean hasNextReports;

    public FacilityReportPage(List<ReportMatchOption> matches, List<ReportParticipantOption> participants,
            List<ReportListItem> reports, Long selectedMatchId, Long selectedTargetUserId,
            ReportDetail selectedReport, int matchPage, boolean hasNextMatches, int reportPage,
            boolean hasNextReports) {
        this.matches = List.copyOf(matches);
        this.participants = List.copyOf(participants);
        this.reports = List.copyOf(reports);
        this.selectedMatchId = selectedMatchId;
        this.selectedTargetUserId = selectedTargetUserId;
        this.selectedReport = selectedReport;
        this.matchPage = matchPage;
        this.hasNextMatches = hasNextMatches;
        this.reportPage = reportPage;
        this.hasNextReports = hasNextReports;
    }

    /** 페이지 정보가 없는 빈 화면 모델이다. */
    public static FacilityReportPage empty() {
        return new FacilityReportPage(List.of(), List.of(), List.of(), null, null, null, 0, false, 0, false);
    }

    public List<ReportMatchOption> getMatches() {
        return matches;
    }

    public List<ReportParticipantOption> getParticipants() {
        return participants;
    }

    public List<ReportListItem> getReports() {
        return reports;
    }

    public Long getSelectedMatchId() {
        return selectedMatchId;
    }

    public Long getSelectedTargetUserId() {
        return selectedTargetUserId;
    }

    public ReportDetail getSelectedReport() {
        return selectedReport;
    }

    public int getMatchPage() {
        return matchPage;
    }

    public boolean isHasNextMatches() {
        return hasNextMatches;
    }

    public int getReportPage() {
        return reportPage;
    }

    public boolean isHasNextReports() {
        return hasNextReports;
    }
}
