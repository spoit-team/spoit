package co.kr.spoit.admin.controller;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.kr.spoit.admin.common.AdminShellModel;
import co.kr.spoit.admin.dto.SystemDashboard;
import co.kr.spoit.admin.dto.SystemErrorLogPage;
import co.kr.spoit.admin.service.SystemErrorLogService;
import co.kr.spoit.admin.service.SystemMonitoringService;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.dto.AdminUserActionResult;
import co.kr.spoit.member.dto.AdminUserPage;
import co.kr.spoit.member.service.AdminUserService;
import co.kr.spoit.report.dto.PenaltyPage;
import co.kr.spoit.report.service.PenaltyService;

/**
 * 시스템 관리자 화면 템플릿을 렌더링하는 페이지 컨트롤러이다.
 *
 * <p>{@code view=dashboard|errors|users|penalties|settings}와 검색 조건
 * ({@code q/role/status/period/page})을 그대로 모델에 반영한다. 모니터링 dashboard는
 * {@link SystemMonitoringService}가 실제 측정치로 구성하고, 오류 로그는
 * {@link SystemErrorLogService}가 메모리 버퍼에서, 사용자 관리는 {@link AdminUserService}가,
 * 페널티 관리는 {@link PenaltyService}가 실제 조회 결과로 만든다. 컨트롤러는 모델 조립과 폼
 * 처리(PRG)만 맡고 매퍼를 직접 호출하지 않는다.</p>
 *
 * <p>사용자 관리의 상태 변경(승인·거절·정지·복구·역할 변경)은 모두 POST 폼이며, 로그인 관리자
 * 번호는 인증 주체({@link MemberPrincipal})에서만 가져온다. 감사 로그 화면은 제거되어
 * {@code audit_log}에 신규 기록을 넣지 않는다.</p>
 */
@Controller
public class SystemAdminController {

    /** 여러 화면이 공유하는 불변 빈 목록이다. */
    private static final List<Object> EMPTY_LIST = Collections.emptyList();

    /** 알려진 화면 값. 그 밖의 값은 모니터링으로 되돌린다. */
    private static final String VIEW_DASHBOARD = "dashboard";
    private static final String VIEW_ERRORS = "errors";
    private static final String VIEW_USERS = "users";
    private static final String VIEW_PENALTIES = "penalties";
    private static final String VIEW_SETTINGS = "settings";

    private final SystemMonitoringService systemMonitoringService;
    private final SystemErrorLogService systemErrorLogService;
    private final AdminUserService adminUserService;
    private final PenaltyService penaltyService;

    public SystemAdminController(SystemMonitoringService systemMonitoringService,
            SystemErrorLogService systemErrorLogService, AdminUserService adminUserService,
            PenaltyService penaltyService) {
        this.systemMonitoringService = systemMonitoringService;
        this.systemErrorLogService = systemErrorLogService;
        this.adminUserService = adminUserService;
        this.penaltyService = penaltyService;
    }

    /* ------------------------------------------------------------------
     * 인증 화면: 로그인은 MemberAuthController, 회원가입은 MemberSignupController가 담당한다.
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------
     * 마이페이지: 요약·프로필 수정 화면 모두 member.controller.MyPageController가 담당한다.
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------
     * 매칭 화면(목록/카드/상세/신청)은 MatchController가 담당한다.
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------
     * 시설 관리자 화면(시설 관리·운영)은 FacilityAdminController,
     * MatchOperationsController가 담당한다.
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------
     * 시스템 관리자 화면: 공통 셸(AdminShellModel) 위에 실제 조회 결과를 얹는다.
     * ------------------------------------------------------------------ */

