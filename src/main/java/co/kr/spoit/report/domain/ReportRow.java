package co.kr.spoit.report.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 신고 목록·상세 조회 SQL의 결과 행이다. 화면에 필요한 표시 이름을 조인해 담는다.
 *
 * <p>목록 쿼리는 {@code summary}를 선택하지 않아 값이 {@code null}이고, 상세 쿼리만 원문을
 * 채운다. 이 구분으로 목록 응답에 신고 원문이 실리지 않는다.</p>
 */
@Getter
@Setter
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
}
