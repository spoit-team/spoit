package co.kr.spoit.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.security.MemberSessionRegistry;
import jakarta.servlet.http.Cookie;

/**
 * 폼 로그인과 remember-me 자동 로그인이 <b>같은</b> 인증 완료 사슬(세션 ID 회전 → 등록 → 재확인)을
 * 타서 실제로 세션이 추적되는지 확인한다. 이 두 경로가 갈라지면 폐기 대상에서 빠진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MemberSessionRegistrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MemberSessionRegistry memberSessionRegistry;

    @MockitoBean
    private MemberMapper memberMapper;

    @Test
    @DisplayName("폼 로그인 세션이 회원 번호로 등록되고 추적 표식이 남는다")
    void formLoginRegistersSession() throws Exception {
        given(memberMapper.findByEmail(anyString())).willReturn(Optional.of(activeMember()));

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", MEMBER_EMAIL)
                        .param("password", RAW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches"))
                .andReturn().getRequest().getSession(false);

        assertThat(session).isNotNull();
        assertThat(memberSessionRegistry.getSessionInformation(session.getId())).isNotNull();
        assertThat(memberSessionRegistry.isTracked(session)).isTrue();
        assertThat(memberSessionRegistry.sessionsOf(MEMBER_ID))
                .anyMatch(info -> info.getSessionId().equals(session.getId()));
    }

    @Test
    @DisplayName("remember-me 자동 로그인도 같은 사슬을 타고 세션을 등록한다")
    void rememberMeLoginRegistersSession() throws Exception {
        given(memberMapper.findByEmail(anyString())).willReturn(Optional.of(activeMember()));

        Cookie rememberMe = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", MEMBER_EMAIL)
                        .param("password", RAW_PASSWORD)
                        .param("remember", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getCookie("remember-me");
        assertThat(rememberMe).isNotNull();
        assertThat(rememberMe.getValue()).isNotBlank();

        // 새 세션(기존 SecurityContext 없음)에서 쿠키만으로 자동 로그인한다.
        MockHttpSession freshSession = (MockHttpSession) mockMvc.perform(get("/mypage")
                        .cookie(rememberMe)
                        .session(new MockHttpSession()))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        assertThat(freshSession).isNotNull();
        assertThat(memberSessionRegistry.getSessionInformation(freshSession.getId())).isNotNull();
        assertThat(memberSessionRegistry.isTracked(freshSession)).isTrue();
    }

    @Test
    @DisplayName("폐기된 세션의 프로필 저장 요청은 CSRF 검사 이전에 로그인 화면으로 돌려보내진다")
    void expiredSessionPostIsRejectedBeforeCsrf() throws Exception {
        given(memberMapper.findByEmail(anyString())).willReturn(Optional.of(activeMember()));

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", MEMBER_EMAIL)
                        .param("password", RAW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn().getRequest().getSession(false);
        assertThat(session).isNotNull();

        // 같은 컨텍스트를 공유하는 다른 테스트의 세션이 남을 수 있으므로 개수 대신 이 세션의 만료만 확인한다.
        assertThat(memberSessionRegistry.expireSessionsOf(MEMBER_ID)).isPositive();
        assertThat(memberSessionRegistry.getSessionInformation(session.getId()).isExpired()).isTrue();

        // CSRF 토큰을 일부러 붙이지 않는다. 폐기 필터가 CsrfFilter 앞에서 막아야 403이 아니라 리다이렉트다.
        MvcResult result = mockMvc.perform(post("/mypage/profile")
                        .session(session)
                        .param("nickname", "새닉네임")
                        .param("phone", "010-9876-5432"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getRedirectedUrl()).startsWith("/login?error=");
        assertThat(session.isInvalid()).isTrue();

        Cookie sessionCookie = response.getCookie("JSESSIONID");
        Cookie rememberMeCookie = response.getCookie("remember-me");
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.getMaxAge()).isZero();
        assertThat(rememberMeCookie).isNotNull();
        assertThat(rememberMeCookie.getMaxAge()).isZero();
    }

    private static final Long MEMBER_ID = 1L;
    private static final String MEMBER_EMAIL = "player@example.com";
    private static final String RAW_PASSWORD = "password1234!";

    private Member activeMember() {
        Member member = new Member();
        member.setUserId(MEMBER_ID);
        member.setEmail(MEMBER_EMAIL);
        member.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        member.setNickname("플레이어");
        member.setPhone("010-1234-5678");
        member.setRole("일반 회원");
        return member;
    }
}
