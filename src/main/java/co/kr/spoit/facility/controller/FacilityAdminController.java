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

import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.dto.FacilityStats;
import co.kr.spoit.facility.dto.SchedulePage;
import co.kr.spoit.facility.service.FacilityAccessDeniedException;
import co.kr.spoit.facility.service.FacilityDeleteBlockedException;
import co.kr.spoit.admin.common.AdminShellModel;
import co.kr.spoit.facility.service.FacilityNotFoundException;
import co.kr.spoit.facility.service.FacilityScheduleService;
import co.kr.spoit.facility.service.FacilityService;
import co.kr.spoit.facility.service.FacilityStatsService;
import co.kr.spoit.global.common.FlashFeedback;
import co.kr.spoit.match.dto.FacilityDashboard;
import co.kr.spoit.match.dto.RoomManagementPage;
import co.kr.spoit.match.service.RoomManagementService;
import co.kr.spoit.member.domain.MemberPrincipal;

/**
 * 시설 관리자 화면의 담당 시설 조회·등록·수정·삭제 요청과 예약 운영 일정·매칭방 목록 화면을 처리한다.
 *
 * <p>화면 탭 속성 중 예약 운영 일정({@code view=schedule})은
 * {@link FacilityScheduleService}가 선택 시설·날짜·탭에 맞춰 구성하고, 매칭방 관리
 * ({@code view=bookings})의 목록·필터·종목 선택지는 {@link RoomManagementService}가 인증 주체의
 * 담당 시설 범위로 구성한다. 나머지 탭은 아직 빈 값이다.</p>
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
    private final RoomManagementService roomManagementService;
    private final FacilityStatsService facilityStatsService;

    public FacilityAdminController(FacilityService facilityService, FacilityScheduleService scheduleService,
            RoomManagementService roomManagementService, FacilityStatsService facilityStatsService) {
        this.facilityService = facilityService;
        this.scheduleService = scheduleService;
        this.roomManagementService = roomManagementService;
        this.facilityStatsService = facilityStatsService;
    }

    /**
     * 시설 관리 화면을 렌더링한다. 담당 시설과 매칭방 목록은 인증 주체의 회원 ID로만 조회한다.
     *
     * <p>{@code view=schedule}이면 예약 운영 일정 화면의 선택 시설·날짜·종목·탭을 복원하고,
     * {@code view=bookings}이면 매칭방 관리 섹션을 기본으로 보여 준다. 그 밖의 경우 기본 보기는
     * dashboard이다.</p>
     *
     * <p>매칭방 목록은 화면 해시로 섹션을 바꾸는 클라이언트 동작 때문에 모든 GET에서 실제 값으로
     * 채운다. 검색어·상태는 매칭방 필터이며, 종목({@code sportId})과 시설({@code facilityId})은
     * 매칭방 화면일 때만 필터로 쓴다. {@code facilityId}는 일정 화면의 시설 선택과 같은 파라미터라
     * 다른 화면에서 그대로 넘기면 숨은 매칭방 목록이 좁아지므로 매칭방 화면에서만 전달한다.</p>
     */
    @GetMapping("/admin/facility")
    public String adminFacility(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "view", required = false) String view,
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "sportId", required = false) Long sportId,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "month", required = false) String month,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "periodFrom", required = false) String periodFrom,
            @RequestParam(name = "periodTo", required = false) String periodTo,
            Model model) {
        AdminShellModel.apply(model);
        // 변경 요청의 결과 안내(message/error/errorTitle)는 flash로 모델에 들어오고,
        // 화면의 fragments/feedback :: flash가 성공 토스트·실패 모달로 보여 준다.
        // view는 서버 상태가 아니라 뷰의 기본 선택값이다.
        boolean scheduleView = "schedule".equals(view);
        boolean bookingsView = "bookings".equals(view);
        boolean statsView = "stats".equals(view);
        model.addAttribute("view", scheduleView ? "schedule"
                : bookingsView ? "bookings"
                : statsView ? "stats" : "dashboard");
        Long userId = principal == null ? null : principal.getUserId();

        // 대시보드는 해시로 섹션을 바꾸는 화면 계약상 모든 view에서 실제 값으로 채운다.
        // 집계는 검색·상태·종목·시설 필터와 무관하게 담당 활성 시설 전체를 대상으로 한다.
        FacilityDashboard dashboard = roomManagementService.findDashboard(userId);
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("scopeName", dashboard.getScopeName());
        model.addAttribute("todayLabel", dashboard.getTodayLabel());
        model.addAttribute("chartCollectedAt", dashboard.getCollectedAt());
        List<FacilityListItem> facilities = facilityService.findManagedFacilities(userId);
        model.addAttribute("facilities", facilities);

        applyScheduleModel(model, scheduleView
                ? scheduleService.findSchedulePage(userId, facilityId, parseDate(date), sportId, tab,
                        parseMonth(month))
                : SchedulePage.empty());

        // 통계 화면도 일정 화면과 같은 시설 선택 모델(selectedFacility)로 드롭다운을 복원한다.
        // applyScheduleModel은 stats 화면에서 빈 일정(SchedulePage.empty())을 넣어 null로 덮으므로,
        // 반드시 그 뒤에 담당 시설 목록에서 찾은 항목으로 덮어쓴다.
        // 담당 범위 밖의 facilityId는 목록에 없으므로 선택 없음으로 남는다.
        if (statsView && facilityId != null) {
            for (FacilityListItem facility : facilities) {
                if (facilityId.equals(facility.getFacilityId())) {
                    model.addAttribute("selectedFacility", facility);
                    break;
                }
            }
        }

        // 매칭방 목록은 담당 시설 범위로만 조회하며, 클라이언트 섹션 전환 때문에 항상 실제 값을 채운다.
        // 종목·시설 필터는 매칭방 화면에서만 적용해 일정 화면의 선택이 숨은 매칭방 목록을 좁히지 않게 한다.
        // facilityId는 일정 화면의 시설 선택과 같은 파라미터이므로, 매칭방 화면이 아니면 반드시 null로 넘긴다.
        RoomManagementPage bookings = roomManagementService.findRooms(userId, q, status,
                bookingsView ? sportId : null, bookingsView ? facilityId : null);
        model.addAttribute("matches", bookings.getMatches());
        model.addAttribute("bookingFilters", bookings.getFilters());
        // 일정 화면의 종목 선택지(sports)를 덮어쓰지 않도록 매칭방 종목은 별도 모델 이름을 쓴다.
        model.addAttribute("bookingSports", bookings.getSports());
        // 생성·수정 모달: 시설별 제공 종목과 1시간 블록. 운영 시간·한도 판단은 저장 때 서버가 한다.
        model.addAttribute("roomEditorSports", bookings.getFacilitySports());
        model.addAttribute("roomEditorHourSlots", bookings.getHourSlots());
        // 통계는 화면 계약상 view=stats일 때만 조회한다. 다른 view는 사이드바 해시 전환이므로
        // 빈 값으로 남겨 불필요한 집계 쿼리를 만들지 않는다.
        // 시설 미선택·미담당 시설의 빈 통계 판정은 서비스 책임이다.
        FacilityStats stats = statsView
                ? facilityStatsService.load(userId, facilityId, period, periodFrom, periodTo)
                : null;
        model.addAttribute("stats", stats);
        model.addAttribute("notices", EMPTY_LIST);
        model.addAttribute("rules", EMPTY_LIST);
        return "admin/facility";
    }

    /**
     * 예약 통계 조회 조건을 POST로 받아 통계 화면으로 되돌린다.
     *
     * <p>저장소의 다른 관리자 변경 요청과 같은 Post/Redirect/Get 관례를 따른다. 조회 결과는
     * {@code view=stats} GET이 그리므로 새로고침·뒤로가기가 폼 재전송 경고를 만들지 않고,
     * 통계 화면 GET 경로도 한 곳에서만 모델을 조립한다.</p>
     */
    @PostMapping("/admin/facility/stats")
    public String searchStats(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "period", required = false) String period,
            @RequestParam(name = "periodFrom", required = false) String periodFrom,
            @RequestParam(name = "periodTo", required = false) String periodTo) {
        if (principal == null) {
            return "redirect:/login";
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/facility")
                .queryParam("view", "stats");
        if (facilityId != null && facilityId > 0) {
            builder.queryParam("facilityId", facilityId);
        }
        if (period != null && !period.isBlank()) {
            builder.queryParam("period", period.strip());
        }
        if (periodFrom != null && !periodFrom.isBlank()) {
            builder.queryParam("periodFrom", periodFrom.strip());
        }
        if (periodTo != null && !periodTo.isBlank()) {
            builder.queryParam("periodTo", periodTo.strip());
        }
        // 값은 서버가 렌더링한 폼 필드에서 오며 build().encode()로 인코딩된다.
        return "redirect:" + builder.build().encode().toUriString() + "#facility-stats";
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

    static final String REGISTER_ERROR_TITLE = "시설을 등록할 수 없습니다";
    static final String UPDATE_ERROR_TITLE = "시설 정보를 수정할 수 없습니다";
    static final String DELETE_ERROR_TITLE = "시설을 삭제할 수 없습니다";

    /** 시설을 등록하고 시설·공간 관리 섹션으로 되돌아간다. 오류는 실패 안내 모달로 전달한다. */
    @PostMapping("/admin/facilities")
    public String registerFacility(@AuthenticationPrincipal MemberPrincipal principal,
            FacilityForm form, BindingResult bindingResult, RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            FlashFeedback.failure(redirect, REGISTER_ERROR_TITLE, "입력 정보를 확인해 주세요.");
            return FACILITY_ASSETS_REDIRECT;
        }
        try {
            facilityService.registerFacility(principal.getUserId(), form);
            FlashFeedback.success(redirect, "시설을 등록했습니다.");
        } catch (IllegalArgumentException | FacilityAccessDeniedException | FacilityNotFoundException exception) {
            FlashFeedback.failure(redirect, REGISTER_ERROR_TITLE, exception.getMessage());
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
            FlashFeedback.failure(redirect, UPDATE_ERROR_TITLE, "입력 정보를 확인해 주세요.");
            return FACILITY_ASSETS_REDIRECT;
        }
        try {
            facilityService.updateFacility(principal.getUserId(), facilityId, form);
            FlashFeedback.success(redirect, "시설 정보를 수정했습니다.");
        } catch (IllegalArgumentException | FacilityAccessDeniedException | FacilityNotFoundException exception) {
            FlashFeedback.failure(redirect, UPDATE_ERROR_TITLE, exception.getMessage());
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
            FlashFeedback.success(redirect, "시설을 삭제했습니다.");
        } catch (FacilityDeleteBlockedException exception) {
            // 삭제를 막는 매칭방 전체를 경고창에 넘겨 어떤 방을 정리해야 하는지 보여 준다.
            // 안내 문구는 모달 안에만 표시하도록 별도 속성으로 전달한다.
            redirect.addFlashAttribute("deleteBlockMessage", exception.getMessage());
            redirect.addFlashAttribute("deleteBlockConflicts", exception.getConflicts());
            redirect.addFlashAttribute("deleteBlockSummary", summarize(exception.getConflicts()));
        } catch (IllegalArgumentException | FacilityAccessDeniedException | FacilityNotFoundException exception) {
            FlashFeedback.failure(redirect, DELETE_ERROR_TITLE, exception.getMessage());
        }
        return FACILITY_ASSETS_REDIRECT;
    }

    /** 경고 요약은 일정 충돌 경고와 같은 형식(제목, 없으면 "매칭방 N")을 쓴다. */
    private static String summarize(List<MatchPeriod> conflicts) {
        StringBuilder summary = new StringBuilder();
        for (MatchPeriod conflict : conflicts) {
            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(conflict.getTitle() == null ? "매칭방 " + conflict.getMatchId() : conflict.getTitle());
        }
        return summary.toString();
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
