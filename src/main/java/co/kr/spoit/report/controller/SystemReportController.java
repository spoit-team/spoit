package co.kr.spoit.report.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import co.kr.spoit.admin.common.AdminShellModel;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.report.dto.SystemReportPage;
import co.kr.spoit.report.service.PenaltyService;
import co.kr.spoit.report.service.ReportAccessDeniedException;
import co.kr.spoit.report.service.ReportNotFoundException;
import co.kr.spoit.report.service.ReportStateConflictException;
import co.kr.spoit.report.service.SystemReportService;

/**
 * 시스템 관리자 신고 접수함 화면의 진입점이다.
 *
 * <p>{@code GET /admin/system/reports}는 목록과, {@code reportId}로 요청된 한 건의 상세만 조회한다.
 * 목록에는 신고 원문이 없고 상세에만 있다. {@code POST /admin/system/reports/{reportId}/decision}은
 * 검토 중·처리 완료·기각을 적용한 뒤 필터와 선택을 보존해 같은 화면으로 되돌린다.</p>
 *
 * <p>모델 계약(UI 고정): {@code reports}(reportId, matchId, facilityName, matchTitle, targetName,
 * reporterName, category, status, createdAt), {@code selectedReport}(nullable, 추가로 summary·
 * resolution), {@code statusFilter}, {@code reportPage}, {@code hasNextReports}. 쿼리 파라미터는
 * {@code status}·{@code reportId}·{@code page}. 페이지는 0부터 시작하며 잘못된 입력은 정규화한다.</p>
 */
@Controller
public class SystemReportController {

    private static final String VIEW = "admin/report-system";
    private static final String REDIRECT_LOGIN = "redirect:/login";

    private final SystemReportService systemReportService;

    public SystemReportController(SystemReportService systemReportService) {
        this.systemReportService = systemReportService;
    }

    /** 신고 접수함 목록(offset 페이지)과 선택 상세를 렌더링한다. */
    @GetMapping("/admin/system/reports")
    public String systemReports(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "reportId", required = false) Long reportId,
            @RequestParam(name = "page", required = false) String page,
            Model model) {
        AdminShellModel.apply(model);
        SystemReportPage reportPage = systemReportService.loadPage(status, reportId,
                ReportPageParameter.parse(page));
        model.addAttribute("reports", reportPage.getReports());
        model.addAttribute("selectedReport", reportPage.getSelectedReport());
        model.addAttribute("statusFilter", reportPage.getStatusFilter());
        model.addAttribute("reportPage", reportPage.getReportPage());
        model.addAttribute("hasNextReports", reportPage.isHasNextReports());
        return VIEW;
    }

    /** 신고 처리 방향을 적용하고 필터·페이지·선택을 보존해 같은 화면으로 되돌린다. */
    @PostMapping("/admin/system/reports/{reportId}/decision")
    public String decide(@PathVariable("reportId") long reportId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "expectedStatus", required = false) String expectedStatus,
            @RequestParam(name = "resolution", required = false) String resolution,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) String page,
            RedirectAttributes redirect) {
        if (principal == null) {
            return REDIRECT_LOGIN;
        }
        try {
            systemReportService.decide(reportId, action, expectedStatus, resolution, principal.getUserId());
            redirect.addFlashAttribute("message", messageFor(action));
        } catch (IllegalArgumentException | ReportAccessDeniedException | ReportStateConflictException
                | ReportNotFoundException exception) {
            // 상태 경합·입력 오류는 화면 안내로 되돌린다.
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return redirectTo(reportId, status, page);
    }

    /**
     * 신고 상세 모달에서 제재(경고·이용 제한)를 적용하고 처리 완료로 마무리한다.
     *
     * <p>대상 회원 번호는 화면 값이 아니라 서비스가 신고 행에서 확인한다. 제재 종류·기간·사유는
     * {@code penaltyType}·{@code durationDays}·{@code reason}으로 받고, 검증 실패는 flash 오류로
     * 되돌린다. 성공해도 같은 필터·페이지·선택으로 PRG 복귀한다.</p>
     */
    @PostMapping("/admin/system/reports/{reportId}/penalty")
    public String applyPenalty(@PathVariable("reportId") long reportId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "penaltyType", required = false) String penaltyType,
            @RequestParam(name = "durationDays", required = false) Integer durationDays,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam(name = "expectedStatus", required = false) String expectedStatus,
            @RequestParam(name = "resolution", required = false) String resolution,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) String page,
            RedirectAttributes redirect) {
        if (principal == null) {
            return REDIRECT_LOGIN;
        }
        try {
            systemReportService.applyPenalty(reportId, penaltyType, durationDays, reason, expectedStatus,
                    resolution, principal.getUserId());
            redirect.addFlashAttribute("message", penaltyMessageFor(penaltyType));
        } catch (IllegalArgumentException | ReportAccessDeniedException | ReportStateConflictException
                | ReportNotFoundException exception) {
            // 검증·상태 경합은 모달 밖 flash로 안내하고 목록을 다시 그린다.
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return redirectTo(reportId, status, page);
    }

    private static String redirectTo(long reportId, String status, String page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/system/reports");
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status.strip());
        }
        int safePage = ReportPageParameter.parse(page);
        if (safePage > 0) {
            builder.queryParam("page", safePage);
        }
        builder.queryParam("reportId", reportId);
        return "redirect:" + builder.build().encode().toUriString() + "#report-case";
    }

    private static String penaltyMessageFor(String penaltyType) {
        if (PenaltyService.TYPE_RESTRICTION.equals(penaltyType)) {
            return "이용 제한을 적용하고 신고를 처리 완료로 마무리했습니다.";
        }
        return "경고를 부여하고 신고를 처리 완료로 마무리했습니다.";
    }

    private static String messageFor(String action) {
        if (action == null) {
            return "신고 처리 방향을 저장했습니다.";
        }
        return switch (action.strip()) {
            case "review" -> "신고를 검토 중으로 옮겼습니다.";
            case "complete" -> "신고를 처리 완료로 마무리했습니다.";
            case "dismiss" -> "신고를 기각했습니다.";
            default -> "신고 처리 방향을 저장했습니다.";
        };
    }
}
