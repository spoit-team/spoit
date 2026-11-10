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
    private final PenaltyService penaltyService;

    public SystemReportService(ReportMapper reportMapper,
            ReportNotificationService reportNotificationService, PenaltyService penaltyService) {
        this.reportMapper = reportMapper;
        this.reportNotificationService = reportNotificationService;
        this.penaltyService = penaltyService;
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
                : loadDetail(selectedReportId);

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

    /** 신고 한 건의 상세를 단독으로 조회한다. 상세가 필요한 호출은 목록 조회를 거치지 않는다. */
    @Transactional(readOnly = true)
    public ReportDetail loadDetail(Long reportId) {
        if (reportId == null || reportId <= 0) {
            throw new ReportNotFoundException("신고를 찾을 수 없습니다.");
        }
        ReportDetail detail = detail(reportMapper.findSystemReport(reportId));
        // 상세 조회만으로는 대상 회원 번호가 비어 있을 수 있어(레거시 행) 그대로 둔다.
        return detail;
    }

    /**
     * 신고 상세에서 확인한 대상 회원에게 제재를 적용한다.
     *
     * <p>대상 회원 번호는 <strong>클라이언트 값이 아니라 서버가 조회한 신고 상세</strong>에서 가져온다.
     * 신고 행을 잠근 뒤 현재 상태를 확인해, 이미 끝난 신고에는 제재를 걸지 않는다. 상태 변경과
     * 알림·제재 기록은 같은 트랜잭션에서 처리한다.</p>
     *
     * @param reportId      근거 신고 번호
     * @param penaltyType   {@code 경고} 또는 {@code 이용 제한}
     * @param durationDays  이용 제한 기간(1/3/7/14/30). 경고에서는 무시한다
     * @param reason        제재 사유
     * @param expectedStatus 화면이 본 신고 상태
     * @param resolution    신고 처리 사유(선택, 비어 있으면 제재 사유를 쓴다)
     * @param reviewerUserId 인증 주체의 시스템 관리자 번호
     */
    @Transactional
    public void applyPenalty(Long reportId, String penaltyType, Integer durationDays, String reason,
            String expectedStatus, String resolution, Long reviewerUserId) {
        if (reportId == null || reportId <= 0) {
            throw new ReportNotFoundException("신고를 찾을 수 없습니다.");
        }
        if (reviewerUserId == null || reviewerUserId <= 0) {
            throw new ReportAccessDeniedException("로그인이 필요합니다.");
        }
        // 화면이 현재 상태를 보내지 않을 수는 있지만(계약상 선택 값), 비어 있지 않은 값이면 반드시
        // 알려진 상태여야 한다. 알 수 없는 값을 '보내지 않음'으로 취급하면 상태 경합 검사를 우회한다.
        Optional<ReportStatus> expected;
        if (expectedStatus == null || expectedStatus.isBlank()) {
            expected = Optional.empty();
        } else {
            expected = Optional.of(ReportStatus.from(expectedStatus)
                    .orElseThrow(() -> new ReportStateConflictException(
                            "현재 신고 상태를 확인할 수 없습니다. 화면을 새로 고친 뒤 다시 시도해 주세요.")));
        }
        String trimmedReason = PenaltyService.normalizeReason(reason);

        Report report = reportMapper.findReportForUpdate(reportId);
        if (report == null) {
            throw new ReportNotFoundException("신고를 찾을 수 없습니다.");
        }
        Optional<ReportStatus> currentStatus = ReportStatus.from(report.getStatus());
        if (currentStatus.isEmpty()) {
            throw new ReportStateConflictException("현재 신고 상태로는 제재를 적용할 수 없습니다.");
        }
        // 화면이 본 상태를 잠근 행에 그대로 강제한다. 화면이 보낸 기대 상태로 조건부 갱신해,
        // 잠금 이후 상태가 달라졌다면 DB에서도 0행으로 드러나게 한다.
        String expectedDbValue = expected.map(ReportStatus::dbValue).orElse(report.getStatus());
        if (expected.isPresent() && !report.getStatus().equals(expectedDbValue)) {
            throw new ReportStateConflictException(
                    "다른 관리자가 먼저 처리해 신고 상태가 바뀌었습니다. 화면을 새로 고친 뒤 다시 시도해 주세요.");
        }
        if (!currentStatus.get().canFinish()) {
            throw new ReportStateConflictException("이미 처리가 끝난 신고에는 제재를 적용할 수 없습니다.");
        }
        Long targetUserId = report.getTargetUserId();
        if (targetUserId == null) {
            throw new IllegalArgumentException("신고 대상 회원을 확인할 수 없습니다.");
        }

        penaltyService.issueForReport(reportId, targetUserId, reviewerUserId, penaltyType, durationDays,
                trimmedReason);

        // 제재를 적용하면 신고를 처리 완료로 마무리한다. 처리 사유는 입력한 사유를 우선 쓴다.
        String finalResolution = resolution != null && !resolution.isBlank()
                ? normalizeResolution(resolution)
                : normalizeResolution(trimmedReason.length() > RESOLUTION_MAX
                        ? trimmedReason.substring(0, RESOLUTION_MAX)
                        : trimmedReason);
        int changed = reportMapper.applyFinalDecision(reportId, expectedDbValue,
                ReportStatus.COMPLETED.dbValue(), finalResolution, reviewerUserId);
        if (changed == 0) {
            throw new ReportStateConflictException(
                    "다른 관리자가 먼저 처리해 신고 상태가 바뀌었습니다. 화면을 새로 고친 뒤 다시 시도해 주세요.");
        }
        reportNotificationService.notifyResolved(reportId, report.getReporterUserId(),
                ReportStatus.COMPLETED.dbValue());
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
