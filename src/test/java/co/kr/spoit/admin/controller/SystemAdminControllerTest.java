package co.kr.spoit.admin.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.kr.spoit.admin.dto.SystemDashboard;
import co.kr.spoit.admin.service.SystemMonitoringService;

/** 컨트롤러가 기본값이 아니라 서비스가 만든 모니터링 모델을 뷰·JSON으로 넘기는지 검증한다. */
class SystemAdminControllerTest {

    private static SystemDashboard dashboard() {
        return new SystemDashboard("11-02 10:00", "정상", "1시간 0분", 12, 20, 34, "512MB",
                45, null, null, true,
                List.of(new SystemDashboard.Bar(12, 34, "10:00")),
                List.of(new SystemDashboard.ServiceStatus("API 서버", "정상", "good")),
                List.of(), List.of());
    }

    @Test
    void dashboardComesFromMonitoringService() throws Exception {
        SystemMonitoringService service = mock(SystemMonitoringService.class);
        SystemDashboard dashboard = dashboard();
        when(service.currentDashboard()).thenReturn(dashboard);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SystemAdminController(service)).build();

        mockMvc.perform(get("/admin/system"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/system"))
                .andExpect(model().attribute("view", "dashboard"))
                .andExpect(model().attribute("dashboard", hasProperty("cpuPercent", notNullValue())))
                .andExpect(model().attribute("dashboard", hasProperty("collectedAt", notNullValue())));
    }

    @Test
    @DisplayName("모니터링 POST는 서비스의 최신 dashboard를 JSON으로 돌려준다")
    void monitoringReturnsLatestDashboardAsJson() throws Exception {
        SystemMonitoringService service = mock(SystemMonitoringService.class);
        when(service.currentDashboard()).thenReturn(dashboard());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SystemAdminController(service)).build();

        mockMvc.perform(post("/admin/system/monitoring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectedAt").value("11-02 10:00"))
                .andExpect(jsonPath("$.apiStatus").value("정상"))
                .andExpect(jsonPath("$.cpuPercent").value(12))
                .andExpect(jsonPath("$.memoryPercent").value(34))
                .andExpect(jsonPath("$.allHealthy").value(true))
                .andExpect(jsonPath("$.bars[0].label").value("10:00"))
                .andExpect(jsonPath("$.services[0].name").value("API 서버"));
    }

    @Test
    @DisplayName("모니터링 응답은 캐시를 금지하는 헤더를 포함한다")
    void monitoringResponseForbidsCaching() throws Exception {
        SystemMonitoringService service = mock(SystemMonitoringService.class);
        when(service.currentDashboard()).thenReturn(dashboard());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SystemAdminController(service)).build();

        mockMvc.perform(post("/admin/system/monitoring"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", containsString("no-cache")))
                .andExpect(header().string("Pragma", "no-cache"));
    }
}
