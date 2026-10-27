package co.kr.spoit.global.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
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

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import co.kr.spoit.web.CorePageController;

/** 실제 보안 필터 체인에서 마이페이지는 인증을 요구하고 관리자 화면은 역할별로 분리되는지 검증한다. */
@WebMvcTest(controllers = CorePageController.class)
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
        mockMvc.perform(get("/mypage").with(user(memberPrincipal())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/mypage/profile/details").with(user(memberPrincipal())))
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
        mockMvc.perform(post("/mypage/profile").with(user(memberPrincipal())))
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
            "/admin/system/settings"
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
            "/admin/unknown"
    })
    @DisplayName("일반 회원은 관리자 화면에 직접 접근할 수 없다")
    void memberCannotAccessAdminPages(String path) throws Exception {
        mockMvc.perform(get(path).with(user(memberPrincipal())))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/admin/facility",
            "/admin/facility/settings",
            "/admin/operations",
            "/admin/operations/1"
    })
    @DisplayName("시설 관리자는 시설·운영 화면에 접근할 수 있다")
    void facilityManagerCanAccessFacilityPages(String path) throws Exception {
        mockMvc.perform(get(path).with(user(facilityManagerPrincipal())))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/admin/system",
            "/admin/system/settings",
            "/admin/unknown"
    })
    @DisplayName("시설 관리자는 시스템 관리 화면에 접근할 수 없다")
    void facilityManagerCannotAccessSystemPages(String path) throws Exception {
        mockMvc.perform(get(path).with(user(facilityManagerPrincipal())))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/admin/system",
            "/admin/system/settings",
            "/admin/unknown"
    })
    @DisplayName("시스템 관리자는 시스템 관리 화면에 접근할 수 있다")
    void adminCanAccessSystemPages(String path) throws Exception {
        mockMvc.perform(get(path).with(user(adminPrincipal())))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/admin/facility",
            "/admin/facility/settings",
            "/admin/operations",
            "/admin/operations/1"
    })
    @DisplayName("시스템 관리자는 시설·운영 화면에 접근할 수 없다")
    void adminCannotAccessFacilityPages(String path) throws Exception {
        mockMvc.perform(get(path).with(user(adminPrincipal())))
                .andExpect(status().isForbidden());
    }

    private static MemberPrincipal memberPrincipal() {
        return principal("일반 회원");
    }

    private static MemberPrincipal facilityManagerPrincipal() {
        return principal("시설 관리자");
    }

    private static MemberPrincipal adminPrincipal() {
        return principal("시스템 관리자");
    }

    private static MemberPrincipal principal(String role) {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("player@example.com");
        member.setNickname("플레이어");
        member.setPhone("010-1234-5678");
        member.setRole(role);
        return MemberPrincipal.from(member);
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
                    .andRoute(GET("/admin/operations"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/operations/1"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/system"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/system/settings"), request -> ServerResponse.ok().build())
                    .andRoute(GET("/admin/unknown"), request -> ServerResponse.ok().build());
        }
    }
}
