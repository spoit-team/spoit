package co.kr.spoit.report.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.notification.service.ReportNotificationService;
import co.kr.spoit.report.domain.Report;
import co.kr.spoit.report.domain.ReportDecisionAction;
import co.kr.spoit.report.domain.ReportRow;
import co.kr.spoit.report.domain.ReportStatus;
import co.kr.spoit.report.dto.ReportDetail;
import co.kr.spoit.report.dto.ReportListItem;
import co.kr.spoit.report.dto.SystemReportPage;
import co.kr.spoit.report.mapper.ReportMapper;

/**
 * 시스템 관리자의 신고 접수함 조회·결정을 담당한다.
 *
 * <p>목록은 신고 원문 없이 요약 정보만 offset 페이지로 내려주고, 상세는 요청받은 한 건만
 * 원문·처리 사유와 함께 조회한다. 상태 필터는 알려진 네 상태만 허용하며, 그 밖의 값은 전체 조회로
 * 되돌린다.</p>
 *
 * <p>결정은 신고 행을 먼저 잠그고 {@code expectedStatus}를 대조해 허용 전이만 적용한다. 접수는
 * 검토 중으로만, 처리 완료·기각은 접수·검토 중에서만 가능하며 이미 끝난 건은 재처리되지 않는다.
 * 상태가 실제로 바뀐 경우에만 신고자에게 결과 알림을 예약한다. 제재·파일 첨부·대상자 통지는 하지
 * 않는다.</p>
 */
@Service
public class SystemReportService {

    /** 한 페이지에 보여 주는 신고 건수. offset 계산이 같은 값을 쓰도록 신고 화면 상수를 공유한다. */
    static final int PAGE_SIZE = FacilityReportService.PAGE_SIZE;

    /** 안전한 페이지 상한. offset 계산과 어긋나지 않게 신고 화면 상수를 공유한다. */
    static final int MAX_PAGE = FacilityReportService.MAX_PAGE;

    /** 처리 사유 최소·최대 길이. */
    private static final int RESOLUTION_MIN = 1;
    private static final int RESOLUTION_MAX = 255;

    /** 상태 필터가 없을 때의 값. */
    private static final String ALL_STATUS = "all";

    private final ReportMapper reportMapper;
    private final ReportNotificationService reportNotificationService;

    public SystemReportService(ReportMapper reportMapper,
            ReportNotificationService reportNotificationService) {
        this.reportMapper = reportMapper;
        this.reportNotificationService = reportNotificationService;
    }

    /** 접수함 목록(offset 페이지)과, 요청으로 고른 한 건의 상세를 구성한다. */
    @Transactional(readOnly = true)
    public SystemReportPage loadPage(String statusFilter, Long selectedReportId, int reportPage) {
        String normalizedFilter = normalizeFilter(statusFilter);
        int safePage = FacilityReportService.normalizePage(reportPage);
        List<ReportRow> rows = reportMapper.findSystemReports(normalizedFilter, PAGE_SIZE + 1,
                FacilityReportService.offset(safePage));
        boolean hasNextReports = rows != null && rows.size() > PAGE_SIZE;

        List<ReportListItem> reports = new ArrayList<>(Math.min(rows == null ? 0 : rows.size(), PAGE_SIZE));
        if (rows != null) {
            for (ReportRow row : rows) {
                if (reports.size() >= PAGE_SIZE) {
                    break;
                }
                if (row != null) {
                    reports.add(ReportViewAssembler.toListItem(row, true));
                }
            }
        }

        // 상세는 reportId에 관계없이 요청받은 한 건만 조회한다. 원문·처리 사유는 이 모델에만 있다.
        ReportDetail selectedReport = selectedReportId == null || selectedReportId <= 0
                ? null
                : detail(reportMapper.findSystemReport(selectedReportId));

        return new SystemReportPage(reports, selectedReport, normalizedFilter, safePage, hasNextReports);
    }

