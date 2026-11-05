package co.kr.spoit.mat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.dto.ParticipantHistoryView;
import co.kr.spoit.mat.service.ParticipantHistoryService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ParticipantHistoryController.class)
@Import(SecurityConfig.class)
class ParticipantHistoryControllerTest {

    private static final String URL = "/admin/operations/matches/10/participants/21/history";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ParticipantHistoryService historyService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    void returnsHistoryForFacilityManager() throws Exception {
        var view = new ParticipantHistoryView("참가자", "실버 4 · 64P", 7,
                List.of(new ParticipantHistoryView.RecentChange(LocalDateTime.of(2026, 10, 26, 13, 0),
                        new BigDecimal("-3.10"))),
                1, 2, List.of(new ParticipantHistoryView.Penalty("경고", null)));
        given(historyService.getHistory(eq(10L), eq(21L), eq(7L), any(LocalDateTime.class)))
                .willReturn(Optional.of(view));

        mockMvc.perform(get(URL).with(user(member("시설 관리자"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tierLabel").value("실버 4 · 64P"))
                .andExpect(jsonPath("$.recentChanges[0].calculatedAt").value("2026-10-26T13:00:00"))
                .andExpect(jsonPath("$.recentChanges[0].finalDelta").value(-3.10))
                .andExpect(jsonPath("$.confirmedReportCount").value(2))
                .andExpect(jsonPath("$.activePenalties[0].penaltyType").value("경고"))
                .andExpect(jsonPath("$.activePenalties[0].endAt").doesNotExist());
    }

    @Test
    void returnsNotFoundWithoutBodyForUnrelatedMemberOrMissingMatch() throws Exception {
        given(historyService.getHistory(eq(10L), eq(21L), eq(7L), any(LocalDateTime.class)))
                .willReturn(Optional.empty());

        mockMvc.perform(get(URL).with(user(member("시설 관리자"))))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void deniesManagerOfAnotherFacility() throws Exception {
        given(historyService.getHistory(eq(10L), eq(21L), eq(7L), any(LocalDateTime.class)))
                .willThrow(new AccessDeniedException("담당 시설 아님"));

        mockMvc.perform(get(URL).with(user(member("시설 관리자"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deniesMemberWithoutFacilityManagerRole() throws Exception {
        mockMvc.perform(get(URL).with(user(member("일반 회원"))))
                .andExpect(status().isForbidden());

        verify(historyService, never()).getHistory(anyLong(), anyLong(), anyLong(), any());
    }

    private static MemberPrincipal member(String role) {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("manager@example.com");
        member.setNickname("시설담당");
        member.setPhone("010-1234-5678");
        member.setRole(role);
        return MemberPrincipal.from(member);
    }
}
