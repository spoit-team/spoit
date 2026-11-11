package co.kr.spoit.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.notification.service.ReportNotificationService;
import co.kr.spoit.report.domain.Report;
import co.kr.spoit.report.domain.ReportRow;
import co.kr.spoit.report.dto.SystemReportPage;
import co.kr.spoit.report.mapper.ReportMapper;

/**
 * 시스템 관리자 신고 처리의 허용 전이·상태 경합·알림 시점과 offset 페이지를 검증한다.
 *
 * <p>검토 중은 접수에서만, 처리 완료·기각은 접수·검토 중에서만 가능하다. 이미 끝난 건은 재처리하지
 * 않고, 상태 조건부 갱신이 0행이면 경합으로 안내한다. 결과 알림은 상태가 실제로 바뀐 최종 처리에서만
 * 신고자에게 간다. 목록은 0부터 시작하는 offset 페이지이며 잘못된 페이지 값은 정규화된다.</p>
 */
class SystemReportServiceTest {

    private static final long REPORT_ID = 15L;
    private static final long REPORTER_ID = 7L;
    private static final long TARGET_ID = 11L;
    private static final long ADMIN_ID = 3L;

    private final ReportMapper mapper = mock(ReportMapper.class);
    private final ReportNotificationService notification = mock(ReportNotificationService.class);
    private final PenaltyService penaltyService = mock(PenaltyService.class);
    private final SystemReportService service = new SystemReportService(mapper, notification, penaltyService);

    @BeforeEach
    void stubDefaults() {
        when(mapper.markReviewing(anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);
        when(mapper.applyFinalDecision(anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(1);
        when(mapper.findSystemReports(any(), anyInt(), anyInt())).thenReturn(List.of());
    }

    @Test
    @DisplayName("접수 신고를 검토 중으로 옮기고 결과 알림은 보내지 않는다")
    void reviewsReceivedReport() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("접수"));

        service.decide(REPORT_ID, "review", "접수", null, ADMIN_ID);

        verify(mapper).markReviewing(REPORT_ID, "접수", "검토 중", ADMIN_ID);
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("검토 중 신고를 처리 완료로 마무리하고 사유를 남기며 신고자에게 결과를 알린다")
    void completesReviewingReport() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("검토 중"));

        service.decide(REPORT_ID, "complete", "검토 중", "  확인 결과 제재 사유에 해당합니다.  ", ADMIN_ID);

        verify(mapper).applyFinalDecision(REPORT_ID, "검토 중", "처리 완료",
                "확인 결과 제재 사유에 해당합니다.", ADMIN_ID);
        verify(notification).notifyResolved(REPORT_ID, REPORTER_ID, "처리 완료");
    }

