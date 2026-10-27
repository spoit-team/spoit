package co.kr.spoit.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.service.FacilityAccessDeniedException;
import co.kr.spoit.facility.service.FacilityNotFoundException;
import co.kr.spoit.facility.service.FacilityService;
import co.kr.spoit.member.domain.MemberPrincipal;

/**
 * 주요 화면 템플릿을 렌더링하는 GET 전용 페이지 컨트롤러이다.
 *
 * <p>서비스/DB 연동 전인 화면은 모델 속성을 빈 값·기본값으로 채워 "데이터가 없는 현재 상태"를
 * 표현한다. 시설 관리 화면의 담당 시설 목록은 로그인한 시설 관리자의 ID로 서비스에서 조회한다.</p>
 */
@Controller
public class CorePageController {

    /** 여러 화면이 공유하는 불변 빈 목록이다. */
    private static final List<Object> EMPTY_LIST = Collections.emptyList();

    private final FacilityService facilityService;

    public CorePageController(FacilityService facilityService) {
        this.facilityService = facilityService;
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
     * 관리자 화면: 공통 셸(addAdminShell) 위에 화면별 빈 모델을 얹는다. 관리자 인증은 아직 없다.
     * ------------------------------------------------------------------ */

    /**
     * 시설 관리 화면을 렌더링한다. 담당 시설은 인증 주체의 회원 ID로만 조회하며,
     * 나머지 탭 속성은 아직 빈 값으로 초기화한다. 기본 보기는 dashboard이다.
     */
    @GetMapping("/admin/facility")
    public String adminFacility(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        addAdminShell(model);
        // view는 서버 상태가 아니라 뷰의 기본 선택값이다.
        model.addAttribute("view", "dashboard");
        model.addAttribute("scopeName", null);
        model.addAttribute("todayLabel", null);
        model.addAttribute("chartCollectedAt", null);
        model.addAttribute("dashboard", null);
        model.addAttribute("facilities", facilityService.findManagedFacilities(
                principal == null ? null : principal.getUserId()));
        model.addAttribute("selectedFacility", null);
        model.addAttribute("weeklyHours", EMPTY_LIST);
        model.addAttribute("hourOptions", EMPTY_LIST);
        model.addAttribute("calendar", null);
        model.addAttribute("sports", EMPTY_LIST);
        model.addAttribute("conditions", null);
        model.addAttribute("closures", EMPTY_LIST);
        model.addAttribute("matches", EMPTY_LIST);
        model.addAttribute("bookingFilters", null);
        model.addAttribute("stats", null);
        model.addAttribute("requestMetrics", null);
        model.addAttribute("requestFilters", null);
        model.addAttribute("requests", EMPTY_LIST);
        model.addAttribute("maintenanceMetrics", null);
        model.addAttribute("maintenanceFilters", null);
        model.addAttribute("maintenance", EMPTY_LIST);
        model.addAttribute("notices", EMPTY_LIST);
        model.addAttribute("rules", EMPTY_LIST);
        model.addAttribute("roomEditorHourSlots", EMPTY_LIST);
        return "admin/facility";
    }

    /** 시설을 등록하고 시설 관리 화면으로 되돌아간다. 오류는 화면 안내로 전달한다. */
    @PostMapping("/admin/facilities")
    public String registerFacility(@AuthenticationPrincipal MemberPrincipal principal,
            FacilityForm form, BindingResult bindingResult, RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            redirect.addAttribute("error", "입력 정보를 확인해 주세요.");
            return "redirect:/admin/facility";
        }
        try {
            facilityService.registerFacility(principal.getUserId(), form);
            redirect.addAttribute("message", "시설을 등록했습니다.");
        } catch (IllegalArgumentException | FacilityAccessDeniedException | FacilityNotFoundException exception) {
            redirect.addAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/facility";
    }

    /** 담당 시설을 수정하고 시설 관리 화면으로 되돌아간다. 위치·지역 값은 유지된다. */
    @PostMapping("/admin/facilities/{facilityId}")
    public String updateFacility(@PathVariable long facilityId,
            @AuthenticationPrincipal MemberPrincipal principal,
            FacilityForm form, BindingResult bindingResult, RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            redirect.addAttribute("error", "입력 정보를 확인해 주세요.");
            return "redirect:/admin/facility";
        }
        try {
            facilityService.updateFacility(principal.getUserId(), facilityId, form);
            redirect.addAttribute("message", "시설 정보를 수정했습니다.");
        } catch (IllegalArgumentException | FacilityAccessDeniedException | FacilityNotFoundException exception) {
            redirect.addAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/facility";
    }

    /** 담당 시설을 삭제(논리 삭제)하고 시설 관리 화면으로 되돌아간다. */
    @PostMapping("/admin/facilities/{facilityId}/delete")
    public String deleteFacility(@PathVariable long facilityId,
            @AuthenticationPrincipal MemberPrincipal principal, RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        try {
            facilityService.deleteFacility(principal.getUserId(), facilityId);
            redirect.addAttribute("message", "시설을 삭제했습니다.");
        } catch (IllegalArgumentException | FacilityAccessDeniedException | FacilityNotFoundException exception) {
            redirect.addAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/facility";
    }

    /** 운영 관리 화면을 렌더링한다. 관리자 셸과 match=null만 전달한다. */
    @GetMapping("/admin/operations")
    public String adminOperations(Model model) {
        addAdminShell(model);
        model.addAttribute("match", null);
        return "admin/operations";
    }

    /** 시스템 관리 화면을 렌더링한다. 모니터링 탭 속성을 빈 값으로 초기화하며 수집 전이므로 allHealthy는 false이다. */
    @GetMapping("/admin/system")
    public String adminSystem(Model model) {
        addAdminShell(model);
        model.addAttribute("view", "dashboard");
        model.addAttribute("dashboard", map(
                "collectedAt", null,
                "apiStatus", null,
                "apiUptime", null,
                "cpuPercent", 0,
                "cpuPeak", null,
                "memoryPercent", 0,
                "memoryCapacity", null,
                "diskPercent", 0,
                "recentErrorCount", 0,
                "recentErrorAttention", null,
                // 상태를 확인하지 않았으므로 전체 정상(true)으로 표시하지 않는다.
                "allHealthy", false,
                "bars", EMPTY_LIST,
                "services", EMPTY_LIST,
                "recentErrors", EMPTY_LIST,
                "recentReports", EMPTY_LIST));
        model.addAttribute("errorMetrics", null);
        model.addAttribute("errorFilters", null);
        model.addAttribute("errors", EMPTY_LIST);
        model.addAttribute("reportMetrics", null);
        model.addAttribute("reportFilters", null);
        model.addAttribute("reports", EMPTY_LIST);
        model.addAttribute("reportPagination", null);
        model.addAttribute("penaltyMetrics", null);
        model.addAttribute("penaltyFilters", null);
        model.addAttribute("penalties", EMPTY_LIST);
        model.addAttribute("userMetrics", null);
        model.addAttribute("userFilters", null);
        model.addAttribute("users", EMPTY_LIST);
        model.addAttribute("userPagination", null);
        model.addAttribute("cmsNotices", EMPTY_LIST);
        model.addAttribute("cmsFaqs", EMPTY_LIST);
        model.addAttribute("cmsTerms", EMPTY_LIST);
        model.addAttribute("settings", null);
        model.addAttribute("auditFilters", null);
        model.addAttribute("auditLogs", EMPTY_LIST);
        model.addAttribute("auditPagination", null);
        return "admin/system";
    }

    /** 관리자 화면 공통 셸 속성을 구성한다. 관리자 인증이 없어 adminUser는 null이고 알림도 비어 있다. */
    private void addAdminShell(Model model) {
        model.addAttribute("adminUser", null);
        model.addAttribute("adminNotifications", EMPTY_LIST);
        model.addAttribute("unreadAdminNotificationCount", 0);
        model.addAttribute("adminNotificationSummary", "");
    }

    /**
     * 키/값을 번갈아 받아 순서를 보존한 맵을 만든다.
     *
     * <p>인자는 반드시 쌍으로 전달해야 하며, 홀수이면 {@link ArrayIndexOutOfBoundsException},
     * 키가 {@link String}이 아니면 {@link ClassCastException}이 발생한다.</p>
     */
    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], entries[i + 1]);
        }
        return values;
    }
}
