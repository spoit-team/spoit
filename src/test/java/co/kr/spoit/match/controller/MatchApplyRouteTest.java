package co.kr.spoit.match.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.match.service.MatchService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/**
 * 기존 {@code POST /matches/{id}/apply} 경로·인증·CSRF 계약이 그대로인지 검증한다.
 * 시설 일정 거부는 500이 아니라 기존 flash 오류 안내로 돌아가야 한다.
 */
@WebMvcTest(controllers = MatchController.class)
@Import(SecurityConfig.class)
class MatchApplyRouteTest {

    private static final long MATCH_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchService matchService;

    @MockitoBean
    private MatchApplicationService applicationService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("신청 POST는 CSRF 토큰이 없으면 거부한다")
    void applyRequiresCsrf() throws Exception {
        mockMvc.perform(post("/matches/{matchId}/apply", MATCH_ID)
                        .with(user(member()))
                        .param("agreeRules", "true"))
                .andExpect(status().isForbidden());
        verify(applicationService, never()).applyIndividual(anyLong(), anyLong());
    }

    @Test
    @DisplayName("비로그인 신청 POST는 CSRF 토큰이 있어도 로그인 화면으로 보낸다")
    void applyRequiresLogin() throws Exception {
        mockMvc.perform(post("/matches/{matchId}/apply", MATCH_ID).with(csrf())
                        .param("agreeRules", "true"))
                .andExpect(status().is3xxRedirection());
        verify(applicationService, never()).applyIndividual(anyLong(), anyLong());
    }

    @Test
    @DisplayName("로그인 회원의 정상 신청은 기존 완료 화면으로 이동한다")
    void applyRedirectsToCompletion() throws Exception {
        given(applicationService.applyIndividual(MATCH_ID, 7L)).willReturn("RCPT-TEST");

        mockMvc.perform(post("/matches/{matchId}/apply", MATCH_ID)
                        .with(user(member()))
                        .with(csrf())
                        .param("agreeRules", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches/1/apply/complete?receiptNo=RCPT-TEST"));
    }

    @Test
    @DisplayName("시설 일정 거부는 500이 아니라 신청 화면의 오류 안내로 돌아간다")
    void facilityRejectionShowsFlashError() throws Exception {
        given(applicationService.applyIndividual(MATCH_ID, 7L))
                .willThrow(new MatchApplyException("해당 시간에는 시설을 운영하지 않습니다."));

        mockMvc.perform(post("/matches/{matchId}/apply", MATCH_ID)
                        .with(user(member()))
                        .with(csrf())
                        .param("agreeRules", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/matches/1/apply"))
                .andExpect(flash().attribute("error", "해당 시간에는 시설을 운영하지 않습니다."));
    }
}
