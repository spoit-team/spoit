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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.domain.ObservationStatus;
import co.kr.spoit.mat.dto.EvaluationSubmitRequest;
import co.kr.spoit.mat.dto.EvaluationSubmitRequest.Score;
import co.kr.spoit.mat.dto.EvaluationSubmitRequest.TargetEvaluation;
import co.kr.spoit.mat.dto.MatchEvaluationView;
import co.kr.spoit.mat.dto.MatchResultResponse;
import co.kr.spoit.mat.dto.MatchResultResponse.Outcome;
import co.kr.spoit.mat.service.MatchEvaluationService;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MatchEvaluationController.class)
@Import(SecurityConfig.class)
class MatchEvaluationControllerTest {

    private static final String URL = "/mypage/matches/10/evaluation";
    private static final String BODY = """
            {"evaluations":[{"targetParticipantId":2,"scores":[
                {"itemId":101,"observationStatus":"OBSERVED","score":8},
                {"itemId":102,"observationStatus":"NOT_OBSERVED","score":null}]}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchEvaluationService evaluationService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    void returnsEvaluationViewForCaptain() throws Exception {
        var view = new MatchEvaluationView(10L, "A", List.of(new MatchEvaluationView.Target(2L, "B팀장", "B")),
                List.of(new MatchEvaluationView.Item(101L, "서브")), false, true,
                LocalDateTime.of(2026, 10, 31, 20, 0));
        given(evaluationService.getView(eq(10L), eq(7L), any(LocalDateTime.class))).willReturn(Optional.of(view));

        mockMvc.perform(get(URL).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targets[0].participantId").value(2))
                .andExpect(jsonPath("$.targets[0].userId").doesNotExist())
                .andExpect(jsonPath("$.items[0].itemName").value("서브"))
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andExpect(jsonPath("$.deadlineAt").value("2026-10-31T20:00:00"));
    }

    // 경기가 없거나 팀장이 아니면 결과 조회 API와 같이 404·403으로 답한다.
    @Test
    void returnsNotFoundForMissingMatch() throws Exception {
        given(evaluationService.getView(eq(10L), eq(7L), any(LocalDateTime.class))).willReturn(Optional.empty());

        mockMvc.perform(get(URL).with(user(member())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.outcome").value("NOT_FOUND"));
    }

    @Test
    void deniesViewForNonCaptain() throws Exception {
        given(evaluationService.getView(eq(10L), eq(7L), any(LocalDateTime.class)))
                .willThrow(new AccessDeniedException("팀장이 아님"));

        mockMvc.perform(get(URL).with(user(member())))
                .andExpect(status().isForbidden());
    }

    @Test
    void submitsEvaluationWithScoresFromRequestBody() throws Exception {
        var request = new EvaluationSubmitRequest(List.of(new TargetEvaluation(2L, List.of(
                new Score(101L, ObservationStatus.OBSERVED, new BigDecimal("8")),
                new Score(102L, ObservationStatus.NOT_OBSERVED, null)))));
        given(evaluationService.submit(eq(10L), eq(7L), eq(request), any(LocalDateTime.class)))
                .willReturn(MatchResultResponse.of(Outcome.SUBMITTED, "제출"));

        mockMvc.perform(post(URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SUBMITTED"));
    }

    @Test
    void returnsBadRequestForInvalidEvaluation() throws Exception {
        given(evaluationService.submit(eq(10L), eq(7L), any(), any(LocalDateTime.class)))
                .willReturn(MatchResultResponse.of(Outcome.INVALID_EVALUATION, "대상 누락"));

        mockMvc.perform(post(URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.outcome").value("INVALID_EVALUATION"));
    }

    @Test
    void rejectsUnknownObservationStatus() throws Exception {
        mockMvc.perform(post(URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("NOT_OBSERVED", "SKIPPED")))
                .andExpect(status().isBadRequest());

        verify(evaluationService, never()).submit(anyLong(), anyLong(), any(), any());
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
