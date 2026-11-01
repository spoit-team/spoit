package co.kr.spoit.facility.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.dto.ClosureForm;
import co.kr.spoit.facility.dto.ConditionsForm;
import co.kr.spoit.facility.dto.DateScheduleForm;
import co.kr.spoit.facility.dto.WeeklyScheduleForm;
import co.kr.spoit.facility.service.FacilityAccessDeniedException;
import co.kr.spoit.facility.service.FacilityNotFoundException;
import co.kr.spoit.facility.service.FacilityScheduleService;
import co.kr.spoit.facility.service.ScheduleConflictException;
import co.kr.spoit.member.domain.MemberPrincipal;

/**
 * 예약 운영 일정 저장 요청을 처리한다.
 *
 * <p>조회 화면은 {@link FacilityAdminController}가 담당하고, 이 컨트롤러는 폼 제출만 받는다.
 * 모든 요청은 인증 주체의 ID로 서비스에 담당자 확인을 맡기며, 성공·실패 모두
 * {@code #facility-schedule} 섹션으로 되돌아간다. 폼 필드 이름은 UI 팀과 합의한 계약을 따른다.</p>
 */
@Controller
public class FacilityScheduleController {

    /** 설정 변경 후 복귀할 섹션이다. {@code admin-shell.js}가 화면을 선택하는 실제 ID다. */
    private static final String SCHEDULE_PATH = "/admin/facility";

    /** 화면이 실제로 여는 탭 ID다. 그 밖의 값은 복귀 URL에 싣지 않는다. */
    private static final List<String> ALLOWED_TABS = List.of(
            "schedule-hours", "schedule-conditions", "schedule-changes");

    private final FacilityScheduleService scheduleService;

    public FacilityScheduleController(FacilityScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /** 요일별 운영 시간을 저장한다. 7일 전체 휴무도 허용한다. */
    @PostMapping("/admin/facility/schedule/weekly")
    public String saveWeekly(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "openDays", required = false) List<Integer> openDays,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam Map<String, String> params,
            RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        WeeklyScheduleForm form = weeklyForm(openDays, params);
        form.setFacilityId(facilityId);
        return write(principal, facilityId, tab, date, null, redirect,
                () -> scheduleService.saveWeeklyHours(principal.getUserId(), form),
                "운영 시간을 저장했습니다.");
    }

