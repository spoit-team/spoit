package co.kr.spoit.admin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.kr.spoit.admin.dto.SystemDashboard;
import co.kr.spoit.admin.dto.SystemErrorFilters;
import co.kr.spoit.admin.dto.SystemErrorLog;
import co.kr.spoit.admin.dto.SystemErrorLogPage;
import co.kr.spoit.admin.dto.SystemErrorMetrics;
import co.kr.spoit.admin.service.SystemErrorLogService;
import co.kr.spoit.admin.service.SystemMonitoringService;
import co.kr.spoit.member.dto.AdminUserFilters;
import co.kr.spoit.member.dto.AdminUserListItem;
import co.kr.spoit.member.dto.AdminUserMetrics;
import co.kr.spoit.member.dto.AdminUserPage;
import co.kr.spoit.member.dto.AdminUserPagination;
import co.kr.spoit.member.service.AdminUserService;
import co.kr.spoit.report.dto.PenaltyFilters;
import co.kr.spoit.report.dto.PenaltyMetrics;
import co.kr.spoit.report.dto.PenaltyPage;
import co.kr.spoit.report.service.PenaltyService;

/**
 * 컨트롤러가 기본값이 아니라 서비스가 만든 실제 조회 결과를 뷰·JSON으로 넘기는지 검증한다.
 *
 * <p>뷰 이름·모델 계약(오류/사용자/페널티)과 검색 조건 전달, CSV export 응답을 한 번에 고정한다.</p>
 */
class SystemAdminControllerTest {

    private SystemMonitoringService monitoringService;
    private SystemErrorLogService errorLogService;
    private AdminUserService adminUserService;
    private PenaltyService penaltyService;
    private MockMvc mockMvc;

    private static SystemDashboard dashboard() {
        return new SystemDashboard("11-02 10:00", "정상", "1시간 0분", 12, 20, 34, "512MB",
                45, 2, 2, true,
                List.of(new SystemDashboard.Bar(12, 34, "10:00")),
                List.of(new SystemDashboard.ServiceStatus("API 서버", "정상", "good")),
                List.of(new SystemDashboard.RecentError("10:00", "오류", "bad", "match", "요약", "CODE", 2)),
                List.of());
    }

    @BeforeEach
    void setUp() {
        monitoringService = mock(SystemMonitoringService.class);
        errorLogService = mock(SystemErrorLogService.class);
        adminUserService = mock(AdminUserService.class);
        penaltyService = mock(PenaltyService.class);
        when(monitoringService.currentDashboard()).thenReturn(dashboard());
        when(errorLogService.load(any(), any(), any())).thenReturn(new SystemErrorLogPage(
                new SystemErrorMetrics(1, 0, 1, null, "2026-11-02 10:00:00"),
                new SystemErrorFilters(null, "all", 24),
                List.of(new SystemErrorLog("2026-11-02 10:00:00", "error", "오류", "bad", "match", "CODE",
                        "요약", 1, "상세"))));
        when(adminUserService.loadPage(any(), any(), any(), anyInt())).thenReturn(new AdminUserPage(
                new AdminUserMetrics(3, 1, 1, 1, 0, 0), new AdminUserFilters(null, null, null, 0),
                List.of(), new AdminUserPagination(0, 1, null, null)));
        when(penaltyService.loadPage(any(), any(), anyInt())).thenReturn(PenaltyPage.empty());
        mockMvc = MockMvcBuilders.standaloneSetup(new SystemAdminController(monitoringService,
                errorLogService, adminUserService, penaltyService)).build();
    }

