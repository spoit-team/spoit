package co.kr.spoit.mat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.dto.MatchResultResponse;
import co.kr.spoit.mat.dto.MatchResultResponse.Outcome;
import co.kr.spoit.mat.service.MatchResultService;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RatingExclusionController.class)
@Import(SecurityConfig.class)
class RatingExclusionControllerTest {

    private static final String URL = "/admin/operations/matches/10/rating-exclusion";

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
    void excludesMatchForFacilityManager() throws Exception {
        given(matchResultService.excludeRating(eq(10L), eq(7L), any(LocalDateTime.class)))
                .willReturn(MatchResultResponse.of(Outcome.EXCLUDED, "제외"));

        mockMvc.perform(post(URL).with(user(manager())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("EXCLUDED"));
    }

    @Test
    void deniesManagerOfAnotherFacility() throws Exception {
        given(matchResultService.excludeRating(eq(10L), eq(7L), any(LocalDateTime.class)))
                .willThrow(new AccessDeniedException("담당 시설이 아님"));

        mockMvc.perform(post(URL).with(user(manager())).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsConflictForAppliedMatch() throws Exception {
        given(matchResultService.excludeRating(eq(10L), eq(7L), any(LocalDateTime.class)))
                .willReturn(MatchResultResponse.of(Outcome.ALREADY_APPLIED, "이미 반영"));

        mockMvc.perform(post(URL).with(user(manager())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.outcome").value("ALREADY_APPLIED"));
    }

    private static MemberPrincipal manager() {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("manager@example.com");
        member.setNickname("시설담당");
        member.setPhone("010-1234-5678");
        member.setRole("시설 관리자");
        return MemberPrincipal.from(member);
    }
}