    /** 날짜별 운영 시간을 저장한다. 선택 시간 0건은 그 날짜 휴무다. */
    @PostMapping("/admin/facility/schedule/date")
    public String saveDate(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "hours", required = false) List<String> hours,
            @RequestParam(name = "note", required = false) String note,
            @RequestParam(name = "tab", required = false) String tab,
            RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        DateScheduleForm form = new DateScheduleForm();
        form.setFacilityId(facilityId);
        form.setDate(date);
        form.setHours(hours == null ? List.of() : hours);
        form.setNote(note);
        return write(principal, facilityId, "schedule-hours", date, null, redirect,
                () -> scheduleService.saveDateHours(principal.getUserId(), form),
                "날짜별 운영 시간을 저장했습니다.");
    }

    /** 날짜별 설정을 지워 주간 기준으로 되돌린다. */
    @PostMapping("/admin/facility/schedule/date/reset")
    public String resetDate(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "date", required = false) String date,
            RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        return write(principal, facilityId, "schedule-hours", date, null, redirect,
                () -> scheduleService.resetDateHours(principal.getUserId(), facilityId, parseDate(date)),
                "날짜별 설정을 지우고 주간 운영 시간을 적용했습니다.");
    }

    /** 종목별 모집 조건을 저장한다. */
    @PostMapping("/admin/facility/schedule/conditions")
    public String saveConditions(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "sportId", required = false) Long sportId,
            @RequestParam(name = "capacity", required = false) Integer capacity,
            @RequestParam(name = "note", required = false) String note,
            @RequestParam(name = "date", required = false) String date,
            RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        ConditionsForm form = new ConditionsForm();
        form.setFacilityId(facilityId);
        form.setSportId(sportId);
        form.setCapacity(capacity);
        form.setNote(note);
        return write(principal, facilityId, "schedule-conditions", date, sportId, redirect,
                () -> scheduleService.saveConditions(principal.getUserId(), form),
                "모집 조건을 저장했습니다.");
    }

    /** 휴관을 등록한다. 활성 매칭방과 겹치면 저장하지 않고 경고를 전달한다. */
    @PostMapping("/admin/facility/schedule/closures")
    public String registerClosure(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "start", required = false) String start,
            @RequestParam(name = "end", required = false) String end,
            @RequestParam(name = "reason", required = false) String reason,
            RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        ClosureForm form = new ClosureForm();
        form.setFacilityId(facilityId);
        form.setDate(date);
        form.setStart(start);
        form.setEnd(end);
        form.setReason(reason);
        return write(principal, facilityId, "schedule-changes", date, null, redirect,
                () -> scheduleService.registerClosure(principal.getUserId(), form),
                "휴관 일정을 등록했습니다.");
    }

    /** 등록한 휴관을 취소한다. 담당 시설·대상 여부는 서비스가 다시 확인한다. */
    @PostMapping("/admin/facility/schedule/closures/{scheduleChangeId}/cancel")
    public String cancelClosure(@PathVariable("scheduleChangeId") long scheduleChangeId,
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "date", required = false) String date,
            RedirectAttributes redirect) {
        if (principal == null) {
            return "redirect:/login";
        }
        return write(principal, facilityId, "schedule-changes", date, null, redirect,
                () -> scheduleService.cancelClosure(principal.getUserId(),
                        facilityId == null ? 0L : facilityId, scheduleChangeId),
                "휴관 일정을 취소했습니다.");
    }

    private WeeklyScheduleForm weeklyForm(List<Integer> openDays, Map<String, String> params) {
        WeeklyScheduleForm form = new WeeklyScheduleForm();
        form.setOpenDays(openDays == null ? List.of() : openDays);
        Map<Integer, String> starts = new HashMap<>();
        Map<Integer, String> ends = new HashMap<>();
        for (int day = 1; day <= 7; day++) {
            starts.put(day, params.get("start-" + day));
            ends.put(day, params.get("end-" + day));
        }
        form.setStartTimes(starts);
        form.setEndTimes(ends);
        return form;
    }

    /**
     * 변경 성공·실패 모두 일정 섹션으로 되돌린다.
     *
     * <p>충돌 시에는 겹치는 매칭방 <b>전체</b>와 입력 구간을 flash로 넘겨, 경고창이 저장 0건과
     * 함께 어떤 방이 문제인지 보여 줄 수 있게 한다. 기존 매칭방·신청은 자동 취소하지 않는다.</p>
     */
    private String write(MemberPrincipal principal, Long facilityId, String tab, String date, Long sportId,
            RedirectAttributes redirect, Runnable action, String successMessage) {
        try {
            action.run();
            redirect.addFlashAttribute("message", successMessage);
        } catch (ScheduleConflictException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            redirect.addFlashAttribute("scheduleConflictRange", exception.getRangeText());
            redirect.addFlashAttribute("scheduleConflicts", exception.getConflicts());
            redirect.addFlashAttribute("scheduleConflictSummary", summarize(exception.getConflicts()));
        } catch (IllegalArgumentException | FacilityAccessDeniedException | FacilityNotFoundException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return scheduleRedirect(facilityId, tab, date, sportId);
    }

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

    private String scheduleRedirect(Long facilityId, String tab, String date, Long sportId) {
        // 사용자 입력(date·tab)을 원문으로 붙이면 '&'나 '#'로 새 파라미터·fragment를 만들 수 있다.
        // 날짜는 ISO 형식만, 탭은 알려진 값만 통과시키고 나머지는 UriComponentsBuilder로 인코딩한다.
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(SCHEDULE_PATH)
                .queryParam("view", "schedule");
        if (facilityId != null) {
            builder.queryParam("facilityId", facilityId);
        }
        String safeDate = safeDate(date);
        if (safeDate != null) {
            builder.queryParam("date", safeDate);
        }
        if (sportId != null) {
            builder.queryParam("sportId", sportId);
        }
        String safeTab = safeTab(tab);
        if (safeTab != null) {
            builder.queryParam("tab", safeTab);
        }
        return "redirect:" + builder.build().encode().toUriString() + "#facility-schedule";
    }

    /** 복귀 날짜로 쓸 수 있는 값만 남긴다. 형식이 다르면 선택을 복원하지 않는다. */
    private static String safeDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip()).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 화면이 실제로 여는 탭 ID만 허용한다. 그 밖의 값은 기본 탭으로 돌린다. */
    private static String safeTab(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        return ALLOWED_TABS.contains(normalized) ? normalized : null;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("날짜를 선택해 주세요.");
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. 예: 2026-11-02", exception);
        }
    }
}
