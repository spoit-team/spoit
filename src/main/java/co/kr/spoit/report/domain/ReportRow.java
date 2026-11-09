package co.kr.spoit.report.domain;

import java.time.LocalDateTime;

/**
 * 신고 목록·상세 조회 SQL의 결과 행이다. 화면에 필요한 표시 이름을 조인해 담는다.
 *
 * <p>목록 쿼리는 {@code summary}를 선택하지 않아 값이 {@code null}이고, 상세 쿼리만 원문을
 * 채운다. 이 구분으로 목록 응답에 신고 원문이 실리지 않는다.</p>
 */
public class ReportRow {

    private Long reportId;
    private Long matchId;
    private String facilityName;
    private String matchTitle;
    private String targetName;
    private String reporterName;
    private String category;
    private String status;
    private LocalDateTime createdAt;
    private String summary;
    private String resolution;

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getMatchId() {
        return matchId;
    }

    public void setMatchId(Long matchId) {
        this.matchId = matchId;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getMatchTitle() {
        return matchTitle;
    }

    public void setMatchTitle(String matchTitle) {
        this.matchTitle = matchTitle;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }
}
