package co.kr.spoit.mat.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.dto.AssignmentResult;
import co.kr.spoit.mat.dto.AssignmentResult.Outcome;
import co.kr.spoit.mat.dto.TeamAssignmentView;
import co.kr.spoit.mat.service.TeamAssignmentService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TeamAssignmentController.class)
@Import(SecurityConfig.class)
class TeamAssignmentControllerTest {

    private static final String BASE = "/admin/operations/matches/10";
    private static final String MANUAL_BODY = "{\"teamA\":[1,2],\"teamB\":[3,4]}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamAssignmentService teamAssignmentService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    void returnsAssignmentView() throws Exception {
        var view = new TeamAssignmentView(10L, LocalDateTime.of(2026, 10, 24, 10, 0), 1, 2, true, "MANUAL",
                false, true, List.of(
                new TeamAssignmentView.Participant(1L, "회원1", new BigDecimal("1200.00"), "A", true),
                new TeamAssignmentView.Participant(2L, "회원2", new BigDecimal("900.00"), "B", true)));
        given(teamAssignmentService.getView(10L, 7L)).willReturn(Optional.of(view));

        mockMvc.perform(get(BASE + "/assignment").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").value("2026-10-24T10:00:00"))
                .andExpect(jsonPath("$.teamCapacity").value(1))
                .andExpect(jsonPath("$.assigned").value(true))
                .andExpect(jsonPath("$.canReset").value(true))
                .andExpect(jsonPath("$.participants[0].nickname").value("회원1"))
                .andExpect(jsonPath("$.participants[1].side").value("B"));
    }

    @Test
    void returnsNotFoundForMissingMatchView() throws Exception {
        given(teamAssignmentService.getView(10L, 7L)).willReturn(Optional.empty());

        mockMvc.perform(get(BASE + "/assignment").with(user(facilityManager())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.outcome").value("NOT_FOUND"));
    }

    @Test
    void confirmsAutoAssignment() throws Exception {
        given(teamAssignmentService.confirmAuto(10L, 7L)).willReturn(AssignmentResult.assigned());

        mockMvc.perform(post(BASE + "/auto-assignment").with(user(facilityManager())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ASSIGNED"));
    }

    @Test
    void returnsConflictWhenAutoAssignmentAlreadyDone() throws Exception {
        given(teamAssignmentService.confirmAuto(10L, 7L)).willReturn(
                AssignmentResult.rejected(Outcome.ALREADY_ASSIGNED, "이미 팀 배정이 끝난 매칭방입니다."));

        mockMvc.perform(post(BASE + "/auto-assignment").with(user(facilityManager())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("ALREADY_ASSIGNED"))
                .andExpect(jsonPath("$.message").value("이미 팀 배정이 끝난 매칭방입니다."));
    }

    @Test
    void confirmsManualAssignment() throws Exception {
        given(teamAssignmentService.confirmManual(10L, 7L, List.of(1L, 2L), List.of(3L, 4L)))
                .willReturn(AssignmentResult.assigned());

        mockMvc.perform(post(BASE + "/assignment").with(user(facilityManager())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(MANUAL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ASSIGNED"));
    }

    @Test
    void returnsBadRequestForInvalidManualTeams() throws Exception {
        given(teamAssignmentService.confirmManual(10L, 7L, List.of(1L, 2L), List.of(3L, 4L)))
                .willReturn(AssignmentResult.rejected(Outcome.INVALID_TEAMS, "확정 참가자 전원을 나눠 주세요."));

        mockMvc.perform(post(BASE + "/assignment").with(user(facilityManager())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(MANUAL_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.outcome").value("INVALID_TEAMS"));
    }

    @Test
    void resetsAssignment() throws Exception {
        given(teamAssignmentService.reset(10L, 7L)).willReturn(AssignmentResult.reset());

        mockMvc.perform(delete(BASE + "/assignment").with(user(facilityManager())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RESET"));
    }

    @Test
    void returnsConflictWhenResetAfterStart() throws Exception {
        given(teamAssignmentService.reset(10L, 7L)).willReturn(
                AssignmentResult.rejected(Outcome.STARTED, "이미 시작한 경기는 팀 배정을 바꿀 수 없습니다."));

        mockMvc.perform(delete(BASE + "/assignment").with(user(facilityManager())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("STARTED"));
    }

    @Test
    void rejectsPostWithoutCsrfToken() throws Exception {
        mockMvc.perform(post(BASE + "/auto-assignment").with(user(facilityManager())))
                .andExpect(status().isForbidden());

        verify(teamAssignmentService, never()).confirmAuto(anyLong(), anyLong());
    }
}