    @Test
    @DisplayName("접수 상태에서도 기각할 수 있고 기각 사유가 저장된다")
    void dismissesFromReceived() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("접수"));

        service.decide(REPORT_ID, "dismiss", "접수", "사실 확인이 되지 않았습니다.", ADMIN_ID);

        verify(mapper).applyFinalDecision(REPORT_ID, "접수", "기각", "사실 확인이 되지 않았습니다.", ADMIN_ID);
        verify(notification).notifyResolved(REPORT_ID, REPORTER_ID, "기각");
    }

    @Test
    @DisplayName("화면이 본 상태와 실제 상태가 다르면 경합으로 거부하고 아무것도 바꾸지 않는다")
    void rejectsStaleExpectedStatus() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("검토 중"));

        assertThatThrownBy(() -> service.decide(REPORT_ID, "complete", "접수", "사유", ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class)
                .hasMessageContaining("상태가 바뀌었습니다");

        verify(mapper, never()).applyFinalDecision(anyLong(), anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("이미 처리 완료된 신고는 재처리하지 않고 알림도 보내지 않는다")
    void rejectsReprocessingFinishedReport() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("처리 완료"));

        assertThatThrownBy(() -> service.decide(REPORT_ID, "complete", "처리 완료", "사유", ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class)
                .hasMessageContaining("이미 처리가 끝난");

        assertThatThrownBy(() -> service.decide(REPORT_ID, "review", "처리 완료", null, ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class);

        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("검토 중 상태를 다시 검토 중으로 옮길 수 없다")
    void rejectsReviewFromReviewing() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("검토 중"));

        assertThatThrownBy(() -> service.decide(REPORT_ID, "review", "검토 중", null, ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class);
    }

    @Test
    @DisplayName("조건부 갱신이 0행이면 다른 관리자의 동시 처리로 보고 경합 안내를 낸다")
    void treatsZeroRowUpdateAsConflict() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("검토 중"));
        when(mapper.applyFinalDecision(REPORT_ID, "검토 중", "처리 완료", "사유", ADMIN_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.decide(REPORT_ID, "complete", "검토 중", "사유", ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class);

        verify(notification, never()).notifyResolved(anyLong(), anyLong(), anyString());
    }

    @Test
    @DisplayName("처리 완료·기각에 사유가 없거나 255자를 넘으면 저장 전에 거부한다")
    void rejectsInvalidResolution() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("접수"));

        assertThatThrownBy(() -> service.decide(REPORT_ID, "complete", "접수", "   ", ADMIN_ID))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.decide(REPORT_ID, "dismiss", "접수", "가".repeat(256), ADMIN_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mapper, never()).applyFinalDecision(anyLong(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("알 수 없는 기존 상태 레코드는 임의 변환 없이 처리만 거부한다")
    void unknownLegacyStatusIsNotConverted() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("보류"));

        assertThatThrownBy(() -> service.decide(REPORT_ID, "review", "접수", null, ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class)
                .hasMessageContaining("처리할 수 없습니다");

        verify(mapper, never()).markReviewing(anyLong(), anyString(), anyString(), anyLong());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("알 수 없는 동작 값은 거부한다")
    void rejectsUnknownAction() {
        assertThatThrownBy(() -> service.decide(REPORT_ID, "delete", "접수", null, ADMIN_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("목록은 원문 없이 pageSize+1 offset 페이지로 조회하고 지정 상태 필터를 넘긴다")
    void loadsListWithStatusFilter() {
        ReportRow row = new ReportRow();
        row.setReportId(1L);
        row.setReporterName("신고자");
        row.setStatus("접수");
        when(mapper.findSystemReports("접수", SystemReportService.PAGE_SIZE + 1, 0)).thenReturn(List.of(row));

        SystemReportPage page = service.loadPage("접수", null, 0);

        assertThat(page.getReports()).hasSize(1);
        assertThat(page.getReports().get(0).getReporterName()).isEqualTo("신고자");
        assertThat(page.getStatusFilter()).isEqualTo("접수");
        assertThat(page.getSelectedReport()).isNull();
        assertThat(page.getReportPage()).isZero();
        assertThat(page.isHasNextReports()).isFalse();
    }

    @Test
    @DisplayName("pageSize+1건이면 다음 페이지가 있다고 표시하고 pageSize만 노출한다")
    void exposesHasNextReportPage() {
        List<ReportRow> rows = new ArrayList<>();
        for (int i = 0; i <= SystemReportService.PAGE_SIZE; i++) {
            ReportRow row = new ReportRow();
            row.setReportId((long) i);
            rows.add(row);
        }
        when(mapper.findSystemReports(null, SystemReportService.PAGE_SIZE + 1,
                SystemReportService.PAGE_SIZE)).thenReturn(rows);

        SystemReportPage page = service.loadPage(null, null, 1);

        assertThat(page.getReports()).hasSize(SystemReportService.PAGE_SIZE);
        assertThat(page.getReportPage()).isEqualTo(1);
        assertThat(page.isHasNextReports()).isTrue();
    }

    @Test
    @DisplayName("음수 페이지는 0으로, 과대 페이지는 안전 상한으로 정규화한다")
    void normalizesUnsafePage() {
        when(mapper.findSystemReports((String) isNull(), eq(SystemReportService.PAGE_SIZE + 1),
                eq(SystemReportService.PAGE_SIZE * SystemReportService.MAX_PAGE))).thenReturn(List.of());

        SystemReportPage negative = service.loadPage(null, null, -3);
        SystemReportPage huge = service.loadPage(null, null, Integer.MAX_VALUE);

        assertThat(negative.getReportPage()).isZero();
        assertThat(huge.getReportPage()).isEqualTo(SystemReportService.MAX_PAGE);
        verify(mapper).findSystemReports((String) isNull(), eq(SystemReportService.PAGE_SIZE + 1), eq(0));
    }

    @Test
    @DisplayName("알 수 없는 상태 필터는 전체 조회로 되돌린다")
    void unknownFilterFallsBackToAll() {
        when(mapper.findSystemReports((String) isNull(), eq(SystemReportService.PAGE_SIZE + 1), eq(0)))
                .thenReturn(List.of());

        SystemReportPage page = service.loadPage("알수없음", null, 0);

        assertThat(page.getStatusFilter()).isNull();
        verify(mapper).findSystemReports((String) isNull(), eq(SystemReportService.PAGE_SIZE + 1), eq(0));
    }

    @Test
    @DisplayName("상세는 요청한 한 건만 원문·처리 사유와 함께 조회한다")
    void loadsSingleDetail() {
        ReportRow row = new ReportRow();
        row.setReportId(REPORT_ID);
        row.setSummary("신고 원문");
        row.setResolution("처리 결과");
        row.setCreatedAt(LocalDateTime.of(2026, 11, 1, 9, 0));
        when(mapper.findSystemReport(REPORT_ID)).thenReturn(row);

        SystemReportPage page = service.loadPage(null, REPORT_ID, 0);

        assertThat(page.getSelectedReport().getSummary()).isEqualTo("신고 원문");
        assertThat(page.getSelectedReport().getResolution()).isEqualTo("처리 결과");
        assertThat(page.getSelectedReport().getCreatedAt()).isEqualTo("2026-11-01 09:00");
        verify(mapper).findSystemReport(REPORT_ID);
    }

    @Test
    @DisplayName("없는 신고 번호의 상세는 404 예외로 처리한다")
    void missingDetailIsNotFound() {
        when(mapper.findSystemReport(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.loadPage(null, 999L, 0))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    @DisplayName("신고 상세에서 제재를 적용하면 대상 회원은 신고 행에서 가져와 처리 완료로 마무리한다")
    void appliesPenaltyUsingServerTarget() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("검토 중"));
        when(mapper.applyFinalDecision(REPORT_ID, "검토 중", "처리 완료", "반복 위반 확인", ADMIN_ID))
                .thenReturn(1);

        service.applyPenalty(REPORT_ID, "이용 제한", 7, "반복 위반 확인", "검토 중", null, ADMIN_ID);

        // 대상 회원은 클라이언트 값이 아니라 신고 행의 target_user_id에서 온다.
        verify(penaltyService).issueForReport(REPORT_ID, TARGET_ID, ADMIN_ID, "이용 제한", 7, "반복 위반 확인");
        verify(mapper).applyFinalDecision(REPORT_ID, "검토 중", "처리 완료", "반복 위반 확인", ADMIN_ID);
        verify(notification).notifyResolved(REPORT_ID, REPORTER_ID, "처리 완료");
    }

    @Test
    @DisplayName("제재 적용에서 처리 사유를 비우면 제재 사유를 신고 처리 사유로 쓴다")
    void penaltyFallsBackToReasonAsResolution() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("접수"));
        when(mapper.applyFinalDecision(REPORT_ID, "접수", "처리 완료", "경고 사유", ADMIN_ID)).thenReturn(1);

        service.applyPenalty(REPORT_ID, "경고", null, "경고 사유", "접수", "   ", ADMIN_ID);

        verify(mapper).applyFinalDecision(REPORT_ID, "접수", "처리 완료", "경고 사유", ADMIN_ID);
    }

    @Test
    @DisplayName("이미 끝난 신고에는 제재를 적용하지 않는다")
    void rejectsPenaltyOnFinishedReport() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("처리 완료"));

        assertThatThrownBy(() -> service.applyPenalty(REPORT_ID, "경고", null, "사유", "처리 완료", null, ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class);

        verify(penaltyService, never()).issueForReport(anyLong(), anyLong(), anyLong(), any(), any(), any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("화면이 보낸 알 수 없는 기대 상태는 생략으로 취급하지 않고 경합으로 거부한다")
    void rejectsMalformedExpectedStatusForPenalty() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("검토 중"));

        assertThatThrownBy(() -> service.applyPenalty(REPORT_ID, "경고", null, "사유", "보류", null, ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class)
                .hasMessageContaining("신고 상태를 확인할 수 없습니다");

        verify(mapper, never()).findReportForUpdate(anyLong());
        verify(penaltyService, never()).issueForReport(anyLong(), anyLong(), anyLong(), any(), any(), any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("잠근 신고의 실제 상태가 기대 상태와 다르면 제재를 적용하지 않는다")
    void rejectsStaleExpectedStatusForPenalty() {
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("검토 중"));

        assertThatThrownBy(() -> service.applyPenalty(REPORT_ID, "경고", null, "사유", "접수", null, ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class)
                .hasMessageContaining("상태가 바뀌었습니다");

        verify(penaltyService, never()).issueForReport(anyLong(), anyLong(), anyLong(), any(), any(), any());
        verify(mapper, never()).applyFinalDecision(anyLong(), anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("잠근 신고에 대상 회원이 없으면 제재 발급·처리 완료·알림 전에 거부한다")
    void rejectsPenaltyWithoutTargetUser() {
        Report report = report("검토 중");
        report.setTargetUserId(null);
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report);

        assertThatThrownBy(() -> service.applyPenalty(REPORT_ID, "경고", null, "사유", "검토 중", null, ADMIN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("신고 대상 회원을 확인할 수 없습니다");

        verify(penaltyService, never()).issueForReport(anyLong(), anyLong(), anyLong(), any(), any(), any());
        verify(mapper, never()).applyFinalDecision(anyLong(), anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("처리 사유가 비어 있고 제재 사유가 255자를 넘으면 255자로 잘라 처리 사유로 저장한다")
    void truncatesLongReasonWhenResolutionBlank() {
        String longReason = "가".repeat(300);
        when(mapper.findReportForUpdate(REPORT_ID)).thenReturn(report("검토 중"));

        service.applyPenalty(REPORT_ID, "경고", null, longReason, "검토 중", "   ", ADMIN_ID);

        verify(penaltyService).issueForReport(REPORT_ID, TARGET_ID, ADMIN_ID, "경고", null, longReason);
        verify(mapper).applyFinalDecision(REPORT_ID, "검토 중", "처리 완료", "가".repeat(255), ADMIN_ID);
        verify(notification).notifyResolved(REPORT_ID, REPORTER_ID, "처리 완료");
    }

    @Test
    @DisplayName("상세 단독 조회는 목록 조회 없이 한 건만 읽고, 없으면 404 예외다")
    void loadsDetailOnly() {
        ReportRow row = new ReportRow();
        row.setReportId(REPORT_ID);
        row.setTargetUserId(TARGET_ID);
        row.setSummary("원문");
        when(mapper.findSystemReport(REPORT_ID)).thenReturn(row);

        assertThat(service.loadDetail(REPORT_ID).getTargetUserId()).isEqualTo(TARGET_ID);
        verify(mapper, never()).findSystemReports(any(), anyInt(), anyInt());

        when(mapper.findSystemReport(999L)).thenReturn(null);
        assertThatThrownBy(() -> service.loadDetail(999L)).isInstanceOf(ReportNotFoundException.class);
    }

    private static Report report(String status) {
        Report report = new Report();
        report.setReportId(REPORT_ID);
        report.setReporterUserId(REPORTER_ID);
        report.setTargetUserId(TARGET_ID);
        report.setStatus(status);
        return report;
    }
}
