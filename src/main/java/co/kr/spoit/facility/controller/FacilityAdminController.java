package co.kr.spoit.facility.controller;

import java.util.Collections;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.service.FacilityAccessDeniedException;
import co.kr.spoit.facility.service.FacilityNotFoundException;
import co.kr.spoit.facility.service.FacilityService;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.web.AdminShellModel;

/**
 * 시설 관리자 화면의 담당 시설 조회·등록·수정·삭제 요청을 처리한다.
 *
 * <p>{@code CorePageController}에서 시설 관리 기능을 분리했다. 화면의 나머지 탭 속성은
 * 아직 빈 값으로 초기화하며, 담당 시설 목록만 로그인한 시설 관리자의 ID로 서비스에서 조회한다.</p>
 */
@Controller
public class FacilityAdminController {

    /** 여러 화면이 공유하는 불변 빈 목록이다. */
    private static final List<Object> EMPTY_LIST = Collections.emptyList();

    /**
     * 등록·수정·삭제 후 시설·공간 관리 섹션으로 복귀하는 경로다.
     * 해시 값은 `admin-shell.js`가 화면을 선택하는 데 쓰는 실제 섹션 ID(`facility-assets`)와 같아야 한다.
     */
    private static final String FACILITY_ASSETS_REDIRECT = "redirect:/admin/facility#facility-assets";

    private final FacilityService facilityService;

    public FacilityAdminController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    /**
     * 시설 관리 화면을 렌더링한다. 담당 시설은 인증 주체의 회원 ID로만 조회하며,
     * 나머지 탭 속성은 아직 빈 값으로 초기화한다. 기본 보기는 dashboard이다.
     */
    @GetMapping("/admin/facility")
    public String adminFacility(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "error", required = false) String error,
            Model model) {
        AdminShellModel.apply(model);
        // 변경 요청이 쿼리 파라미터로 전달한 결과 안내를 화면에서 렌더링할 수 있게 모델에 옮긴다.
        // 값이 비어 있으면 빈 안내 상자가 남지 않도록 아예 전달하지 않는다.
        if (message != null && !message.isBlank()) {
            model.addAttribute("message", message);
        }
        if (error != null && !error.isBlank()) {
            model.addAttribute("error", error);
        }
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

    /** 시설을 등록하고 시설·공간 관리 섹션으로 되돌아간다. 오류는 화면 안내로 전달한다. */
    @PostMapping("/admin/facilities")
    public String registerFacility(@AuthenticationPrincipal MemberPrincipal principal,
            FacilityForm form, BindingResult bindingResult, RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            redirect.addAttribute("error", "입력 정보를 확인해 주세요.");
            return FACILITY_ASSETS_REDIRECT;
        }
        try {
            facilityService.registerFacility(principal.getUserId(), form);
            redirect.addAttribute("message", "시설을 등록했습니다.");
        } catch (IllegalArgumentException | FacilityAccessDeniedException | FacilityNotFoundException exception) {
            redirect.addAttribute("error", exception.getMessage());
        }
        return FACILITY_ASSETS_REDIRECT;
    }

    /** 담당 시설을 수정하고 시설 관리 화면의 시설 목록으로 되돌아간다. 위치·지역 값은 유지된다. */
    @PostMapping("/admin/facilities/{facilityId}")
    public String updateFacility(@PathVariable("facilityId") long facilityId,
            @AuthenticationPrincipal MemberPrincipal principal,
            FacilityForm form, BindingResult bindingResult, RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            redirect.addAttribute("error", "입력 정보를 확인해 주세요.");
            return FACILITY_ASSETS_REDIRECT;
        }
        try {
            facilityService.updateFacility(principal.getUserId(), facilityId, form);
            redirect.addAttribute("message", "시설 정보를 수정했습니다.");
        } catch (IllegalArgumentException | FacilityAccessDeniedException | FacilityNotFoundException exception) {
            redirect.addAttribute("error", exception.getMessage());
        }
        return FACILITY_ASSETS_REDIRECT;
    }

    /** 담당 시설을 삭제(논리 삭제)하고 시설 관리 화면의 시설 목록으로 되돌아간다. */
    @PostMapping("/admin/facilities/{facilityId}/delete")
    public String deleteFacility(@PathVariable("facilityId") long facilityId,
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
        return FACILITY_ASSETS_REDIRECT;
    }
}
