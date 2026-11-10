package co.kr.spoit.report.service;

import co.kr.spoit.report.domain.ReportRow;
import co.kr.spoit.report.dto.ReportDetail;
import co.kr.spoit.report.dto.ReportListItem;

/**
 * 신고 조회 행을 화면 모델로 옮기는 조립기다.
 *
 * <p>목록 모델에는 신고 원문이 없고, 상세 모델에만 원문이 실린다. 시스템 화면만 신고자 이름을
 * 목록에 노출하므로 호출 측이 {@code includeReporter}로 구분한다.</p>
 */
final class ReportViewAssembler {

    private ReportViewAssembler() {
    }

    static ReportListItem toListItem(ReportRow row, boolean includeReporter) {
        return new ReportListItem(row.getReportId(), row.getMatchId(), row.getFacilityName(),
                row.getMatchTitle(), row.getTargetName(), includeReporter ? row.getReporterName() : null,
                row.getCategory(), row.getStatus(), ReportListItem.formatCreatedAt(row.getCreatedAt()),
                row.getResolution());
    }

    static ReportDetail toDetail(ReportRow row) {
        return new ReportDetail(row.getReportId(), row.getMatchId(), row.getTargetUserId(),
                row.getFacilityName(), row.getMatchTitle(), row.getTargetName(), row.getReporterName(),
                row.getCategory(), row.getStatus(), ReportDetail.formatCreatedAt(row.getCreatedAt()),
                row.getSummary(), row.getResolution());
    }
}
