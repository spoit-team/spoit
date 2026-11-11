package co.kr.spoit.global.config;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.admin;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RouterFunctions.route;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import co.kr.spoit.facility.service.FacilityService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import co.kr.spoit.admin.controller.SystemAdminController;
import co.kr.spoit.admin.service.SystemErrorLogService;
import co.kr.spoit.admin.service.SystemMonitoringService;
import co.kr.spoit.member.service.AdminUserService;
import co.kr.spoit.report.service.PenaltyService;

/** 실제 보안 필터 체인에서 마이페이지는 인증을 요구하고 관리자 화면은 역할별로 분리되는지 검증한다. */
@WebMvcTest(controllers = SystemAdminController.class)
@Import({SecurityConfig.class, SecurityConfigTest.StubRoutes.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @MockitoBean
    private FacilityService facilityService;

    @MockitoBean
    private SystemMonitoringService systemMonitoringService;

    @MockitoBean
    private SystemErrorLogService systemErrorLogService;

    @MockitoBean
    private AdminUserService adminUserService;

    @MockitoBean
    private PenaltyService penaltyService;

    @org.junit.jupiter.api.BeforeEach
    void stubSystemServices() {
        when(systemErrorLogService.load(any(), any(), any())).thenReturn(
                new co.kr.spoit.admin.dto.SystemErrorLogPage(
                        new co.kr.spoit.admin.dto.SystemErrorMetrics(0, 0, 0, null, null),
                        new co.kr.spoit.admin.dto.SystemErrorFilters(null, "all", 24), java.util.List.of()));
        when(adminUserService.loadPage(any(), any(), any(), any(Integer.class))).thenReturn(
                co.kr.spoit.member.dto.AdminUserPage.empty());
        when(penaltyService.loadPage(any(), any(), any(Integer.class)))
                .thenReturn(co.kr.spoit.report.dto.PenaltyPage.empty());
        when(systemMonitoringService.currentDashboard()).thenReturn(null);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/mypage",
            "/mypage/",
            "/mypage/profile",
            "/mypage/profile/",
            "/mypage/profile/details"
    })
    @DisplayName("비로그인 마이페이지 GET 요청은 로그인 화면으로 보낸다")
    void anonymousMyPageRedirectsToLogin(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("로그인 회원의 마이페이지 요청은 통과시킨다")
    void authenticatedMyPageIsAllowed() throws Exception {
        mockMvc.perform(get("/mypage").with(user(member())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/mypage/profile/details").with(user(member())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인 마이페이지 POST도 CSRF 토큰이 있으면 로그인 화면으로 보낸다")
    void anonymousMyPagePostRedirectsToLoginWithCsrf() throws Exception {
        mockMvc.perform(post("/mypage/profile").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("마이페이지 POST에서 CSRF 토큰이 없으면 거부한다")
    void myPagePostRequiresCsrf() throws Exception {
        mockMvc.perform(post("/mypage/profile").with(user(member())))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/login", "/matches"})
    @DisplayName("기존 공개 화면은 비로그인 상태에서도 계속 접근할 수 있다")
    void existingPublicPagesRemainPublic(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/admin/facility",
            "/admin/facility/settings",
            "/admin/operations",
            "/admin/operations/1",
            "/admin/system",
            "/admin/system/settings",
            "/admin/system/errors/export",
            "/admin/system/users/export"
    })
    @DisplayName("비로그인 관리자 화면 요청은 로그인 화면으로 보낸다")
    void anonymousAdminPagesRedirectToLogin(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/admin/facility",
            "/admin/facility/settings",
            "/admin/operations",
            "/admin/operations/1",
            "/admin/system",
            "/admin/system/settings",
            "/admin/system/errors/export",
            "/admin/system/users/export",
            "/admin/unknown"
    })
    @DisplayName("일반 회원은 관리자 화면에 직접 접근할 수 없다")
    void memberCannotAccessAdminPages(String path) throws Exception {
        mockMvc.perform(get(path).with(user(member())))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/admin/facility",
            "/admin/facility/settings",
            "/admin/facilities",
            "/admin/facilities/10",
            "/admin/facilities/10/delete",
            "/admin/operations",
            "/admin/operations/1"
    })
    @DisplayName("시설 관리자는 시설·운영 화면에 접근할 수 있다")
    void facilityManagerCanAccessFacilityPages(String path) throws Exception {
        mockMvc.perform(get(path).with(user(facilityManager())))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/admin/system",
            "/admin/system/settings",
            "/admin/system/errors/export",
            "/admin/system/users/export",
            "/admin/unknown"
    })
    @DisplayName("시설 관리자는 시스템 관리 화면에 접근할 수 없다")
    void facilityManagerCannotAccessSystemPages(String path) throws Exception {
        mockMvc.perform(get(path).with(user(facilityManager())))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/admin/system",
            "/admin/system/settings",
            "/admin/system/errors/export",
            "/admin/system/users/export",
            "/admin/unknown"
    })
    @DisplayName("시스템 관리자는 시스템 관리 화면에 접근할 수 있다")
    void adminCanAccessSystemPages(String path) throws Exception {
        mockMvc.perform(get(path).with(user(admin())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비로그인 모니터링 POST는 CSRF 토큰이 있어도 로그인 화면으로 보낸다")
    void anonymousMonitoringPostRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/admin/system/monitoring").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"member", "facilityManager"})
    @DisplayName("시스템 관리자가 아닌 사용자는 모니터링 POST를 호출할 수 없다")
    void nonAdminCannotPostMonitoring(String role) throws Exception {
        var principal = "member".equals(role) ? member() : facilityManager();
        mockMvc.perform(post("/admin/system/monitoring").with(user(principal)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("시스템 관리자의 모니터링 POST는 통과한다")
    void adminCanPostMonitoring() throws Exception {
        mockMvc.perform(post("/admin/system/monitoring").with(user(admin())).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("모니터링 POST에서 CSRF 토큰이 없으면 거부한다")
    void monitoringPostRequiresCsrf() throws Exception {
        mockMvc.perform(post("/admin/system/monitoring").with(user(admin())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("사용자 관리 상태 변경 라우트는 시스템 관리자만 호출할 수 있다")
    void userActionRoutesRequireSystemAdmin() throws Exception {
        for (String path : new String[] {
                "/admin/system/users/5/approve",
                "/admin/system/users/5/reject",
                "/admin/system/users/5/suspend",
                "/admin/system/users/suspend",
                "/admin/system/users/5/restore",
                "/admin/system/users/role",
                "/admin/system/penalties/9/release"}) {
            mockMvc.perform(post(path).with(csrf()).with(user(member())))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(path).with(csrf()).with(user(facilityManager())))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("사용자 관리 상태 변경은 CSRF 토큰이 없으면 거부한다")
    void userActionRoutesRequireCsrf() throws Exception {
        mockMvc.perform(post("/admin/system/users/5/suspend").with(user(admin())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/system/users/role").with(user(admin())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/system/penalties/9/release").with(user(admin())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("경로에 userId가 없는 정지 폼은 본문 userId로 처리하고 사용자 화면으로 되돌린다")
    void suspendWithoutPathVariableUsesBodyUserId() throws Exception {
        when(adminUserService.suspend(eq(7L), eq(5L), eq(7), any()))
                .thenReturn(co.kr.spoit.member.dto.AdminUserActionResult.applied("정지"));

        mockMvc.perform(post("/admin/system/users/suspend")
                        .param("userId", "5")
                        .param("durationDays", "7")
                        .param("reason", "운영 정책 위반")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/system?view=users*"));

        verify(adminUserService).suspend(7L, 5L, 7, "운영 정책 위반");
    }

    @Test
    @DisplayName("역할 변경은 로그인 관리자 ID와 입력 역할을 서비스로 넘긴다")
    void roleChangeUsesPrincipalId() throws Exception {
        when(adminUserService.changeRole(eq(7L), eq(5L), eq("facility"), any()))
                .thenReturn(co.kr.spoit.member.dto.AdminUserActionResult.applied("변경"));

        mockMvc.perform(post("/admin/system/users/role")
                        .param("userId", "5")
                        .param("role", "facility")
                        .param("reason", "시설 담당")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/system?view=users"));

        verify(adminUserService).changeRole(7L, 5L, "facility", "시설 담당");
    }

    @Test
    @DisplayName("페널티 해제는 인증 주체 ID로 조기 해제하고 페널티 화면으로 돌아간다")
    void releasePenaltyUsesPrincipalId() throws Exception {
        mockMvc.perform(post("/admin/system/penalties/9/release")
                        .param("status", "active")
                        .param("page", "2")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/system?view=penalties*"));

        verify(penaltyService).release(9L, 7L);
    }

    @Test
    @DisplayName("사용자 관리 export GET은 실제 CSV 응답을 돌려준다")
    void userExportReturnsCsv() throws Exception {
        mockMvc.perform(get("/admin/system/users/export").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @DisplayName("오류 로그 export GET은 실제 CSV 응답을 돌려준다")
    void errorExportReturnsCsv() throws Exception {
        mockMvc.perform(get("/admin/system/errors/export").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/admin/facility",
            "/admin/facility/settings",
            "/admin/facilities",
            "/admin/facilities/10",
            "/admin/facilities/10/delete",
            "/admin/operations",
            "/admin/operations/1"
    })
    @DisplayName("시스템 관리자는 시설·운영 화면에 접근할 수 없다")
    void adminCannotAccessFacilityPages(String path) throws Exception {
        mockMvc.perform(get(path).with(user(admin())))
                .andExpect(status().isForbidden());
    }

    /**
     * 실제 컨트롤러 대신 보안 필터 통과 여부만 관찰하기 위한 최소 라우트다.
     *
     * <p>테스트 슬라이스에만 명시적으로 등록되는 {@link RouterFunction}이라
     * 테스트 클래스가 런타임 클래스패스에 섞여도 애플리케이션 컴포넌트 스캔에
     * 등록되지 않는다.</p>
     */
    static class StubRoutes {

        @Bean
        RouterFunction<ServerResponse> securityStubRoutes() {
            return route(GET("/mypage"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/mypage/profile/details"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/login"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/matches"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/facility"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/facility/settings"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/facilities"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/facilities/10"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/facilities/10/delete"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/operations"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/operations/1"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/system"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/system/settings"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/unknown"), request -> ServerResponse.ok().build());
        }
    }
}
