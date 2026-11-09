package co.kr.spoit.report.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.admin;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;

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
import co.kr.spoit.report.dto.ReportListItem;
import co.kr.spoit.report.dto.ReportMatchOption;
import co.kr.spoit.report.dto.ReportParticipantOption;
import co.kr.spoit.report.dto.SystemReportPage;
import co.kr.spoit.report.service.FacilityReportService;
import co.kr.spoit.report.service.SystemReportService;

/**
 * 신고 화면이 실제 Thymeleaf 템플릿에서 모델 계약대로 렌더링되는지 검증한다.
 *
 * <p>UI 고정 계약: 목록에는 신고 원문이 없고, 선택한 한 건의 원문·처리 사유만 상세에 나온다.
 * 원문은 항상 {@code th:text}로만 출력되며 스크립트 데이터 속성으로 넘기지 않는다.</p>
 */
@WebMvcTest(controllers = {FacilityReportController.class, SystemReportController.class})
@Import(SecurityConfig.class)
class ReportPageRenderTest {

    private static final String SENSITIVE_SUMMARY = "민감한신고원문XYZ";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityReportService facilityReportService;

    @MockitoBean
    private SystemReportService systemReportService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("시설 신고 화면은 경기·참가자·신고 목록을 모델 그대로 렌더하고 원문은 목록에 없다")
    void rendersFacilityReportScreen() throws Exception {
        when(facilityReportService.loadPage(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(
                new FacilityReportPage(
                        List.of(new ReportMatchOption(100L, "테스트 시설", "테스트 경기 경기",
                                LocalDateTime.of(2026, 11, 1, 10, 0))),
                        List.of(new ReportParticipantOption(21L, "대상 참가자")),
                        List.of(new ReportListItem(5L, 100L, "테스트 시설", "테스트 경기", "대상 참가자", null,
                                "노쇼", "접수", "2026-11-01 10:00", null)),
                        100L, 21L,
                        new ReportDetail(5L, 100L, "테스트 시설", "테스트 경기", "대상 참가자", null, "노쇼",
                                "접수", "2026-11-01 10:00", SENSITIVE_SUMMARY, "처리 결과 문구"),
                        0, true, 0, false));

        String html = mockMvc.perform(get("/admin/facility/reports")
                        .param("matchId", "100")
                        .param("targetUserId", "21")
                        .param("reportId", "5")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/report-facility"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(html).contains("테스트 경기 경기")
                .contains("대상 참가자")
                .contains("노쇼")
                .contains("처리 결과 문구")
                // 선택한 상세 한 건에만 원문이 나온다.
                .contains(SENSITIVE_SUMMARY)
                .doesNotContain("th:utext");
    }

    @Test
    @DisplayName("시스템 접수함은 목록에서 원문을 숨기고 상세에서만 원문·사유를 보여 준다")
    void rendersSystemReportScreen() throws Exception {
        when(systemReportService.loadPage(any(), any(), anyInt())).thenReturn(new SystemReportPage(
                List.of(new ReportListItem(5L, 100L, "테스트 시설", "테스트 경기", "대상 참가자", "신고 관리자",
                        "기타", "접수", "2026-11-01 10:00", null)),
                new ReportDetail(5L, 100L, "테스트 시설", "테스트 경기", "대상 참가자", "신고 관리자", "기타",
                        "접수", "2026-11-01 10:00", SENSITIVE_SUMMARY, "처리 결과 문구"),
                "접수", 0, false));

        String html = mockMvc.perform(get("/admin/system/reports")
                        .param("status", "접수")
                        .param("reportId", "5")
                        .with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/report-system"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(html).contains("신고 관리자")
                .contains("처리 결과 문구")
                .contains(SENSITIVE_SUMMARY)
                // 신고 원문은 스크립트가 읽는 data 속성으로 내려가지 않는다.
                .doesNotContain("data-report-summary")
                .doesNotContain("data-report-detail")
                .doesNotContain("data-selected-report");
    }

    @Test
    @DisplayName("선택한 신고가 없으면 상세 영역과 원문 없이 목록만 렌더한다")
    void rendersWithoutSelectedReport() throws Exception {
        when(systemReportService.loadPage(any(), any(), anyInt()))
                .thenReturn(new SystemReportPage(List.of(), null, null, 0, false));

        String html = mockMvc.perform(get("/admin/system/reports").with(user(admin())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(html).doesNotContain("report-case-title")
                .doesNotContain(SENSITIVE_SUMMARY);
    }
}
