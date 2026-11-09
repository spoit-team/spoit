package co.kr.spoit.report.domain;

import java.time.LocalDateTime;

/**
 * {@code report} 한 행. 저장(생성 키)과 잠금 단건 조회에 함께 쓴다.
 *
 * <p>상태·유형은 DB 원문 문자열로 보관하고 해석은 {@link ReportStatus}·{@link ReportCategory}가
 * 맡는다. 알 수 없는 기존 값도 원문 그대로 읽어 화면에 보여 줄 수 있게 한다.</p>
 */
public class Report {

    private Long reportId;
    private Long reporterUserId;
    private Long targetUserId;
    private Long matchId;
    private Long reviewedBy;
    private String category;
    private String summary;
    private String status;
    private String resolution;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getReporterUserId() {
        return reporterUserId;
    }

    public void setReporterUserId(Long reporterUserId) {
        this.reporterUserId = reporterUserId;
    }

    public Long getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(Long targetUserId) {
        this.targetUserId = targetUserId;
    }

    public Long getMatchId() {
        return matchId;
    }

    public void setMatchId(Long matchId) {
        this.matchId = matchId;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
