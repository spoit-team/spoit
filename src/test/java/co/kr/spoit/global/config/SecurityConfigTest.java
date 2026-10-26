package co.kr.spoit.global.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 실제 보안 필터 체인에서 마이페이지 계열만 인증을 요구하는지 검증한다. */
@WebMvcTest(controllers = SecurityConfigTest.StubController.class)
@Import({SecurityConfig.class, SecurityConfigTest.StubController.class})
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

    private static MemberPrincipal memberPrincipal() {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("player@example.com");
        member.setNickname("플레이어");
        member.setPhone("010-1234-5678");
        member.setRole("일반 회원");
        return MemberPrincipal.from(member);
    }

    /** 실제 컨트롤러 대신 보안 필터 통과 여부만 관찰하기 위한 최소 핸들러다. */
    @RestController
    static class StubController {

        @GetMapping("/mypage")
        String myPage() {
            return "mypage";
        }

        @GetMapping("/mypage/profile/details")
        String profileDetails() {
            return "profile-details";
        }

        @GetMapping("/login")
        String login() {
            return "login";
        }

        @GetMapping("/matches")
        String matches() {
            return "matches";
        }
    }
}
