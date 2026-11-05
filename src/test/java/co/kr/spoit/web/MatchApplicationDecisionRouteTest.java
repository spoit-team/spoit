package co.kr.spoit.web;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.match.controller.MatchApplicationDecisionController;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 운영 화면의 참가 신청 승인·반려 API가 인증 주체 ID로 처리되고, 권한·CSRF·처리 실패를 막는지 검증한다. */
@WebMvcTest(controllers = MatchApplicationDecisionController.class)
@Import(SecurityConfig.class)
class MatchApplicationDecisionRouteTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchApplicationService applicationService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("승인은 인증 주체 ID로 신청 한 건을 확정하고 안내 문구를 돌려준다")
    void confirmsByPrincipal() throws Exception {
        mockMvc.perform(post("/admin/operations/applications/31/confirm").with(user(facilityManager())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("참가 신청을 승인했습니다."));

        verify(applicationService).confirm(31L, 7L);
    }

    @Test
    @DisplayName("반려는 인증 주체 ID로 신청 한 건을 반려한다")
    void rejectsByPrincipal() throws Exception {
        mockMvc.perform(post("/admin/operations/applications/31/reject").with(user(facilityManager())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("참가 신청을 반려했습니다."));

        verify(applicationService).reject(31L, 7L);
    }

    @Test
    @DisplayName("처리할 수 없는 신청은 409와 서비스 문구를 돌려준다")
    void returnsConflictMessage() throws Exception {
        doThrow(new MatchApplyException("이미 처리된 신청입니다.")).when(applicationService).confirm(31L, 7L);

        mockMvc.perform(post("/admin/operations/applications/31/confirm").with(user(facilityManager())).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 처리된 신청입니다."));
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 처리하지 않는다")
    void requiresCsrf() throws Exception {
        mockMvc.perform(post("/admin/operations/applications/31/confirm").with(user(facilityManager())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("시설 관리 권한이 없는 회원은 승인·반려할 수 없다")
    void memberCannotDecide() throws Exception {
        mockMvc.perform(post("/admin/operations/applications/31/reject").with(user(member())).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(applicationService);
    }
}