    /**
     * 신고 처리 방향을 적용한다.
     *
     * @param reportId       처리 대상 신고 번호
     * @param action         {@code review}·{@code complete}·{@code dismiss}
     * @param expectedStatus 화면이 본 현재 상태. 실제 상태와 다르면 거부한다
     * @param resolution     처리 사유(최종 처리에서 필수, 1~255자)
     * @param reviewerUserId 인증 주체의 시스템 관리자 번호
     */
    @Transactional
    public void decide(Long reportId, String action, String expectedStatus, String resolution,
            Long reviewerUserId) {
        if (reportId == null || reportId <= 0) {
            throw new ReportNotFoundException("신고를 찾을 수 없습니다.");
        }
        if (reviewerUserId == null || reviewerUserId <= 0) {
            throw new ReportAccessDeniedException("로그인이 필요합니다.");
        }
        ReportDecisionAction decision = ReportDecisionAction.from(action)
                .orElseThrow(() -> new IllegalArgumentException("처리 방향을 선택해 주세요."));
        ReportStatus expected = ReportStatus.from(expectedStatus)
                .orElseThrow(() -> new ReportStateConflictException(
                        "현재 신고 상태를 확인할 수 없습니다. 화면을 새로 고친 뒤 다시 시도해 주세요."));
        String trimmedResolution = decision.isFinal() ? normalizeResolution(resolution) : null;

        // 신고 행을 잠가 다른 관리자의 동시 처리를 직렬화한다.
        Report report = reportMapper.findReportForUpdate(reportId);
        if (report == null) {
            throw new ReportNotFoundException("신고를 찾을 수 없습니다.");
        }
        // 알 수 없는 기존 상태 레코드는 열람만 허용하고 임의로 변환하지 않는다.
        Optional<ReportStatus> currentStatus = ReportStatus.from(report.getStatus());
        if (currentStatus.isEmpty()) {
            throw new ReportStateConflictException("현재 신고 상태로는 처리할 수 없습니다.");
        }
        if (!report.getStatus().equals(expected.dbValue())) {
            throw new ReportStateConflictException(
                    "다른 관리자가 먼저 처리해 신고 상태가 바뀌었습니다. 화면을 새로 고친 뒤 다시 시도해 주세요.");
        }

        int changed;
        if (decision == ReportDecisionAction.REVIEW) {
            if (!currentStatus.get().canReview()) {
                throw new ReportStateConflictException("검토 중으로 옮길 수 없는 신고 상태입니다.");
            }
            changed = reportMapper.markReviewing(reportId, expected.dbValue(),
                    decision.targetStatus().dbValue(), reviewerUserId);
        } else {
            if (!currentStatus.get().canFinish()) {
                throw new ReportStateConflictException("이미 처리가 끝난 신고는 다시 처리할 수 없습니다.");
            }
            changed = reportMapper.applyFinalDecision(reportId, expected.dbValue(),
                    decision.targetStatus().dbValue(), trimmedResolution, reviewerUserId);
        }

        if (changed == 0) {
            throw new ReportStateConflictException(
                    "다른 관리자가 먼저 처리해 신고 상태가 바뀌었습니다. 화면을 새로 고친 뒤 다시 시도해 주세요.");
        }

        // 상태가 실제로 바뀐 최종 처리에서만 신고자에게 결과를 알린다.
        if (decision.isFinal()) {
            reportNotificationService.notifyResolved(reportId, report.getReporterUserId(),
                    decision.targetStatus().dbValue());
        }
    }

    private static String normalizeFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return null;
        }
        String value = statusFilter.strip();
        if (ALL_STATUS.equalsIgnoreCase(value)) {
            return null;
        }
        // 알 수 없는 상태 값은 전체 조회로 되돌려 빈 화면에 갇히지 않게 한다.
        return ReportStatus.from(value).map(ReportStatus::dbValue).orElse(null);
    }

    private static String normalizeResolution(String resolution) {
        if (resolution == null || resolution.isBlank()) {
            throw new IllegalArgumentException("처리 완료 또는 기각에는 처리 사유를 입력해 주세요.");
        }
        String value = resolution.strip();
        if (value.length() < RESOLUTION_MIN || value.length() > RESOLUTION_MAX) {
            throw new IllegalArgumentException("처리 사유는 1~255자로 입력해 주세요.");
        }
        return value;
    }

    private static ReportDetail detail(ReportRow row) {
        if (row == null) {
            throw new ReportNotFoundException("신고를 찾을 수 없습니다.");
        }
        return ReportViewAssembler.toDetail(row);
    }
}