    @Test
    void dashboardComesFromMonitoringService() throws Exception {
        mockMvc.perform(get("/admin/system"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/system"))
                .andExpect(model().attribute("view", "dashboard"))
                .andExpect(model().attribute("dashboard", hasProperty("cpuPercent", notNullValue())))
                .andExpect(model().attribute("dashboard", hasProperty("collectedAt", notNullValue())))
                .andExpect(model().attribute("errors", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("모니터링 POST는 서비스의 최신 dashboard를 JSON으로 돌려준다")
    void monitoringReturnsLatestDashboardAsJson() throws Exception {
        mockMvc.perform(post("/admin/system/monitoring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectedAt").value("11-02 10:00"))
                .andExpect(jsonPath("$.apiStatus").value("정상"))
                .andExpect(jsonPath("$.cpuPercent").value(12))
                .andExpect(jsonPath("$.memoryPercent").value(34))
                .andExpect(jsonPath("$.recentErrorCount").value(2))
                .andExpect(jsonPath("$.recentErrors[0].code").value("CODE"))
                .andExpect(jsonPath("$.allHealthy").value(true))
                .andExpect(jsonPath("$.bars[0].label").value("10:00"))
                .andExpect(jsonPath("$.services[0].name").value("API 서버"));
    }

    @Test
    @DisplayName("모니터링 응답은 캐시를 금지하는 헤더를 포함한다")
    void monitoringResponseForbidsCaching() throws Exception {
        mockMvc.perform(post("/admin/system/monitoring"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(header().string("Pragma", "no-cache"));
    }

    @Test
    @DisplayName("view·검색 조건이 화면 값과 서비스 호출에 그대로 반영된다")
    void viewAndFiltersArePassedThrough() throws Exception {
        when(adminUserService.loadPage("kim", "facility", "suspended", 2)).thenReturn(new AdminUserPage(
                new AdminUserMetrics(10, 0, 4, 1, 3, 2),
                new AdminUserFilters("kim", "facility", "suspended", 2), List.of(),
                new AdminUserPagination(2, 3, "/admin/system?view=users&page=1",
                        "/admin/system?view=users&page=3")));
        when(penaltyService.loadPage("kim", "suspended", 2)).thenReturn(PenaltyPage.empty());

        mockMvc.perform(get("/admin/system")
                        .param("view", "users")
                        .param("q", "kim")
                        .param("role", "facility")
                        .param("status", "suspended")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "users"))
                .andExpect(model().attribute("userMetrics", hasProperty("totalCount", notNullValue())))
                .andExpect(model().attribute("userFilters", hasProperty("q", notNullValue())))
                .andExpect(model().attribute("userPagination", hasProperty("page", notNullValue())));

        verify(adminUserService).loadPage("kim", "facility", "suspended", 2);
        verify(penaltyService).loadPage("kim", "suspended", 2);
    }

    @Test
    @DisplayName("페널티 화면은 status·page를 그대로 서비스로 넘기고 page 모델과 다음 페이지 여부를 노출한다")
    void penaltyViewPassesFilters() throws Exception {
        when(penaltyService.loadPage("kim", "released", 1)).thenReturn(new PenaltyPage(
                new co.kr.spoit.report.dto.PenaltyMetrics(0, 0, 0),
                new co.kr.spoit.report.dto.PenaltyFilters("kim", "released", 1),
                List.of(), true));

        mockMvc.perform(get("/admin/system")
                        .param("view", "penalties")
                        .param("q", "kim")
                        .param("status", "released")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "penalties"))
                .andExpect(model().attribute("penaltyPage", hasProperty("hasNextPenalties", notNullValue())))
                .andExpect(model().attribute("hasNextPenalties", true))
                .andExpect(model().attribute("penaltyFilters", hasProperty("status", notNullValue())));

        verify(penaltyService).loadPage("kim", "released", 1);
    }

    @Test
    @DisplayName("알 수 없는 view는 모니터링으로 되돌리고 잘못된 page는 0으로 정규화한다")
    void normalizesUnknownViewAndPage() throws Exception {
        mockMvc.perform(get("/admin/system").param("view", "audit").param("page", "-4"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "dashboard"));

        verify(adminUserService).loadPage(null, null, null, 0);
        verify(penaltyService).loadPage(null, null, 0);
    }

    @Test
    @DisplayName("오류 로그 CSV export는 실제 응답 본문과 첨부 헤더를 돌려준다")
    void exportErrorsReturnsCsv() throws Exception {
        when(errorLogService.load("match", "error", "24")).thenReturn(new SystemErrorLogPage(
                new SystemErrorMetrics(1, 0, 1, null, "2026-11-02 10:00:00"),
                new SystemErrorFilters("match", "error", 24),
                List.of(new SystemErrorLog("2026-11-02 10:00:00", "error", "오류", "bad", "match", "CODE",
                        "요약", 1, "상세"))));

        mockMvc.perform(get("/admin/system/errors/export")
                        .param("q", "match")
                        .param("severity", "error")
                        .param("period", "24"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                        .contains("발생 시각").contains("CODE").contains("요약"));
    }

    @Test
    @DisplayName("사용자 CSV export는 필터 결과를 실제 본문으로 돌려준다")
    void exportUsersReturnsCsv() throws Exception {
        when(adminUserService.findAllForExport("kim", "facility", "suspended"))
                .thenReturn(List.of(exportRow(1L, "회원1"), exportRow(21L, "회원21")));

        mockMvc.perform(get("/admin/system/users/export")
                        .param("q", "kim")
                        .param("role", "facility")
                        .param("status", "suspended"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                        .contains("계정 ID").contains("닉네임")
                        .contains("회원1").contains("회원21"));

        verify(adminUserService).findAllForExport("kim", "facility", "suspended");
    }

    private static AdminUserListItem exportRow(long userId, String nickname) {
        return new AdminUserListItem(userId, nickname, "user" + userId + "@example.invalid", "회원", "",
                "member", "없음", "neutral", false, "정상", "good", false, null, "2026-01-01", "—");
    }

    @Test
    @DisplayName("CSV 칸은 쉼표·따옴표를 인용하고 수식 주입을 무력화한다")
    void csvCellEscapes() {
        org.assertj.core.api.Assertions.assertThat(SystemAdminController.csvCell("a,b")).isEqualTo("\"a,b\"");
        org.assertj.core.api.Assertions.assertThat(SystemAdminController.csvCell("=cmd")).isEqualTo("'=cmd");
        org.assertj.core.api.Assertions.assertThat(SystemAdminController.csvCell(null)).isEmpty();
    }
}
