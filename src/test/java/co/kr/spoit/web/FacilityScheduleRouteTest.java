package co.kr.spoit.web;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.facility.controller.FacilityAdminController;
import co.kr.spoit.facility.controller.FacilityScheduleController;
import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.dto.CalendarView;
import co.kr.spoit.facility.dto.ConditionsView;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.dto.SchedulePage;
import co.kr.spoit.facility.dto.WeeklyHourRow;
import co.kr.spoit.facility.service.FacilityScheduleService;
import co.kr.spoit.facility.service.FacilityService;
import co.kr.spoit.facility.service.ScheduleConflictException;
import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 예약 운영 일정 화면·저장 라우트가 서비스 계약으로 연결되는지 검증한다. */
@WebMvcTest(controllers = {FacilityAdminController.class, FacilityScheduleController.class})
@Import(SecurityConfig.class)
class FacilityScheduleRouteTest {

    private static final String SCHEDULE_REDIRECT = "/admin/facility?view=schedule&facilityId=10&tab=";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityService facilityService;

    @MockitoBean
    private FacilityScheduleService scheduleService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("view=schedule 요청은 선택 시설·날짜·탭을 일정 화면 모델로 채운다")
    void rendersScheduleViewWithSelectedFacilityAndTab() throws Exception {
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of(facility()));
        when(scheduleService.findSchedulePage(eq(7L), eq(10L), eq(LocalDate.of(2026, 11, 3)),
                eq(3L), eq("schedule-changes"), any()))
                .thenReturn(page());

        mockMvc.perform(get("/admin/facility")
                        .param("view", "schedule")
                        .param("facilityId", "10")
                        .param("date", "2026-11-03")
                        .param("sportId", "3")
                        .param("tab", "schedule-changes")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/facility"))
                .andExpect(model().attribute("view", "schedule"))
                .andExpect(model().attribute("selectedFacility", org.hamcrest.Matchers.hasProperty(
                        "facilityId", org.hamcrest.Matchers.is(10L))))
                .andExpect(model().attribute("tab", "schedule-changes"))
                .andExpect(model().attribute("weeklyHours", org.hamcrest.Matchers.hasSize(7)));
    }

    @Test
    @DisplayName("시설 선택 요청은 AJAX엔 일정 카드 조각을, 일반 제출엔 기존 GET 주소로 되돌린다")
    void selectsScheduleFacility() throws Exception {
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of(facility()));
        when(scheduleService.findSchedulePage(eq(7L), eq(10L), isNull(), isNull(), isNull(), any()))
                .thenReturn(page());

