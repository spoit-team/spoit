package co.kr.spoit.report.dto;

import java.time.format.DateTimeFormatter;

import lombok.Getter;

/**
 * 신고 목록 한 행의 화면 모델이다.
 *
 * <p>시설·시스템 두 화면이 같은 타입을 쓰고, 시스템 화면만 {@code reporterName}을 채운다.
 * 신고 원문({@code summary})은 목록에 넣지 않는다.</p>
 */
@Getter
public class ReportListItem {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String NONE = "—";

    private final Long reportId;
    private final Long matchId;
    private final String facilityName;
    private final String matchTitle;
    private final String targetName;
    private final String reporterName;
    private final String category;
    private final String status;
    private final String createdAt;
    private final String resolution;

    public ReportListItem(Long reportId, Long matchId, String facilityName, String matchTitle,
            String targetName, String reporterName, String category, String status, String createdAt,
            String resolution) {
        this.reportId = reportId;
        this.matchId = matchId;
        this.facilityName = facilityName;
        this.matchTitle = matchTitle;
        this.targetName = targetName;
        this.reporterName = reporterName;
        this.category = category;
        this.status = status;
        this.createdAt = createdAt;
        this.resolution = resolution;
    }

    /** 화면 표시용 접수 시각. 값이 없으면 {@code "—"}. */
    public static String formatCreatedAt(java.time.LocalDateTime value) {
        return value == null ? NONE : DATE_TIME.format(value);
    }
}
