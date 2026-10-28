package co.kr.spoit.mat.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.domain.TeamAssignment;
import co.kr.spoit.mat.dto.AutoAssignmentResult;
import co.kr.spoit.mat.dto.AutoAssignmentResult.Outcome;
import co.kr.spoit.mat.service.AutoAssignmentService;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AutoAssignmentController.class)
@Import(SecurityConfig.class)
class AutoAssignmentControllerTest {

    private static final String URL = "/admin/operations/matches/10/auto-assignment";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AutoAssignmentService autoAssignmentService;

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

        verify(autoAssignmentService, never()).preview(anyLong(), anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {"일반 회원", "시스템 관리자"})
    void rejectsUsersWithoutFacilityManagerRole(String role) throws Exception {
        mockMvc.perform(get(URL).with(user(principal(role))))
                .andExpect(status().isForbidden());

        verify(autoAssignmentService, never()).preview(anyLong(), anyLong());
    }

    @Test
    void rejectsManagerOfAnotherFacility() throws Exception {
        given(autoAssignmentService.preview(10L, 7L)).willThrow(new AccessDeniedException("담당 시설이 아님"));

        mockMvc.perform(get(URL).with(user(principal("시설 관리자"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsAssignmentForFacilityManager() throws Exception {
        var assignment = new TeamAssignment(List.of(1L, 2L), List.of(3L, 4L),
                new BigDecimal("2100.00"), new BigDecimal("2050.00"),
                new BigDecimal("1050.00"), new BigDecimal("1025.00"),
                new BigDecimal("50.00"), 1L, 3L);
        given(autoAssignmentService.preview(10L, 7L)).willReturn(AutoAssignmentResult.ready(assignment));

        mockMvc.perform(get(URL).with(user(principal("시설 관리자"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("READY"))
                .andExpect(jsonPath("$.assignment.teamAParticipantIds[0]").value(1))
                .andExpect(jsonPath("$.assignment.teamBParticipantIds[1]").value(4))
                .andExpect(jsonPath("$.assignment.captainAParticipantId").value(1))
                .andExpect(jsonPath("$.assignment.captainBParticipantId").value(3));
    }

    @Test
    void returnsConflictForBusinessState() throws Exception {
        given(autoAssignmentService.preview(10L, 7L)).willReturn(
                AutoAssignmentResult.rejected(Outcome.NOT_ENOUGH_PARTICIPANTS, "현재 3명"));

        mockMvc.perform(get(URL).with(user(principal("시설 관리자"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("NOT_ENOUGH_PARTICIPANTS"))
                .andExpect(jsonPath("$.message").value("현재 3명"))
                .andExpect(jsonPath("$.assignment").doesNotExist());
    }

    @Test
    void returnsNotFoundForMissingMatch() throws Exception {
        given(autoAssignmentService.preview(10L, 7L)).willReturn(
                AutoAssignmentResult.rejected(Outcome.NOT_FOUND, "매칭방 정보를 찾을 수 없습니다."));

        mockMvc.perform(get(URL).with(user(principal("시설 관리자"))))
                .andExpect(status().isNotFound());
    }

    private static MemberPrincipal principal(String role) {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("manager@example.com");
        member.setNickname("시설담당");
        member.setPhone("010-1234-5678");
        member.setRole(role);
        return MemberPrincipal.from(member);
    }
}
