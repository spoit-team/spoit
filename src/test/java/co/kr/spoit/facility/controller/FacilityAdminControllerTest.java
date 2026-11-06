package co.kr.spoit.facility.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
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
        FacilityStats stats = FacilityStats.empty(30, "최근 30일");
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
                .thenReturn(FacilityStats.empty(31, "최근 31일"));

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
                .thenReturn(FacilityStats.empty(30, "최근 30일"));

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
        FacilityStats empty = FacilityStats.empty(30, "최근 30일");
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
                .thenReturn(FacilityStats.empty(30, "최근 30일"));

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
                .thenReturn(FacilityStats.empty(30, "최근 30일"));

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
                .thenReturn(FacilityStats.empty(30, "최근 30일"));
        when(facilityStatsService.load(eq(7L), eq(99L), any(), any(), any()))
                .thenReturn(FacilityStats.empty(30, "최근 30일"));

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
