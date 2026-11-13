package co.kr.spoit.report.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.admin;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import co.kr.spoit.report.dto.FacilityReportPage;
import co.kr.spoit.report.dto.ReportDetail;
import co.kr.spoit.report.service.FacilityReportService;
import co.kr.spoit.report.service.ReportAccessDeniedException;
import co.kr.spoit.report.service.ReportNotFoundException;

/**
 * 시설 관리자 신고 라우트의 접근 권한·모델 계약·페이지 파라미터·PRG 흐름을 검증한다.
 *
 * <p>경로 접근은 실제 {@link SecurityConfig}가 역할로 막고, 컨트롤러는 인증 주체 ID만 서비스로
 * 넘긴다. 신고 대상 조건·중복 방어·페이지 정규화는 서비스 단위 테스트가 맡는다.</p>
 */
@WebMvcTest(controllers = FacilityReportController.class)
@Import(SecurityConfig.class)
class FacilityReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityReportService facilityReportService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @BeforeEach
    void stubDefaults() {
        when(facilityReportService.loadPage(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(FacilityReportPage.empty());
    }

    @Test
    @DisplayName("시설 관리자는 신고 화면을 열고 인증 주체 ID와 페이지를 서비스로 넘긴다")
    void facilityManagerOpensPage() throws Exception {
        mockMvc.perform(get("/admin/facility/reports")
                        .param("matchId", "100")
                        .param("targetUserId", "21")
                        .param("reportId", "5")
                        .param("matchPage", "2")
                        .param("reportPage", "3")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/report-facility"))
                .andExpect(model().attribute("matches", List.of()))
                .andExpect(model().attribute("reports", List.of()))
                .andExpect(model().attribute("selectedReport", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("matchPage", 0))
                .andExpect(model().attribute("hasNextMatches", false))
                .andExpect(model().attribute("reportPage", 0))
                .andExpect(model().attribute("hasNextReports", false));

        verify(facilityReportService).loadPage(7L, 100L, 21L, 5L, 2, 3);
    }

    @Test
    @DisplayName("신고 화면은 선택 경기·대상·상세와 페이지 정보를 모델 계약 이름으로 넘긴다")
    void exposesSelectionAndPageModel() throws Exception {
        ReportDetail detail = new ReportDetail(5L, 100L, "시설", "경기", "대상", null, "기타", "접수",
                "2026-11-01 10:00", "원문", null);
        when(facilityReportService.loadPage(7L, 100L, 21L, 5L, 1, 2)).thenReturn(new FacilityReportPage(
                List.of(), List.of(), List.of(), 100L, 21L, detail, 1, true, 2, true));

        mockMvc.perform(get("/admin/facility/reports")
                        .param("matchId", "100")
                        .param("targetUserId", "21")
                        .param("reportId", "5")
                        .param("matchPage", "1")
                        .param("reportPage", "2")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(model().attribute("selectedMatchId", 100L))
                .andExpect(model().attribute("selectedTargetUserId", 21L))
                .andExpect(model().attribute("selectedReport",
                        org.hamcrest.Matchers.hasProperty("reportId", org.hamcrest.Matchers.is(5L))))
                .andExpect(model().attribute("matchPage", 1))
                .andExpect(model().attribute("hasNextMatches", true))
                .andExpect(model().attribute("reportPage", 2))
                .andExpect(model().attribute("hasNextReports", true));
    }

    @Test
    @DisplayName("잘못된 페이지 파라미터는 0으로 정규화해 서비스에 넘긴다")
    void normalizesInvalidPageParams() throws Exception {
        mockMvc.perform(get("/admin/facility/reports")
                        .param("matchPage", "-4")
                        .param("reportPage", "abc")
                        .with(user(facilityManager())))
                .andExpect(status().isOk());

        mockMvc.perform(get("/admin/facility/reports")
                        .param("matchPage", "999999999999")
                        .param("reportPage", "")
                        .with(user(facilityManager())))
                .andExpect(status().isOk());

        verify(facilityReportService).loadPage(7L, null, null, null, 0, 0);
        // int 범위를 넘는 값은 정수 상한으로 자른 뒤 서비스가 안전 상한으로 정규화한다.
        verify(facilityReportService).loadPage(7L, null, null, null, Integer.MAX_VALUE, 0);
    }

    @Test
    @DisplayName("시스템 관리자·일반 회원은 시설 신고 화면에 접근할 수 없다")
    void nonFacilityRolesForbidden() throws Exception {
        mockMvc.perform(get("/admin/facility/reports").with(user(admin())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/facility/reports").with(user(member())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(facilityReportService);
    }

    @Test
    @DisplayName("비로그인 요청은 로그인 화면으로 보낸다")
    void anonymousRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/facility/reports"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("신고 접수 성공은 생성 번호·페이지를 보존해 본인 상세로 이동하고 인증 주체 ID를 쓴다")
    void submitsWithPrg() throws Exception {
        when(facilityReportService.submit(7L, 100L, 21L, "노쇼", "내용")).thenReturn(4321L);

        mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", "100")
                        .param("targetUserId", "21")
                        .param("category", "노쇼")
                        .param("summary", "내용")
                        .param("matchPage", "2")
                        .param("reportPage", "3")
                        .with(csrf())
                        .with(user(facilityManager())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/facility/reports?reportId=4321&matchPage=2&reportPage=3#report-selected"))
                .andExpect(flash().attribute("message", "신고가 접수되었습니다."));

        verify(facilityReportService).submit(7L, 100L, 21L, "노쇼", "내용");
    }

    @Test
    @DisplayName("접수 실패는 대상·페이지를 redirect query로 보존하고 원문·유형은 길이 제한 flash로만 남긴다")
    void accessDeniedPreservesInputAndScope() throws Exception {
        when(facilityReportService.submit(7L, 100L, 21L, "노쇼", "작성 중이던 내용"))
                .thenThrow(new ReportAccessDeniedException("이용이 끝난 경기만 신고할 수 있습니다."));

        mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", "100")
                        .param("targetUserId", "21")
                        .param("category", "노쇼")
                        .param("summary", "작성 중이던 내용")
                        .param("matchPage", "2")
                        .param("reportPage", "3")
                        .with(csrf())
                        .with(user(facilityManager())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/facility/reports?matchId=100&targetUserId=21&matchPage=2&reportPage=3"))
                .andExpect(flash().attribute("errorTitle", "신고를 접수할 수 없습니다"))
                // 작성 내용을 양식에 되돌려 주므로 실패 안내에 그 사실을 덧붙인다.
                .andExpect(flash().attribute("error",
                        "이용이 끝난 경기만 신고할 수 있습니다.\n" + FacilityReportController.KEPT_INPUT_NOTICE))
                .andExpect(flash().attribute("submittedSummary", "작성 중이던 내용"))
                .andExpect(flash().attribute("submittedCategory", "노쇼"));
        // 원문은 URL로 흘러가지 않는다.
        mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", "100")
                        .param("targetUserId", "21")
                        .param("category", "노쇼")
                        .param("summary", "작성 중이던 내용")
                        .with(csrf())
                        .with(user(facilityManager())))
                .andExpect(redirectedUrl("/admin/facility/reports?matchId=100&targetUserId=21"
                        + "&matchPage=0&reportPage=0"));
    }

    @Test
    @DisplayName("실패 시 허용되지 않은 유형 값은 화면에 반사하지 않는다")
    void unknownCategoryIsNotReflected() throws Exception {
        when(facilityReportService.submit(anyLong(), anyLong(), anyLong(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("신고 유형을 선택해 주세요."));

        mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", "100")
                        .param("targetUserId", "21")
                        .param("category", "<script>alert(1)</script>")
                        .param("summary", "내용")
                        .with(csrf())
                        .with(user(facilityManager())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("submittedCategory", org.hamcrest.Matchers.nullValue()))
                .andExpect(flash().attribute("submittedSummary", "내용"));
    }

    @Test
    @DisplayName("실패 시 원문은 입력 상한으로 잘라 flash에 보존하고 빈 값은 넣지 않는다")
    void limitsPreservedSummary() throws Exception {
        String overLimit = "가".repeat(co.kr.spoit.report.service.FacilityReportService.SUMMARY_MAX_LENGTH + 50);
        when(facilityReportService.submit(anyLong(), anyLong(), anyLong(), anyString(), anyString()))
                .thenThrow(new ReportAccessDeniedException("담당 시설의 경기만 신고할 수 있습니다."));

        mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", "100")
                        .param("targetUserId", "21")
                        .param("category", "")
                        .param("summary", overLimit)
                        .with(csrf())
                        .with(user(facilityManager())))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("submittedSummary",
                        "가".repeat(co.kr.spoit.report.service.FacilityReportService.SUMMARY_MAX_LENGTH)))
                .andExpect(flash().attribute("submittedCategory", org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("신고 접수 POST는 CSRF 토큰이 없으면 거부한다")
    void submitRequiresCsrf() throws Exception {
        mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", "100")
                        .param("targetUserId", "21")
                        .param("category", "노쇼")
                        .param("summary", "내용")
                        .with(user(facilityManager())))
                .andExpect(status().isForbidden());

        verify(facilityReportService, never()).submit(anyLong(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("시스템 관리자는 시설 신고 접수 POST를 호출할 수 없다")
    void adminCannotSubmitFacilityReport() throws Exception {
        mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", "100")
                        .param("targetUserId", "21")
                        .param("category", "노쇼")
                        .param("summary", "내용")
                        .with(csrf())
                        .with(user(admin())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(facilityReportService);
    }

    @Test
    @DisplayName("권한 밖 상세 조회는 404로 응답한다")
    void outOfScopeDetailIsNotFound() throws Exception {
        when(facilityReportService.loadPage(7L, null, null, 999L, 0, 0))
                .thenThrow(new ReportNotFoundException("신고를 찾을 수 없습니다."));

        mockMvc.perform(get("/admin/facility/reports")
                        .param("reportId", "999")
                        .with(user(facilityManager())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("쿼리 파라미터가 없으면 모두 null·0으로 서비스에 넘긴다")
    void passesNullSelection() throws Exception {
        mockMvc.perform(get("/admin/facility/reports").with(user(facilityManager())))
                .andExpect(status().isOk());

        verify(facilityReportService).loadPage(eq(7L), isNull(), isNull(), isNull(), eq(0), eq(0));
    }
}
