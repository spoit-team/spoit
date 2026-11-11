package co.kr.spoit.report.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code report} 한 행. 저장(생성 키)과 잠금 단건 조회에 함께 쓴다.
 *
 * <p>상태·유형은 DB 원문 문자열로 보관하고 해석은 {@link ReportStatus}·{@link ReportCategory}가
 * 맡는다. 알 수 없는 기존 값도 원문 그대로 읽어 화면에 보여 줄 수 있게 한다.</p>
 */
@Getter
@Setter
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
}