        mockMvc.perform(post("/admin/facility/schedule/facility")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10")
                        .header("X-Requested-With", "fetch"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/schedule-card :: schedule-card"))
                .andExpect(model().attribute("selectedFacility", org.hamcrest.Matchers.hasProperty(
                        "facilityId", org.hamcrest.Matchers.is(10L))));

        mockMvc.perform(post("/admin/facility/schedule/facility")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/facility?view=schedule&facilityId=10#facility-schedule"));
    }

    @Test
    @DisplayName("일반 회원은 예약 운영 일정 화면에 접근할 수 없다")
    void memberCannotReadSchedule() throws Exception {
        mockMvc.perform(get("/admin/facility").param("view", "schedule").with(user(member())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("요일별 저장은 주간 폼 계약을 서비스로 넘기고 일정 섹션으로 복귀한다")
    void savesWeeklyHoursAndReturnsToSchedule() throws Exception {
        mockMvc.perform(post("/admin/facility/schedule/weekly")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10")
                        .param("openDays", "1", "2")
                        .param("start-1", "06:00")
                        .param("end-1", "24:00")
                        .param("start-2", "22:00")
                        .param("end-2", "06:00")
                        .param("tab", "schedule-hours"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "운영 시간을 저장했습니다."))
                .andExpect(redirectedUrl(SCHEDULE_REDIRECT + "schedule-hours#facility-schedule"));

        verify(scheduleService).saveWeeklyHours(eq(7L), org.mockito.ArgumentMatchers.argThat(form ->
                form.getFacilityId().equals(10L)
                        && form.getOpenDays().equals(List.of(1, 2))
                        && "06:00".equals(form.getStartTimes().get(1))
                        && "24:00".equals(form.getEndTimes().get(1))
                        && "22:00".equals(form.getStartTimes().get(2))
                        && "06:00".equals(form.getEndTimes().get(2))
                        && form.getStartTimes().get(3) == null));
    }

    @Test
    @DisplayName("7일 전체 휴무 저장은 openDays 없이도 서비스로 전달한다")
    void savesAllWeekClosure() throws Exception {
        mockMvc.perform(post("/admin/facility/schedule/weekly")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10"))
                .andExpect(status().is3xxRedirection());

        verify(scheduleService).saveWeeklyHours(eq(7L), org.mockito.ArgumentMatchers.argThat(form ->
                form.getOpenDays().isEmpty()));
    }

    @Test
    @DisplayName("날짜별 저장은 선택 시간과 안내 문구를 넘긴다")
    void savesDateHours() throws Exception {
        mockMvc.perform(post("/admin/facility/schedule/date")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10")
                        .param("date", "2026-11-03")
                        .param("hours", "08:00", "09:00")
                        .param("note", "오전 개방"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "날짜별 운영 시간을 저장했습니다."));

        verify(scheduleService).saveDateHours(eq(7L), org.mockito.ArgumentMatchers.argThat(form ->
                "2026-11-03".equals(form.getDate())
                        && form.getHours().equals(List.of("08:00", "09:00"))
                        && "오전 개방".equals(form.getNote())));
    }

    @Test
    @DisplayName("날짜별 설정 초기화는 시설·날짜를 서비스로 넘기고 날짜를 유지해 복귀한다")
    void resetsDateHours() throws Exception {
        mockMvc.perform(post("/admin/facility/schedule/date/reset")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10")
                        .param("date", "2026-11-03"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "날짜별 설정을 지우고 주간 운영 시간을 적용했습니다."))
                .andExpect(redirectedUrl(
                        "/admin/facility?view=schedule&facilityId=10&date=2026-11-03"
                                + "&tab=schedule-hours#facility-schedule"));

        verify(scheduleService).resetDateHours(7L, 10L, LocalDate.of(2026, 11, 3));
    }

    @Test
    @DisplayName("담당하지 않는 시설의 일정 저장은 서비스 거부를 오류 안내로 보여 준다")
    void reportsAccessDenied() throws Exception {
        org.mockito.Mockito.doThrow(new co.kr.spoit.facility.service.FacilityAccessDeniedException(
                        "담당 시설만 관리할 수 있습니다."))
                .when(scheduleService).saveWeeklyHours(eq(7L), any());

        mockMvc.perform(post("/admin/facility/schedule/weekly")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "11"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "담당 시설만 관리할 수 있습니다."))
                .andExpect(redirectedUrl("/admin/facility?view=schedule&facilityId=11#facility-schedule"));
    }

    @Test
    @DisplayName("휴관 충돌은 저장 0건을 알리고 겹치는 방 전체를 flash로 넘긴다")
    void reportsAllConflictingMatches() throws Exception {
        List<MatchPeriod> conflicts = List.of(match(500L, "첫 방"), match(501L, "둘째 방"));
        org.mockito.Mockito.doThrow(new ScheduleConflictException(
                        "입력한 휴관 기간과 겹치는 모집 중·매칭 완료 매칭방이 있어 휴관을 등록하지 않았습니다.",
                        conflicts, "휴관 2026년 11월 3일 (화) 10:00 ~ 12:00"))
                .when(scheduleService).registerClosure(eq(7L), any());

        mockMvc.perform(post("/admin/facility/schedule/closures")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10")
                        .param("date", "2026-11-03")
                        .param("start", "10:00")
                        .param("end", "12:00")
                        .param("reason", "시설 점검"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("scheduleConflicts", conflicts))
                .andExpect(flash().attribute("scheduleConflictRange", "휴관 2026년 11월 3일 (화) 10:00 ~ 12:00"))
                .andExpect(flash().attribute("error",
                        "입력한 휴관 기간과 겹치는 모집 중·매칭 완료 매칭방이 있어 휴관을 등록하지 않았습니다."))
                .andExpect(redirectedUrl(
                        "/admin/facility?view=schedule&facilityId=10&date=2026-11-03&tab=schedule-changes"
                                + "#facility-schedule"));

        verify(scheduleService).registerClosure(eq(7L), org.mockito.ArgumentMatchers.argThat(form ->
                form.getFacilityId().equals(10L)
                        && "2026-11-03".equals(form.getDate())
                        && "10:00".equals(form.getStart())
                        && "12:00".equals(form.getEnd())
                        && "시설 점검".equals(form.getReason())));
    }

    @Test
    @DisplayName("서비스가 입력 오류를 던지면 그 한국어 안내를 오류 flash로 보여 준다")
    void reportsValidationMessage() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalArgumentException("최대 참가 인원은 2~200명 사이로 입력해 주세요."))
                .when(scheduleService).saveConditions(eq(7L), any());

        mockMvc.perform(post("/admin/facility/schedule/conditions")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10")
                        .param("sportId", "3")
                        .param("capacity", "1")
                        .param("deadline", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "최대 참가 인원은 2~200명 사이로 입력해 주세요."));

        verify(scheduleService).saveConditions(eq(7L), org.mockito.ArgumentMatchers.argThat(form ->
                form.getFacilityId().equals(10L)
                        && form.getSportId().equals(3L)
                        && form.getCapacity() == 1
                        && form.getDeadline() == 1));
    }

    @Test
    @DisplayName("복귀 URL의 date·tab에 '&'나 '#'가 섞여도 새 파라미터·fragment를 만들지 않는다")
    void encodesInjectedDateAndTabInRedirect() throws Exception {
        mockMvc.perform(post("/admin/facility/schedule/weekly")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10")
                        .param("date", "2026-11-03&view=evil")
                        .param("tab", "schedule-hours#injected")
                        .param("openDays", "1")
                        .param("start-1", "06:00")
                        .param("end-1", "22:00"))
                .andExpect(status().is3xxRedirection())
                // 주입된 값은 검증에서 걸러져 date·tab 파라미터가 생기지 않고, fragment도 하나뿐이다.
                .andExpect(redirectedUrl("/admin/facility?view=schedule&facilityId=10#facility-schedule"));
    }

    @Test
    @DisplayName("정상 날짜는 유지하고 알 수 없는 tab만 복귀 URL에서 제외한다")
    void dropsUnknownTabButKeepsValidDate() throws Exception {
        mockMvc.perform(post("/admin/facility/schedule/weekly")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10")
                        .param("date", "2026-11-03")
                        .param("tab", "javascript:alert(1)")
                        .param("openDays", "1")
                        .param("start-1", "06:00")
                        .param("end-1", "22:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/facility?view=schedule&facilityId=10&date=2026-11-03#facility-schedule"));
    }

    @Test
    @DisplayName("휴관 취소는 경로 변수와 시설 ID를 서비스로 넘긴다")
    void cancelsClosure() throws Exception {
        mockMvc.perform(post("/admin/facility/schedule/closures/300/cancel")
                        .with(user(facilityManager())).with(csrf())
                        .param("facilityId", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "휴관 일정을 취소했습니다."));

        verify(scheduleService).cancelClosure(7L, 10L, 300L);
    }

    @Test
    @DisplayName("일반 회원의 일정 저장 요청은 서비스를 호출하지 않는다")
    void memberCannotWriteSchedule() throws Exception {
        mockMvc.perform(post("/admin/facility/schedule/weekly")
                        .with(user(member())).with(csrf())
                        .param("facilityId", "10"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scheduleService);
    }

    @Test
    @DisplayName("일정 저장 요청에는 CSRF 토큰이 필요하다")
    void writeRequiresCsrf() throws Exception {
        mockMvc.perform(post("/admin/facility/schedule/weekly")
                        .with(user(facilityManager()))
                        .param("facilityId", "10"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(scheduleService);
    }

    private static SchedulePage page() {
        return new SchedulePage(
                facility(),
                java.util.stream.IntStream.rangeClosed(1, 7)
                        .mapToObj(day -> WeeklyHourRow.of(day, true, "06:00", "22:00"))
                        .toList(),
                List.of("06:00", "24:00"),
                new CalendarView("2026년 11월", "/prev", "/next", List.of(), "2026-11-03",
                        "2026년 11월 3일 (화)", "주간 운영 시간을 기준으로 표시합니다.", 0, List.of(),
                        "선택된 시간이 없습니다.", null, "예약 가능 0시간"),
                List.of(),
                ConditionsView.empty(),
                List.of(),
                "schedule-changes");
    }

    private static FacilityListItem facility() {
        co.kr.spoit.facility.domain.FacilitySummary summary =
                new co.kr.spoit.facility.domain.FacilitySummary();
        summary.setFacilityId(10L);
        summary.setName("테스트 체육관");
        summary.setAddress("서울특별시 테스트구");
        summary.setConcurrentMatchLimit(3);
        summary.setStatus("운영 중");
        return FacilityListItem.from(summary);
    }

    private static MatchPeriod match(long matchId, String title) {
        MatchPeriod period = new MatchPeriod();
        period.setMatchId(matchId);
        period.setTitle(title);
        period.setStartAt(LocalDateTime.of(2026, 11, 3, 10, 0));
        period.setEndAt(LocalDateTime.of(2026, 11, 3, 11, 0));
        return period;
    }
}
