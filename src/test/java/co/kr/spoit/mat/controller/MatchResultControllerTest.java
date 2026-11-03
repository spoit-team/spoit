package co.kr.spoit.mat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.domain.MatchResult;
import co.kr.spoit.mat.dto.MatchResultResponse;
import co.kr.spoit.mat.dto.MatchResultResponse.Outcome;
import co.kr.spoit.mat.dto.MatchResultView;
import co.kr.spoit.mat.service.MatchResultService;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MatchResultController.class)
@Import(SecurityConfig.class)
class MatchResultControllerTest {

    private static final String URL = "/mypage/matches/10/result";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchResultService matchResultService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    void anonymousRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        verify(matchResultService, never()).getView(anyLong(), anyLong(), any());
    }

    @Test
    void returnsResultViewForCaptain() throws Exception {
        var view = new MatchResultView(10L, "A", "A_WIN", true, false, null, null, "WAITING",
                LocalDateTime.of(2026, 10, 31, 20, 0), false);
        given(matchResultService.getView(eq(10L), eq(7L), any(LocalDateTime.class))).willReturn(Optional.of(view));

        mockMvc.perform(get(URL).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mySide").value("A"))
                .andExpect(jsonPath("$.opponentSubmitted").value(true))
                .andExpect(jsonPath("$.opponentResult").doesNotExist())
                .andExpect(jsonPath("$.deadlineAt").value("2026-10-31T20:00:00"))
                .andExpect(jsonPath("$.canSubmit").value(false));
    }

    @Test
    void submitsResultForCaptain() throws Exception {
        given(matchResultService.submit(eq(10L), eq(7L), eq(MatchResult.A_WIN), any(LocalDateTime.class)))
                .willReturn(MatchResultResponse.of(Outcome.CONFIRMED, "확정"));

        mockMvc.perform(post(URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"A_WIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CONFIRMED"));
    }

    @Test
    void deniesNonCaptain() throws Exception {
        given(matchResultService.submit(eq(10L), eq(7L), eq(MatchResult.DRAW), any(LocalDateTime.class)))
                .willThrow(new AccessDeniedException("팀장이 아님"));

        mockMvc.perform(post(URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"DRAW\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnknownResultValue() throws Exception {
        mockMvc.perform(post(URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"WIN\"}"))
                .andExpect(status().isBadRequest());

        verify(matchResultService, never()).submit(anyLong(), anyLong(), any(), any());
    }

    @Test
    void returnsConflictForClosedPeriod() throws Exception {
        given(matchResultService.submit(eq(10L), eq(7L), eq(MatchResult.B_WIN), any(LocalDateTime.class)))
                .willReturn(MatchResultResponse.of(Outcome.OUT_OF_PERIOD, "기간 아님"));

        mockMvc.perform(post(URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"result\":\"B_WIN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("OUT_OF_PERIOD"));
    }

    private static MemberPrincipal member() {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("captain@example.com");
        member.setNickname("팀장");
        member.setPhone("010-1234-5678");
        member.setRole("일반 회원");
        return MemberPrincipal.from(member);
    }
}
