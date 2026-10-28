package co.kr.spoit.facility.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.dto.SchedulePage;
import co.kr.spoit.facility.service.FacilityAccessDeniedException;
import co.kr.spoit.facility.service.FacilityNotFoundException;
import co.kr.spoit.facility.service.FacilityScheduleService;
import co.kr.spoit.facility.service.FacilityService;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.web.AdminShellModel;

/**
 * 시설 관리자 화면의 담당 시설 조회·등록·수정·삭제 요청과 예약 운영 일정 화면을 처리한다.
 *
 * <p>화면 탭 속성 중 예약 운영 일정({@code view=schedule})은
 * {@link FacilityScheduleService}가 선택 시설·날짜·탭에 맞춰 구성한다. 나머지 탭은 아직
 * 빈 값이며, 담당 시설 목록만 로그인한 시설 관리자의 ID로 조회한다.</p>
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
    private final FacilityScheduleService scheduleService;

    public FacilityAdminController(FacilityService facilityService, FacilityScheduleService scheduleService) {
        this.facilityService = facilityService;
        this.scheduleService = scheduleService;
    }

    /**
     * 시설 관리 화면을 렌더링한다. 담당 시설은 인증 주체의 회원 ID로만 조회한다.
     *
     * <p>{@code view=schedule}이면 예약 운영 일정 화면의 선택 시설·날짜·종목·탭을 복원하고,
     * 그 밖의 경우 기본 보기는 dashboard이다.</p>
     */
    @GetMapping("/admin/facility")
    public String adminFacility(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "view", required = false) String view,
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "sportId", required = false) Long sportId,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "month", required = false) String month,
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "error", required = false) String error,
            Model model) {
        AdminShellModel.apply(model);
        // 변경 요청이 전달한 결과 안내를 화면에서 렌더링할 수 있게 모델에 옮긴다.
        // 값이 비어 있으면 빈 안내 상자가 남지 않도록 아예 전달하지 않는다.
        if (message != null && !message.isBlank()) {
            model.addAttribute("message", message);
        }
        if (error != null && !error.isBlank()) {
            model.addAttribute("error", error);
        }
        // view는 서버 상태가 아니라 뷰의 기본 선택값이다.
        boolean scheduleView = "schedule".equals(view);
        model.addAttribute("view", scheduleView ? "schedule" : "dashboard");
        model.addAttribute("scopeName", null);
        model.addAttribute("todayLabel", null);
        model.addAttribute("chartCollectedAt", null);
        model.addAttribute("dashboard", null);
        Long userId = principal == null ? null : principal.getUserId();
        model.addAttribute("facilities", facilityService.findManagedFacilities(userId));

        applyScheduleModel(model, scheduleView
                ? scheduleService.findSchedulePage(userId, facilityId, parseDate(date), sportId, tab,
                        parseMonth(month))
                : SchedulePage.empty());

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

    /** 예약 운영 일정 화면에서 시설 선택만 다시 조회한다. AJAX는 일정 카드 조각을, 일반 제출은 기존 GET 화면으로 되돌린다. */
    @PostMapping("/admin/facility/schedule/facility")
    public String selectScheduleFacility(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "sportId", required = false) Long sportId,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestHeader(name = "X-Requested-With", required = false) String requestedWith,
            Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        SchedulePage page = scheduleService.findSchedulePage(principal.getUserId(), facilityId,
                parseDate(date), sportId, tab, null);
        boolean ajax = "fetch".equals(requestedWith) || "XMLHttpRequest".equals(requestedWith);
        if (!ajax) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/facility")
                    .queryParam("view", "schedule");
            if (page.getSelectedFacility() != null) {
                builder.queryParam("facilityId", page.getSelectedFacility().getFacilityId());
            }
            if (date != null && !date.isBlank()) {
                builder.queryParam("date", date.strip());
            }
            if (sportId != null) {
                builder.queryParam("sportId", sportId);
            }
            if (tab != null && !tab.isBlank()) {
                builder.queryParam("tab", tab.strip());
            }
            // 값은 모두 서버가 렌더링한 히든 필드에서 오며 build().encode()로 인코딩된다.
            return "redirect:" + builder.build().encode().toUriString() + "#facility-schedule";
        }
        applyScheduleModel(model, page);
        return "fragments/schedule-card :: schedule-card";
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

    /** 예약 운영 일정 화면 변수를 템플릿 계약 이름 그대로 모델에 옮긴다. */
    private void applyScheduleModel(Model model, SchedulePage page) {
        model.addAttribute("selectedFacility", page.getSelectedFacility());
        model.addAttribute("weeklyHours", page.getWeeklyHours());
        model.addAttribute("hourOptions", page.getHourOptions());
        model.addAttribute("calendar", page.getCalendar());
        model.addAttribute("sports", page.getSports());
        model.addAttribute("conditions", page.getConditions());
        model.addAttribute("closures", page.getClosures());
        // 탭 복귀는 템플릿이 `tab`으로 판단한다. 계약 이름을 그대로 채워 화면이 서버 상태를 쓰게 한다.
        model.addAttribute("tab", page.getTab());
        model.addAttribute("scheduleTab", page.getTab());
        // 충돌 flash는 redirect가 이미 모델에 넣는다. 없을 때만 빈 기본값을 채워
        // 템플릿이 null 목록을 순회하지 않게 한다.
        if (!model.containsAttribute("scheduleConflicts")) {
            model.addAttribute("scheduleConflicts", EMPTY_LIST);
        }
        if (!model.containsAttribute("scheduleConflictRange")) {
            model.addAttribute("scheduleConflictRange", null);
        }
        if (!model.containsAttribute("scheduleConflictMessage")) {
            model.addAttribute("scheduleConflictMessage", null);
        }
    }

    /** 잘못된 날짜 문자열은 선택 없음으로 처리해 화면이 깨지지 않게 한다. */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static YearMonth parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(value.strip());
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
