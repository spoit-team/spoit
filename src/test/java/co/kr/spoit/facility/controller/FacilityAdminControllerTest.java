package co.kr.spoit.facility.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.facility.domain.FacilitySummary;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.dto.FacilityStats;
import co.kr.spoit.facility.service.FacilityScheduleService;
import co.kr.spoit.facility.service.FacilityService;
import co.kr.spoit.facility.service.FacilityStatsService;
import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.match.dto.FacilityDashboard;
import co.kr.spoit.match.dto.RoomManagementPage;
import co.kr.spoit.match.service.RoomManagementService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/**
 * 예약 통계 탭({@code view=stats})의 컨트롤러 계약을 검증한다.
 *
 * <p>통계 조회는 stats 화면에서만 일어나야 하고, 관리자 식별자는 인증 주체에서만 취하며,
 * 기간 파라미터는 서비스로 그대로 전달된다. 빈 통계 판정은 서비스 책임이므로 시설 미선택도
 * 서비스에 위임되는지만 확인한다.</p>
 */
@WebMvcTest(controllers = FacilityAdminController.class)
@Import(SecurityConfig.class)
class FacilityAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityService facilityService;

    @MockitoBean
    private FacilityScheduleService scheduleService;

    @MockitoBean
    private RoomManagementService roomManagementService;

    @MockitoBean
    private FacilityStatsService facilityStatsService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @BeforeEach
    void stubDefaults() {
        when(roomManagementService.findDashboard(any()))
                .thenReturn(FacilityDashboard.empty("2026-11-06(금)", "2026-11-06 14:05"));
        when(roomManagementService.findRooms(any(), any(), any(), any(), any()))
                .thenReturn(RoomManagementPage.empty());
        when(facilityService.findManagedFacilities(any())).thenReturn(List.of());
        when(scheduleService.findSchedulePage(any(), any(), any(), any(), any(), any()))
                .thenReturn(co.kr.spoit.facility.dto.SchedulePage.empty());
    }

    @Test
    @DisplayName("view=stats이면 인증 주체 ID로 통계를 조회해 stats 모델을 채운다")
    void loadsStatsForStatsView() throws Exception {
        FacilityStats stats = emptyStats(30, "최근 30일");
        when(facilityStatsService.load(7L, 10L, null, null, null)).thenReturn(stats);

        mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .param("facilityId", "10")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/facility"))
                .andExpect(model().attribute("view", "stats"))
                .andExpect(model().attribute("stats", stats));

        verify(facilityStatsService).load(7L, 10L, null, null, null);
    }

    @Test
    @DisplayName("view=dashboard 등 다른 view에서는 통계 서비스를 호출하지 않는다")
    void doesNotLoadStatsForOtherViews() throws Exception {
        mockMvc.perform(get("/admin/facility")
                        .param("view", "dashboard")
                        .param("facilityId", "10")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "dashboard"))
                .andExpect(model().attribute("stats", org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/admin/facility").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "dashboard"));

        verify(facilityStatsService, never()).load(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("period·periodFrom·periodTo를 서비스로 그대로 전달한다")
    void passesPeriodParametersToService() throws Exception {
        when(facilityStatsService.load(7L, 10L, "custom", "2026-10-01", "2026-10-31"))
                .thenReturn(emptyStats(31, "최근 31일"));

        mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .param("facilityId", "10")
                        .param("period", "custom")
                        .param("periodFrom", "2026-10-01")
                        .param("periodTo", "2026-10-31")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stats",
                        org.hamcrest.Matchers.hasProperty("periodLabel",
                                org.hamcrest.Matchers.is("최근 31일"))));

        verify(facilityStatsService).load(7L, 10L, "custom", "2026-10-01", "2026-10-31");
    }

    @Test
    @DisplayName("기간 파라미터가 없으면 null로 넘기고 정규화는 서비스가 맡는다")
    void passesNullPeriodWhenAbsent() throws Exception {
        when(facilityStatsService.load(eq(7L), eq(10L), isNull(), isNull(), isNull()))
                .thenReturn(emptyStats(30, "최근 30일"));

        mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .param("facilityId", "10")
                        .with(user(facilityManager())))
                .andExpect(status().isOk());

        verify(facilityStatsService).load(7L, 10L, null, null, null);
    }

    @Test
    @DisplayName("시설 미선택이어도 통계 서비스를 호출하고 빈 통계 판정은 서비스가 한다")
    void delegatesEmptyStatsWhenFacilityNotSelected() throws Exception {
        FacilityStats empty = emptyStats(30, "최근 30일");
        when(facilityStatsService.load(eq(7L), isNull(), any(), any(), any())).thenReturn(empty);

        mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("stats", empty));

        verify(facilityStatsService).load(eq(7L), isNull(), any(), any(), any());
    }

    @Test
    @DisplayName("시설 관리 권한이 없는 회원은 통계 서비스를 호출하지 못한다")
    void memberWithoutFacilityRoleCannotLoadStats() throws Exception {
        mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .param("facilityId", "10")
                        .with(user(member())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(facilityStatsService);
    }

    @Test
    @DisplayName("클라이언트가 넘긴 managerUserId는 무시하고 인증 주체 ID만 쓴다")
    void ignoresClientManagerUserId() throws Exception {
        when(facilityStatsService.load(eq(7L), eq(10L), any(), any(), any()))
                .thenReturn(emptyStats(30, "최근 30일"));

        mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .param("facilityId", "10")
                        .param("managerUserId", "999")
                        .with(user(facilityManager())))
                .andExpect(status().isOk());

        verify(facilityStatsService).load(eq(7L), eq(10L), any(), any(), any());
        verify(facilityStatsService, never()).load(eq(999L), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("view=stats이면 담당 시설 목록에서 고른 시설을 selectedFacility로 복원한다")
    void restoresSelectedFacilityOnStatsView() throws Exception {
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of(
                facility(10L, "송파 풋살파크"),
                facility(11L, "마포 체육관")));
        when(facilityStatsService.load(eq(7L), eq(10L), any(), any(), any()))
                .thenReturn(emptyStats(30, "최근 30일"));

        mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .param("facilityId", "10")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                // 일정 화면의 빈 SchedulePage가 넣은 null을 덮어써야 드롭다운이 복원된다.
                .andExpect(model().attribute("selectedFacility",
                        org.hamcrest.Matchers.hasProperty("facilityId",
                                org.hamcrest.Matchers.is(10L))));
    }

    @Test
    @DisplayName("시설을 고르지 않은 통계 화면과 담당 범위 밖 시설은 selectedFacility가 null이다")
    void leavesSelectedFacilityNullWhenNotSelectable() throws Exception {
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of(facility(10L, "송파 풋살파크")));
        when(facilityStatsService.load(eq(7L), isNull(), any(), any(), any()))
                .thenReturn(emptyStats(30, "최근 30일"));
        when(facilityStatsService.load(eq(7L), eq(99L), any(), any(), any()))
                .thenReturn(emptyStats(30, "최근 30일"));

        mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedFacility", org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .param("facilityId", "99")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedFacility", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("통계 조회 POST는 조회 조건을 보존한 view=stats GET으로 되돌린다")
    void postsStatsSearchAsRedirectToStatsView() throws Exception {
        mockMvc.perform(post("/admin/facility/stats")
                        .param("facilityId", "10")
                        .param("period", "custom")
                        .param("periodFrom", "2026-10-01")
                        .param("periodTo", "2026-10-31")
                        .with(csrf())
                        .with(user(facilityManager())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/facility?view=stats&facilityId=10"
                        + "&period=custom&periodFrom=2026-10-01&periodTo=2026-10-31#facility-stats"));

        // 조회는 POST에서 하지 않는다. 결과 렌더링은 GET 한 곳에서만 일어난다.
        verifyNoInteractions(facilityStatsService);
    }

    @Test
    @DisplayName("시설 미선택 통계 조회 POST도 view=stats로 돌아가 시설 파라미터를 남기지 않는다")
    void postsStatsSearchWithoutFacility() throws Exception {
        mockMvc.perform(post("/admin/facility/stats")
                        .param("facilityId", "")
                        .param("period", "7")
                        .with(csrf())
                        .with(user(facilityManager())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/facility?view=stats&period=7#facility-stats"));
    }

    @Test
    @DisplayName("통계 조회 POST는 시설 관리 권한이 없으면 CSRF를 통과해도 거부된다")
    void memberWithoutFacilityRoleCannotPostStatsSearch() throws Exception {
        mockMvc.perform(post("/admin/facility/stats")
                        .param("facilityId", "10")
                        .with(csrf())
                        .with(user(member())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(facilityStatsService);
    }

    @Test
    @DisplayName("시설 선택 시 상세 집계 7×24 표와 선택 요일·시간대 인원·비중을 실제 렌더한다")
    void rendersStatsDetailsFromFacilityStatsModel() throws Exception {
        when(facilityService.findManagedFacilities(7L))
                .thenReturn(List.of(facility(10L, "송파 풋살파크")));
        when(facilityStatsService.load(eq(7L), eq(10L), any(), any(), any()))
                .thenReturn(detailStats());

        String html = mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .param("facilityId", "10")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/facility"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // 선택 시설이 통계 범위 문구에 그대로 연결된다.
        assertThat(html).contains("송파 풋살파크").doesNotContain("시설 미선택");

        // 지표 카드의 확정 참가 인원도 건수가 아닌 명 단위로, 출석·노쇼 미구분 기준과 함께 렌더한다.
        assertThat(html).contains("확정 참가 인원")
                .contains("<span>30</span><small>명</small>")
                .contains("경기별 확정 인원 합 · 출석·노쇼 미구분");

        // 상세 패널 제목과 안내도 인원(명)과 전체 확정 참가 인원 대비 비중 기준을 쓴다.
        assertThat(html).contains("요일별 확정 참가 인원")
                .contains("시간대별 확정 참가 인원")
                .contains("00시부터 23시까지, 0명인 시간대도 표시합니다")
                .contains("전체 확정 참가 인원 대비 비중");

        // 요일 상세: 7행(월~일)과 0명 행의 is-zero, 선택 요일의 인원·전체 참가자 비중.
        String dayList = section(html, "class=\"stats-day-list\"", "</ul>");
        assertThat(countOf(dayList, "<li class=\"stats-day-row")).isEqualTo(7);
        assertThat(countOf(dayList, "is-zero")).isEqualTo(4);
        assertThat(dayList).contains("<span class=\"stats-row-label\">월</span>")
                .contains("<b class=\"stats-count\">15명</b>")
                .contains("<b class=\"stats-share\">전체 50%</b>")
                .contains("<span class=\"stats-row-label\">화</span>")
                .contains("<b class=\"stats-count\">9명</b>")
                .contains("<b class=\"stats-share\">전체 30%</b>")
                .contains("<span class=\"stats-row-label\">수</span>")
                .contains("<b class=\"stats-count\">6명</b>")
                .contains("<b class=\"stats-share\">전체 20%</b>")
                .contains("<b class=\"stats-count\">0명</b>")
                .doesNotContain("건");

        // 시간대 상세: 00~23시 24행, 선택 시간대의 인원·전체 참가자 비중.
        String hourList = section(html, "class=\"stats-hour-list\"", "</ul>");
        assertThat(countOf(hourList, "<li class=\"stats-hour-row")).isEqualTo(24);
        assertThat(hourList).contains("<span class=\"stats-row-label\">00시</span>")
                .contains("<span class=\"stats-row-label\">23시</span>")
                .contains("<b class=\"stats-count\">18명</b>")
                .contains("<b class=\"stats-share\">전체 60%</b>")
                .contains("<b class=\"stats-count\">8명</b>")
                .contains("<b class=\"stats-share\">전체 27%</b>")
                .doesNotContain("건");

        // 요일×시간 표: 7행 × 24칸, 선택한 요일/시간 칸의 인원과 인원 구간(히트 레벨).
        String heatmap = section(html, "<table class=\"stats-heatmap\"", "</table>");
        assertThat(countOf(heatmap, "<td")).isEqualTo(7 * 24);
        assertThat(countOf(heatmap, "<tr>")).isEqualTo(7 + 1);
        // 색은 혼잡도가 아니라 확정 참가 인원 구간을 뜻한다.
        assertThat(html).contains("색은 인원 구간을 나타냅니다")
                .doesNotContain("혼잡");
        assertThat(heatmap).contains("월요일 09시 시작 종료 매칭의 확정 참가 인원 12명")
                .contains("월요일 14시 시작 종료 매칭의 확정 참가 인원 3명")
                .contains("화요일 09시 시작 종료 매칭의 확정 참가 인원 6명")
                .contains("화요일 18시 시작 종료 매칭의 확정 참가 인원 2명")
                .contains("화요일 20시 시작 종료 매칭의 확정 참가 인원 1명")
                .contains("수요일 18시 시작 종료 매칭의 확정 참가 인원 6명")
                .contains("data-heat-level=\"4\"")
                .contains("data-heat-level=\"3\"")
                .contains("data-heat-level=\"2\"")
                .contains("data-heat-level=\"1\"");
        assertThat(countOf(heatmap, "data-heat-level=")).isEqualTo(6);

        // 범례는 0명·1–2명·3–5명·6–9명·10명 이상 인원 구간을 건수가 아닌 명 기준으로 안내한다.
        String legend = section(html, "class=\"stats-heat-legend\"", "</div>");
        assertThat(legend).contains("0명").contains("1–2명").contains("3–5명")
                .contains("6–9명").contains("10명 이상")
                .doesNotContain("건");

        // 통계 기준 문구: 출석·노쇼 미구분과 시작 시각 기준(이용 중 인원 아님)을 밝힌다.
        String notice = section(html, "<strong>통계 기준</strong>", "</div>");
        assertThat(notice).contains("출석·노쇼는 구분하지 못합니다")
                .contains("여러 경기에 참여하면 경기마다 각각 포함")
                .contains("시작 시각을 기준으로")
                .contains("이용 중인 모든 인원을 센 값이 아닙니다")
                .contains("비중(%)은 같은 조건의 전체 확정 참가 인원 대비 비율")
                .contains("취소된 매칭은 제외");
    }

    @Test
    @DisplayName("시설 미선택이면 상세 집계 표 없이 안전하게 렌더한다")
    void rendersSafelyWhenFacilityNotSelected() throws Exception {
        when(facilityService.findManagedFacilities(7L))
                .thenReturn(List.of(facility(10L, "송파 풋살파크")));
        when(facilityStatsService.load(eq(7L), isNull(), any(), any(), any()))
                .thenReturn(emptyStats(30, "최근 30일"));

        String html = mockMvc.perform(get("/admin/facility")
                        .param("view", "stats")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/facility"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(html).contains("시설 미선택")
                .contains("조회할 시설을 선택하면")
                .contains("요일·시간대별 확정 참가 인원이 표시됩니다")
                .doesNotContain("stats-day-list")
                .doesNotContain("stats-hour-list")
                .doesNotContain("facility-stat-heatmap");
    }

    private static String section(String html, String startMarker, String endMarker) {
        int start = html.indexOf(startMarker);
        assertThat(start).as("시작 마커 %s", startMarker).isNotNegative();
        int end = html.indexOf(endMarker, start);
        assertThat(end).as("종료 마커 %s", endMarker).isNotNegative();
        return html.substring(start, end);
    }

    private static int countOf(String text, String token) {
        int count = 0;
        int index = text.indexOf(token);
        while (index >= 0) {
            count++;
            index = text.indexOf(token, index + token.length());
        }
        return count;
    }

    /**
     * 0건 통계 스텁. 0건 화면 모델(7행·24행·7×24) 조립은 서비스 책임이므로 이 픽스처는
     * 지표만 0으로 둔 전달 모델을 만든다.
     */
    private static FacilityStats emptyStats(int period, String periodLabel) {
        return new FacilityStats(period, periodLabel, 0, 0, null, null, 0, 0, 0, 0, 0);
    }

    /**
     * 월 15명(50%)·화 9명(30%)·수 6명(20%), 09시 18명(60%)·18시 8명(27%),
     * 요일×시간대는 월 09시 12명·월 14시 3명·화 09시 6명·화 18시 2명·화 20시 1명·수 18시 6명인
     * 상세 통계. 확정 참가 인원 합계는 30명이다.
     */
    private static FacilityStats detailStats() {
        List<String> days = List.of("월", "화", "수", "목", "금", "토", "일");
        FacilityStats stats = emptyStats(30, "최근 30일");
        stats.setBookingCount(10);
        stats.setParticipantCount(30);
        stats.setPopularDay("월");
        stats.setPeakTime("09시");

        List<FacilityStats.WeekdayDetail> weekdays = new java.util.ArrayList<>();
        for (int day = 0; day < days.size(); day++) {
            long count = day == 0 ? 15L : day == 1 ? 9L : day == 2 ? 6L : 0L;
            weekdays.add(new FacilityStats.WeekdayDetail(days.get(day), count,
                    (int) Math.round(count * 100.0 / 30)));
        }
        stats.setWeekdayDetails(weekdays);

        List<FacilityStats.HourDetail> hours = new java.util.ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            long count = hour == 9 ? 18L : hour == 14 ? 3L : hour == 18 ? 8L : hour == 20 ? 1L : 0L;
            hours.add(new FacilityStats.HourDetail(String.format("%02d시", hour), count,
                    (int) Math.round(count * 100.0 / 30)));
        }
        stats.setHourDetails(hours);

        List<FacilityStats.WeekdayHourDetail> weekdayHours = new java.util.ArrayList<>();
        for (int day = 0; day < days.size(); day++) {
            List<Long> hourlyCounts = new java.util.ArrayList<>();
            for (int hour = 0; hour < 24; hour++) {
                long count = day == 0 && hour == 9 ? 12L
                        : day == 0 && hour == 14 ? 3L
                        : day == 1 && hour == 9 ? 6L
                        : day == 1 && hour == 18 ? 2L
                        : day == 1 && hour == 20 ? 1L
                        : day == 2 && hour == 18 ? 6L
                        : 0L;
                hourlyCounts.add(count);
            }
            weekdayHours.add(new FacilityStats.WeekdayHourDetail(days.get(day), hourlyCounts));
        }
        stats.setWeekdayHourDetails(weekdayHours);
        return stats;
    }

    private static FacilityListItem facility(Long id, String name) {
        FacilitySummary facility = new FacilitySummary();
        facility.setFacilityId(id);
        facility.setName(name);
        facility.setAddress("서울특별시 테스트구 " + id);
        facility.setSportsLabel("풋살");
        facility.setConcurrentMatchLimit(2);
        facility.setStatus("운영 중");
        return FacilityListItem.from(facility);
    }
}
