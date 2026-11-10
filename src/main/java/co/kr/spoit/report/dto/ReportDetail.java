package co.kr.spoit.report.dto;

import java.time.format.DateTimeFormatter;

import lombok.Getter;

/**
 * 선택한 신고 한 건의 상세 화면 모델이다.
 *
 * <p>목록과 달리 신고 원문({@code summary})을 담는다. 상세는 요청받은 한 건만 조회해 내려주며,
 * 목록 응답에는 이 타입을 싣지 않는다.</p>
 */
@Getter
public class ReportDetail {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Long reportId;
    private final Long matchId;
    private final Long targetUserId;
    private final String facilityName;
    private final String matchTitle;
    private final String targetName;
    private final String reporterName;
    private final String category;
    private final String status;
    private final String createdAt;
    private final String summary;
    private final String resolution;

    public ReportDetail(Long reportId, Long matchId, String facilityName, String matchTitle,
            String targetName, String reporterName, String category, String status, String createdAt,
            String summary, String resolution) {
        this(reportId, matchId, null, facilityName, matchTitle, targetName, reporterName, category, status,
                createdAt, summary, resolution);
    }

    public ReportDetail(Long reportId, Long matchId, Long targetUserId, String facilityName, String matchTitle,
            String targetName, String reporterName, String category, String status, String createdAt,
            String summary, String resolution) {
        this.reportId = reportId;
        this.matchId = matchId;
        this.targetUserId = targetUserId;
        this.facilityName = facilityName;
        this.matchTitle = matchTitle;
        this.targetName = targetName;
        this.reporterName = reporterName;
        this.category = category;
        this.status = status;
        this.createdAt = createdAt;
        this.summary = summary;
        this.resolution = resolution;
    }

    /** 화면 표시용 접수 시각. 값이 없으면 {@code "—"}. */
    public static String formatCreatedAt(java.time.LocalDateTime value) {
        return value == null ? "—" : DATE_TIME.format(value);
    }
}
