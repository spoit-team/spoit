package co.kr.spoit.report.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.admin;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import co.kr.spoit.report.dto.ReportDetail;
import co.kr.spoit.report.dto.SystemReportPage;
import co.kr.spoit.report.service.ReportStateConflictException;
import co.kr.spoit.report.service.SystemReportService;

/**
 * 시스템 관리자 신고 접수함 라우트의 접근 권한·모델 계약·페이지 파라미터·결정 PRG 흐름을 검증한다.
 *
 * <p>경로 접근은 실제 {@link SecurityConfig}가 {@code ROLE_ADMIN}으로 막고, 컨트롤러는 인증 주체
 * ID만 서비스로 넘긴다. 상태 전이·경합·알림 시점·페이지 정규화는 서비스 단위 테스트가 맡는다.</p>
 */
@WebMvcTest(controllers = SystemReportController.class)
@Import(SecurityConfig.class)
class SystemReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemReportService systemReportService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @BeforeEach
    void stubDefaults() {
        when(systemReportService.loadPage(any(), any(), anyInt())).thenReturn(SystemReportPage.empty());
    }

    @Test
    @DisplayName("시스템 관리자는 접수함을 열고 상태 필터·선택 신고·페이지를 서비스로 넘긴다")
    void adminOpensPage() throws Exception {
        mockMvc.perform(get("/admin/system/reports")
                        .param("status", "접수")
                        .param("reportId", "5")
                        .param("page", "2")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/report-system"))
                .andExpect(model().attribute("reports", List.of()))
                .andExpect(model().attribute("selectedReport", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("reportPage", 0))
                .andExpect(model().attribute("hasNextReports", false));

        verify(systemReportService).loadPage("접수", 5L, 2);
    }

    @Test
    @DisplayName("상세는 요청한 한 건만, 페이지 정보는 서비스 결과 그대로 모델에 실린다")
    void exposesSingleDetailAndPage() throws Exception {
        ReportDetail detail = new ReportDetail(5L, 100L, "시설", "경기", "대상", "신고자", "기타", "접수",
                "2026-11-01 10:00", "원문", null);
        when(systemReportService.loadPage("접수", 5L, 1))
                .thenReturn(new SystemReportPage(List.of(), detail, "접수", 1, true));

        mockMvc.perform(get("/admin/system/reports")
                        .param("status", "접수")
                        .param("reportId", "5")
                        .param("page", "1")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedReport",
                        org.hamcrest.Matchers.hasProperty("summary", org.hamcrest.Matchers.is("원문"))))
                .andExpect(model().attribute("statusFilter", "접수"))
                .andExpect(model().attribute("reportPage", 1))
                .andExpect(model().attribute("hasNextReports", true));
    }

    @Test
    @DisplayName("잘못된 page 파라미터는 0으로 정규화한다")
    void normalizesInvalidPage() throws Exception {
        mockMvc.perform(get("/admin/system/reports")
                        .param("page", "-7")
                        .with(user(admin())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/system/reports")
                        .param("page", "not-a-number")
                        .with(user(admin())))
                .andExpect(status().isOk());

        verify(systemReportService, org.mockito.Mockito.times(2)).loadPage(null, null, 0);
    }

    @Test
    @DisplayName("시설 관리자·일반 회원은 시스템 접수함에 접근할 수 없다")
    void nonAdminRolesForbidden() throws Exception {
        mockMvc.perform(get("/admin/system/reports").with(user(facilityManager())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/system/reports").with(user(member())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(systemReportService);
    }

    @Test
    @DisplayName("결정 POST는 PRG로 필터·페이지·선택을 보존하고 인증 주체 ID를 쓴다")
    void decidesWithPrg() throws Exception {
        mockMvc.perform(post("/admin/system/reports/15/decision")
                        .param("action", "complete")
                        .param("expectedStatus", "검토 중")
                        .param("resolution", "처리 사유")
                        .param("status", "검토 중")
                        .param("page", "2")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(
                        "/admin/system/reports?status=*&page=2&reportId=15#report-case"))
                .andExpect(flash().attribute("message", "신고를 처리 완료로 마무리했습니다."));

        verify(systemReportService).decide(15L, "complete", "검토 중", "처리 사유", 7L);
    }

    @Test
    @DisplayName("0 페이지는 리다이렉트 쿼리에 page를 싣지 않는다")
    void zeroPageIsOmitted() throws Exception {
        mockMvc.perform(post("/admin/system/reports/15/decision")
                        .param("action", "review")
                        .param("expectedStatus", "접수")
                        .param("page", "0")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/system/reports?reportId=15#report-case"));
    }

    @Test
    @DisplayName("상태 경합은 오류 안내로 되돌리고 저장하지 않는다")
    void stateConflictBecomesFlashError() throws Exception {
        doThrow(new ReportStateConflictException("다른 관리자가 먼저 처리했습니다."))
                .when(systemReportService).decide(anyLong(), any(), any(), any(), anyLong());

        mockMvc.perform(post("/admin/system/reports/15/decision")
                        .param("action", "review")
                        .param("expectedStatus", "접수")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "다른 관리자가 먼저 처리했습니다."));
    }

    @Test
    @DisplayName("결정 POST는 CSRF 토큰이 없으면 거부한다")
    void decisionRequiresCsrf() throws Exception {
        mockMvc.perform(post("/admin/system/reports/15/decision")
                        .param("action", "review")
                        .param("expectedStatus", "접수")
                        .with(user(admin())))
                .andExpect(status().isForbidden());

        verify(systemReportService, never()).decide(anyLong(), any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("시설 관리자는 시스템 신고 결정 POST를 호출할 수 없다")
    void facilityManagerCannotDecide() throws Exception {
        mockMvc.perform(post("/admin/system/reports/15/decision")
                        .param("action", "review")
                        .param("expectedStatus", "접수")
                        .with(csrf())
                        .with(user(facilityManager())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(systemReportService);
    }

    @Test
    @DisplayName("선택한 신고가 없으면 상세 모델은 null이다")
    void noSelectionMeansNullDetail() throws Exception {
        mockMvc.perform(get("/admin/system/reports").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedReport", org.hamcrest.Matchers.nullValue()));

        verify(systemReportService).loadPage(isNull(), isNull(), eq(0));
    }
}
