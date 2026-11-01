package co.kr.spoit.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.mapper.MemberMapper;

/** 로그인·로그아웃 필수 경로만 검증한다. 조회 결과는 목 매퍼로 대체한다. */
@SpringBootTest
@AutoConfigureMockMvc
class MemberAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MemberMapper memberMapper;

    @Test
    @DisplayName("로그인 화면은 폼 상태를 복원하고 위험한 redirect를 기본 경로로 바꾼다")
    void loginPageSanitizesRedirect() throws Exception {
        mockMvc.perform(get("/login").param("error", "오류").param("redirect", "/mypage"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attribute("error", "오류"))
                .andExpect(model().attribute("redirect", "/mypage"));

        mockMvc.perform(get("/login").param("redirect", "//evil.example"))
                .andExpect(model().attribute("redirect", "/matches"));
    }

    @Test
    @DisplayName("로그인 성공 시 세션에 principal을 남기고 비밀번호 해시는 지운다")
    void loginSuccessStoresPrincipalWithoutHash() throws Exception {
        given(memberMapper.findByEmail(anyString()))
                .willReturn(Optional.of(activeMember()));

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "player@example.com")
                        .param("password", "password1234!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches"))
                .andReturn().getRequest().getSession(false);

        MemberPrincipal principal = principalOf(session);
        assertThat(principal.getNickname()).isEqualTo("플레이어");
        assertThat(principal.getPassword()).isNull();
    }

    @Test
    @DisplayName("비밀번호 오류와 없는 계정은 같은 문구로 되돌린다")
    void loginFailureUsesSameMessage() throws Exception {
        given(memberMapper.findByEmail(anyString())).willAnswer(i ->
                "player@example.com".equals(i.getArgument(0)) ? Optional.of(activeMember()) : Optional.empty());

        String wrong = errorOf(login("player@example.com", "wrong-password"));
        String missing = errorOf(login("nobody@example.com", "password1234!"));

        assertThat(wrong).isNotBlank().isEqualTo(missing);
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 로그인은 거부된다")
    void loginRequiresCsrf() throws Exception {
        mockMvc.perform(post("/login")
                        .param("email", "player@example.com")
                        .param("password", "password1234!"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그아웃하면 세션이 무효화되고 로그인 화면으로 이동한다")
    void logoutInvalidatesSession() throws Exception {
        given(memberMapper.findByEmail(anyString()))
                .willReturn(Optional.of(activeMember()));
        MockHttpSession session = (MockHttpSession) login("player@example.com", "password1234!")
                .getRequest().getSession(false);

        mockMvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?message=*"));

        assertThat(session.isInvalid()).isTrue();
    }

    private org.springframework.test.web.servlet.MvcResult login(String email, String password)
            throws Exception {
        return mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    private static String errorOf(org.springframework.test.web.servlet.MvcResult result) {
        return UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("error");
    }

    private static MemberPrincipal principalOf(MockHttpSession session) {
        SecurityContext context = (SecurityContext) session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        return (MemberPrincipal) context.getAuthentication().getPrincipal();
    }

    private Member activeMember() {
        Member member = new Member();
        member.setUserId(1L);
        member.setEmail("player@example.com");
        member.setPasswordHash(passwordEncoder.encode("password1234!"));
        member.setNickname("플레이어");
        member.setPhone("010-1234-5678");
        member.setRole("일반 회원");
        return member;
    }
}
