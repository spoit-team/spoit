package co.kr.spoit.mat.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.dto.MatchResultSummary;
import co.kr.spoit.mat.service.MatchResultService;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 결과 화면 진입 라우트가 확정 참가자에게만 열리고, 경기 없음·비참가자를 각각 404·403으로 구분하는지 검증한다. */
@WebMvcTest(controllers = MatchResultPageController.class)
@Import(SecurityConfig.class)
class MatchResultPageControllerTest {

    private static final String URL = "/mypage/matches/10/result-form";

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
    void rendersResultPageForConfirmedParticipant() throws Exception {
        given(matchResultService.getSummary(10L, 7L)).willReturn(Optional.of(
                new MatchResultSummary(10L, "종료", "CONFIRMED", "APPLIED", "A_WIN", "A", null)));

        mockMvc.perform(get(URL).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/match-result"))
                .andExpect(model().attribute("matchId", 10L))
                // 실제 템플릿이 모델의 경기 번호로 렌더링되고, 스크립트가 읽는 진입 마커가 남는지 확인한다.
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("data-result-root"),
                        Matchers.containsString("data-match-id=\"10\""),
                        Matchers.containsString("data-csrf-header=\"X-CSRF-TOKEN\""))))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.stringContainsInOrder("id=\"match-result\"", "id=\"match-evaluation\""),
                        Matchers.matchesPattern("(?s).*<section(?=[^>]*\\sid=\"match-evaluation\")"
                                + "(?=[^>]*\\shidden(?:\\s|=|>))[^>]*>.*"),
                        Matchers.containsString("data-evaluation-url=\"/mypage/matches/10/evaluation\""),
                        Matchers.stringContainsInOrder("\"/js/match-evaluation.js\"", "\"/js/match-result.js\""))))
                .andExpect(content().string(Matchers.allOf(
                        Matchers.containsString("data-notification-open"),
                        Matchers.containsString("id=\"notification-modal\""),
                        Matchers.containsString("data-notification-authenticated=\"true\""),
                        Matchers.containsString("data-notification-api=\"/api/notifications\""),
                        Matchers.containsString("data-notification-stream=\"/ws/notifications\""))));
    }

    @Test
    void returnsNotFoundWhenMatchDoesNotExist() throws Exception {
        given(matchResultService.getSummary(10L, 7L)).willReturn(Optional.empty());

        mockMvc.perform(get(URL).with(user(member())))
                .andExpect(status().isNotFound());
    }

    @Test
    void deniesNonParticipant() throws Exception {
        given(matchResultService.getSummary(10L, 7L))
                .willThrow(new AccessDeniedException("확정 참가자 아님"));

        mockMvc.perform(get(URL).with(user(member())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        verify(matchResultService, never()).getSummary(anyLong(), anyLong());
    }

    private static MemberPrincipal member() {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("participant@example.com");
        member.setNickname("참가자");
        member.setPhone("010-1234-5678");
        member.setRole("일반 회원");
        return MemberPrincipal.from(member);
    }
}