    /**
     * 시스템 관리 화면을 렌더링한다.
     *
     * <p>{@code view}로 화면을 고르고, 각 화면이 쓰는 검색 조건을 서비스에 그대로 넘긴다. 모든
     * 결과는 실제 조회 값이며, 화면 표시에 쓰이지 않는 감사·CMS 속성은 넣지 않는다.</p>
     */
    @GetMapping("/admin/system")
    public String adminSystem(
            @RequestParam(name = "view", required = false) String view,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "page", required = false) String page,
            Model model) {
        AdminShellModel.apply(model);
        String currentView = normalizeView(view);
        int safePage = parsePage(page);

        model.addAttribute("view", currentView);
        model.addAttribute("dashboard", systemMonitoringService.currentDashboard());

        // 오류 지표는 사이드바 배지도 쓰므로 항상 실제 버퍼에서 계산한다.
        SystemErrorLogPage errorPage = systemErrorLogService.load(q, severity, period);
        model.addAttribute("errorMetrics", errorPage.getMetrics());
        model.addAttribute("errorFilters", errorPage.getFilters());
        model.addAttribute("errors", errorPage.getErrors());

        // 사용자·페널티 목록은 해시로 탭을 전환하는 화면이라 항상 실제 조회 결과를 싣는다.
        // status 값은 각 서비스가 자기 화면의 허용 값만 해석하고 나머지는 전체로 되돌린다.
        PenaltyPage penaltyPage = penaltyService.loadPage(q, status, safePage);
        model.addAttribute("penaltyPage", penaltyPage);
        model.addAttribute("penaltyMetrics", penaltyPage.getMetrics());
        model.addAttribute("penaltyFilters", penaltyPage.getFilters());
        model.addAttribute("penalties", penaltyPage.getPenalties());
        // 다음 페이지 존재 여부는 pageSize+1 조회 계약을 그대로 화면에 전달한다.
        model.addAttribute("hasNextPenalties", penaltyPage.isHasNextPenalties());

        AdminUserPage userPage = adminUserService.loadPage(q, role, status, safePage);
        model.addAttribute("userMetrics", userPage.getMetrics());
        model.addAttribute("userFilters", userPage.getFilters());
        model.addAttribute("users", userPage.getUsers());
        model.addAttribute("userPagination", userPage.getPagination());

        // 신고 접수함은 별도 라우트가 담당한다. 모델 계약상 빈 값만 유지한다.
        model.addAttribute("reports", EMPTY_LIST);
        model.addAttribute("settings", null);
        return "admin/system";
    }

    /**
     * 모니터링 dashboard 최신 상태를 JSON으로 돌려준다.
     *
     * <p>페이지 전체 재로드 없이 15초마다 갱신하는 프런트가 호출한다. 이전 측정치가 캐시에서
     * 재사용되지 않도록 no-store 계열 헤더만 붙인다. 인증·역할 검사는 SecurityConfig의
     * {@code /admin/system/**} 규칙이, 요청 위조 방지는 기존 CSRF 필터가 그대로 담당한다.</p>
     */
    @PostMapping("/admin/system/monitoring")
    @ResponseBody
    public ResponseEntity<SystemDashboard> monitoring() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache")
                .body(systemMonitoringService.currentDashboard());
    }

    /* ------------------------------------------------------------------
     * 사용자 관리: 승인·거절·정지·복구·역할 변경. 모두 PRG로 같은 화면으로 돌아온다.
     * ------------------------------------------------------------------ */

    @PostMapping("/admin/system/users/{userId}/approve")
    public String approveUser(@org.springframework.web.bind.annotation.PathVariable("userId") long userId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) String page,
            RedirectAttributes redirect) {
        return applyUserAction(redirect, principal, q, role, status, page,
                actor -> adminUserService.approve(actor, userId));
    }

    @PostMapping("/admin/system/users/{userId}/reject")
    public String rejectUser(@org.springframework.web.bind.annotation.PathVariable("userId") long userId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) String page,
            RedirectAttributes redirect) {
        return applyUserAction(redirect, principal, q, role, status, page,
                actor -> adminUserService.reject(actor, userId));
    }

    @PostMapping({"/admin/system/users/{userId}/suspend", "/admin/system/users/suspend"})
    public String suspendUser(
            @org.springframework.web.bind.annotation.PathVariable(name = "userId", required = false) Long userId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "userId", required = false) Long userIdParam,
            @RequestParam(name = "durationDays", required = false) Integer durationDays,
            @RequestParam(name = "days", required = false) Integer days,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) String page,
            RedirectAttributes redirect) {
        // JS 없이 제출되면 action이 /admin/system/users/suspend이고 userId는 폼 본문으로 온다.
        Long targetId = userId != null ? userId : userIdParam;
        Integer duration = durationDays != null ? durationDays : days;
        return applyUserAction(redirect, principal, q, role, status, page, actor -> {
            if (targetId == null || targetId <= 0) {
                return AdminUserActionResult.rejected("정지할 계정을 확인해 주세요.");
            }
            return adminUserService.suspend(actor, targetId, duration, reason);
        });
    }

    @PostMapping("/admin/system/users/{userId}/restore")
    public String restoreUser(@org.springframework.web.bind.annotation.PathVariable("userId") long userId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) String page,
            RedirectAttributes redirect) {
        return applyUserAction(redirect, principal, q, role, status, page,
                actor -> adminUserService.restore(actor, userId));
    }

    @PostMapping("/admin/system/users/role")
    public String changeRole(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "reason", required = false) String reason,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) String page,
            RedirectAttributes redirect) {
        // 이 폼의 role은 '변경할 역할'이므로 목록 필터(role)로 되돌리지 않는다.
        return applyUserAction(redirect, principal, q, null, status, page,
                actor -> adminUserService.changeRole(actor, userId, role, reason));
    }

    /* ------------------------------------------------------------------
     * 페널티 관리: 조기 해제.
     * ------------------------------------------------------------------ */

    @PostMapping("/admin/system/penalties/{penaltyId}/release")
    public String releasePenalty(
            @org.springframework.web.bind.annotation.PathVariable("penaltyId") long penaltyId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", required = false) String page,
            RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        try {
            penaltyService.release(penaltyId, principal.getUserId());
            redirect.addFlashAttribute("message", "제재를 조기 해제했습니다.");
        } catch (RuntimeException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return redirectTo(VIEW_PENALTIES, q, null, status, page);
    }

    /* ------------------------------------------------------------------
     * 내보내기: 화면의 링크가 실제 CSV를 돌려준다.
     * ------------------------------------------------------------------ */

    /** 오류 로그를 현재 검색 조건 기준 CSV로 내려준다. */
    @GetMapping("/admin/system/errors/export")
    public ResponseEntity<byte[]> exportErrors(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "period", required = false) String period) {
        SystemErrorLogPage page = systemErrorLogService.load(q, severity, period);
        StringBuilder csv = new StringBuilder("발생 시각,심각도,서비스,오류 코드,요약,횟수,상세\n");
        page.getErrors().forEach(entry -> csv.append(csvCell(entry.getTime())).append(',')
                .append(csvCell(entry.getSeverityLabel())).append(',')
                .append(csvCell(entry.getService())).append(',')
                .append(csvCell(entry.getCode())).append(',')
                .append(csvCell(entry.getSummary())).append(',')
                .append(entry.getCount()).append(',')
                .append(csvCell(entry.getDetail())).append('\n'));
        return csvResponse("system-errors.csv", csv.toString());
    }

    /** 사용자 목록을 현재 검색 조건 기준 CSV로 내려준다. */
    @GetMapping("/admin/system/users/export")
    public ResponseEntity<byte[]> exportUsers(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) String status) {
        StringBuilder csv = new StringBuilder("계정 ID,닉네임,이메일,역할,승인,계정 상태,가입 일자\n");
        adminUserService.findAllForExport(q, role, status).forEach(user -> csv.append(user.getUserId()).append(',')
                .append(csvCell(user.getNickname())).append(',')
                .append(csvCell(user.getEmail())).append(',')
                .append(csvCell(user.getRoleLabel())).append(',')
                .append(csvCell(user.getApprovalLabel())).append(',')
                .append(csvCell(user.getStatusLabel())).append(',')
                .append(csvCell(user.getJoinedAt())).append('\n'));
        return csvResponse("system-users.csv", csv.toString());
    }

    /* ------------------------------------------------------------------
     * 내부 헬퍼
     * ------------------------------------------------------------------ */

    @FunctionalInterface
    private interface UserAction {
        AdminUserActionResult apply(Long actorUserId);
    }

    private String applyUserAction(RedirectAttributes redirect, MemberPrincipal principal, String q,
            String role, String status, String page, UserAction action) {
        if (principal == null) {
            return "redirect:/login";
        }
        try {
            AdminUserActionResult result = action.apply(principal.getUserId());
            redirect.addFlashAttribute(result.applied() ? "message" : "error", result.message());
        } catch (RuntimeException exception) {
            // 입력·상태 오류는 화면 안내로 돌려보내고 저장하지 않는다.
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return redirectTo(VIEW_USERS, q, role, status, page);
    }

    private static String redirectTo(String view, String q, String role, String status, String page) {
        org.springframework.web.util.UriComponentsBuilder builder =
                org.springframework.web.util.UriComponentsBuilder.fromPath("/admin/system");
        builder.queryParam("view", view);
        if (q != null && !q.isBlank()) {
            builder.queryParam("q", q.strip());
        }
        if (role != null && !role.isBlank()) {
            builder.queryParam("role", role.strip());
        }
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status.strip());
        }
        int safePage = parsePage(page);
        if (safePage > 0) {
            builder.queryParam("page", safePage);
        }
        return "redirect:" + builder.build().encode().toUriString();
    }

    private static ResponseEntity<byte[]> csvResponse(String filename, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build()
                                .toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    /** CSV 한 칸. 쉼표·따옴표·줄바꿈은 따옴표로 감싸고, 수식 주입을 막기 위해 앞머리 문자를 무력화한다. */
    static String csvCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = value;
        char first = text.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    static String normalizeView(String view) {
        if (view == null || view.isBlank()) {
            return VIEW_DASHBOARD;
        }
        return switch (view.strip().toLowerCase(Locale.ROOT)) {
            case VIEW_ERRORS -> VIEW_ERRORS;
            case VIEW_USERS -> VIEW_USERS;
            case VIEW_PENALTIES -> VIEW_PENALTIES;
            case VIEW_SETTINGS -> VIEW_SETTINGS;
            default -> VIEW_DASHBOARD;
        };
    }

    /** 사용자 화면과 페널티 화면이 같은 {@code status} 이름을 쓰므로 값은 서비스가 각자 해석한다. */
    static int parsePage(String page) {
        if (page == null || page.isBlank()) {
            return 0;
        }
        try {
            long parsed = Long.parseLong(page.strip());
            if (parsed < 0) {
                return 0;
            }
            return parsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parsed;
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
