package co.kr.spoit.report.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.admin;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import co.kr.spoit.report.dto.ReportSubmitRequest;
import co.kr.spoit.report.dto.ReportTargetResponse;
import co.kr.spoit.report.service.MemberReportService;
import co.kr.spoit.report.service.ReportAccessDeniedException;
import co.kr.spoit.report.service.ReportNotFoundException;

/**
 * 회원 신고 API의 접근 권한·응답 계약·오류 상태 매핑을 검증한다.
 *
 * <p>로그인 확인은 실제 {@link SecurityConfig}가 맡고, 컨트롤러는 인증 주체 ID만 서비스로 넘긴다.
 * 확정 참가·경기 상태·중복 판정은 서비스 단위 테스트가 맡는다. 잘못된 입력은 400, 권한·상태 위반은
 * 403, 없는 경기는 404이며 어느 응답에도 참가자 정보를 싣지 않는다.</p>
 */
@WebMvcTest(controllers = MemberReportController.class)
@Import(SecurityConfig.class)
class MemberReportControllerTest {

    private static final String TARGETS_URL = "/mypage/matches/10/report-targets";
    private static final String REPORTS_URL = "/mypage/matches/10/reports";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberReportService memberReportService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    void returnsTargetsForParticipant() throws Exception {
        when(memberReportService.loadTargets(7L, 10L))
                .thenReturn(List.of(new ReportTargetResponse(21L, "상대")));

        mockMvc.perform(get(TARGETS_URL).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(21))
                .andExpect(jsonPath("$[0].nickname").value("상대"))
                .andExpect(jsonPath("$[0].email").doesNotExist());

        verify(memberReportService).loadTargets(7L, 10L);
    }

    @Test
    void deniesTargetsForNonParticipant() throws Exception {
        when(memberReportService.loadTargets(7L, 10L))
                .thenThrow(new ReportAccessDeniedException("이 경기의 확정 참가자만 신고할 수 있습니다."));

        mockMvc.perform(get(TARGETS_URL).with(user(member())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reportId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.message").value("이 경기의 확정 참가자만 신고할 수 있습니다."));
    }

    @Test
    void returnsNotFoundForMissingMatch() throws Exception {
        when(memberReportService.loadTargets(7L, 10L))
                .thenThrow(new ReportNotFoundException("경기 정보를 찾을 수 없습니다."));

        mockMvc.perform(get(TARGETS_URL).with(user(member())))
                .andExpect(status().isNotFound());
    }

    @Test
    void submitsReportWithPrincipalIdAndReturnsReportId() throws Exception {
        when(memberReportService.submit(eq(7L), eq(10L), any(ReportSubmitRequest.class))).thenReturn(4321L);

        mockMvc.perform(post(REPORTS_URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":21,\"category\":\"노쇼\",\"summary\":\"내용\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(4321));

        // 신고자 번호는 요청 본문이 아니라 인증 주체에서만 넘긴다.
        verify(memberReportService).submit(7L, 10L,
                new ReportSubmitRequest(21L, "노쇼", "내용"));
    }

    @Test
    void returnsBadRequestForInvalidContent() throws Exception {
        when(memberReportService.submit(eq(7L), eq(10L), any(ReportSubmitRequest.class)))
                .thenThrow(new IllegalArgumentException("신고 내용을 입력해 주세요."));

        mockMvc.perform(post(REPORTS_URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":21,\"category\":\"노쇼\",\"summary\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("신고 내용을 입력해 주세요."));
    }

    @Test
    void returnsBadRequestForUnknownCategory() throws Exception {
        when(memberReportService.submit(eq(7L), eq(10L), any(ReportSubmitRequest.class)))
                .thenThrow(new IllegalArgumentException("신고 유형을 선택해 주세요."));

        mockMvc.perform(post(REPORTS_URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":21,\"category\":\"없는 유형\",\"summary\":\"내용\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deniesSelfTarget() throws Exception {
        when(memberReportService.submit(eq(7L), eq(10L), any(ReportSubmitRequest.class)))
                .thenThrow(new ReportAccessDeniedException("본인은 신고할 수 없습니다."));

        mockMvc.perform(post(REPORTS_URL).with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":7,\"category\":\"노쇼\",\"summary\":\"내용\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get(TARGETS_URL))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post(REPORTS_URL).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":21,\"category\":\"노쇼\",\"summary\":\"내용\"}"))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(memberReportService);
    }

    @Test
    void submitRequiresCsrf() throws Exception {
        mockMvc.perform(post(REPORTS_URL).with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetUserId\":21,\"category\":\"노쇼\",\"summary\":\"내용\"}"))
                .andExpect(status().isForbidden());

        verify(memberReportService, never()).submit(anyLong(), anyLong(), any(ReportSubmitRequest.class));
    }

    @Test
    void reportsAreAvailableToEveryAuthenticatedRole() throws Exception {
        when(memberReportService.loadTargets(anyLong(), anyLong())).thenReturn(List.of());

        // 관리자 역할도 일반 회원과 같은 신고 API를 쓸 수 있다(로그인만 요구).
        mockMvc.perform(get(TARGETS_URL).with(user(facilityManager()))).andExpect(status().isOk());
        mockMvc.perform(get(TARGETS_URL).with(user(admin()))).andExpect(status().isOk());

        verify(memberReportService, never()).submit(anyLong(), anyLong(), any(ReportSubmitRequest.class));
    }
}
