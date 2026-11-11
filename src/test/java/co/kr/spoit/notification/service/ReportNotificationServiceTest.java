package co.kr.spoit.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.kr.spoit.notification.domain.NotificationType;
import co.kr.spoit.notification.dto.SystemAdminCandidate;
import co.kr.spoit.notification.mapper.NotificationMapper;

/**
 * 신고 접수·처리 결과 알림의 수신자 범위와 문구 안전성을 검증한다.
 *
 * <p>접수 알림은 활성 시스템 관리자에게만, 결과 알림은 신고자에게만 간다. 수신자 판정은 DB 후보를
 * {@code MemberRole.from}의 정확 일치 규칙으로 다시 거르므로 부분 일치·알 수 없는 역할이 관리자로
 * 승격되지 않는다. 저장·커밋 후 전송 경계는 기존 Dispatcher가 그대로 맡는다.</p>
 */
class ReportNotificationServiceTest {

    private static final long REPORT_ID = 15L;
    private static final long REPORTER_ID = 7L;

    private final NotificationMapper mapper = mock(NotificationMapper.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final ReportNotificationService service = new ReportNotificationService(mapper, dispatcher);

    @BeforeEach
    void setUp() {
        when(mapper.findActiveAdminCandidates()).thenReturn(List.of());
    }

    @Test
    @DisplayName("접수 알림은 정확히 시스템 관리자로 해석되는 활성 회원에게만 발송한다")
    void notifySubmittedSendsOnlyToSystemAdmins() {
        when(mapper.findActiveAdminCandidates()).thenReturn(Arrays.asList(
                candidate(3L, "시스템 관리자"),
                candidate(4L, "ROLE_ADMIN"),
                candidate(5L, " admin "),
                candidate(6L, "시설 관리자"),
                candidate(7L, "일반 회원"),
                candidate(8L, "NOT_ADMIN"),
                candidate(9L, "SUPER_ADMIN"),
                candidate(10L, null),
                null,
                candidate(null, "시스템 관리자"),
                candidate(0L, "ADMIN")));

        service.notifySubmitted(REPORT_ID);

        verify(dispatcher).notifyAfterCommit(eq(List.of(3L, 4L, 5L)),
                eq(NotificationType.REPORT_SUBMITTED.name()), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull(), eq(REPORT_ID));
    }

    @Test
    @DisplayName("접수 알림 제목·본문에는 신고 원문·대상·사유 같은 민감정보를 넣지 않는다")
    void notifySubmittedHasNoSensitiveContent() {
        when(mapper.findActiveAdminCandidates()).thenReturn(List.of(candidate(3L, "ADMIN")));

        service.notifySubmitted(REPORT_ID);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).notifyAfterCommit(anyList(), eq(NotificationType.REPORT_SUBMITTED.name()),
                title.capture(), body.capture(), isNull(), isNull(), isNull(), isNull(), eq(REPORT_ID));

        assertThat(title.getValue()).doesNotContain(String.valueOf(REPORT_ID));
        assertThat(title.getValue()).doesNotContain("욕설", "신고자", "대상");
        assertThat(body.getValue()).doesNotContain(String.valueOf(REPORT_ID));
        assertThat(body.getValue()).doesNotContain("욕설", "신고자", "대상");
    }

    @Test
    @DisplayName("활성 시스템 관리자가 없으면 접수 알림을 저장하지 않는다")
    void notifySubmittedSkipsWithoutActiveAdmin() {
        service.notifySubmitted(REPORT_ID);

        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("신고 번호가 없거나 0 이하이면 접수·결과 알림을 만들지 않는다")
    void skipsInvalidReportId() {
        service.notifySubmitted(null);
        service.notifySubmitted(0L);
        service.notifySubmitted(-5L);
        service.notifyResolved(null, REPORTER_ID, "처리 완료");
        service.notifyResolved(0L, REPORTER_ID, "처리 완료");
        service.notifyResolved(-1L, REPORTER_ID, "처리 완료");

        verifyNoInteractions(dispatcher);
    }

    @Test
    @DisplayName("처리 결과 알림은 신고자 한 명에게만 발송하고 관리자 후보를 조회하지 않는다")
    void notifyResolvedSendsOnlyToReporter() {
        service.notifyResolved(REPORT_ID, REPORTER_ID, "처리 완료");

        verify(dispatcher).notifyAfterCommit(eq(List.of(REPORTER_ID)),
                eq(NotificationType.REPORT_RESOLVED.name()), anyString(), anyString(),
                isNull(), isNull(), isNull(), isNull(), eq(REPORT_ID));
        verify(mapper, never()).findActiveAdminCandidates();
    }

    @Test
    @DisplayName("결과 알림은 상태 원문을 그대로 옮기지 않고 고정 문구만 쓴다")
    void notifyResolvedDoesNotEchoStatusText() {
        service.notifyResolved(REPORT_ID, REPORTER_ID, "SENSITIVE_RESOLUTION_TEXT");

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).notifyAfterCommit(eq(List.of(REPORTER_ID)),
                eq(NotificationType.REPORT_RESOLVED.name()), title.capture(), body.capture(),
                isNull(), isNull(), isNull(), isNull(), eq(REPORT_ID));

        assertThat(title.getValue()).doesNotContain("SENSITIVE_RESOLUTION_TEXT", String.valueOf(REPORT_ID));
        assertThat(body.getValue()).doesNotContain("SENSITIVE_RESOLUTION_TEXT", String.valueOf(REPORT_ID));
        assertThat(title.getValue()).isNotBlank();
        assertThat(body.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("기각 결과도 신고자에게만 가고 신고 번호·사유 원문을 노출하지 않는다")
    void notifyResolvedDismissedStaysSafe() {
        service.notifyResolved(REPORT_ID, REPORTER_ID, "기각");

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatcher).notifyAfterCommit(eq(List.of(REPORTER_ID)),
                eq(NotificationType.REPORT_RESOLVED.name()), title.capture(), body.capture(),
                isNull(), isNull(), isNull(), isNull(), eq(REPORT_ID));

        assertThat(title.getValue()).doesNotContain(String.valueOf(REPORT_ID));
        assertThat(body.getValue()).doesNotContain(String.valueOf(REPORT_ID));
        assertThat(title.getValue()).isNotBlank();
        assertThat(body.getValue()).isNotBlank();
    }

    @Test
    @DisplayName("신고자 번호가 없거나 0 이하이면 결과 알림을 만들지 않는다")
    void notifyResolvedSkipsWithoutReporter() {
        service.notifyResolved(REPORT_ID, null, "처리 완료");
        service.notifyResolved(REPORT_ID, 0L, "처리 완료");
        service.notifyResolved(REPORT_ID, -3L, "처리 완료");

        verify(dispatcher, never()).notifyAfterCommit(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("기각 상태는 한글 라벨·영문 코드 어느 표기든 같은 결과 안내로 처리한다")
    void notifyResolvedAcceptsDismissedVariants() {
        service.notifyResolved(REPORT_ID, REPORTER_ID, "기각");
        service.notifyResolved(REPORT_ID, REPORTER_ID, "DISMISSED");
        service.notifyResolved(REPORT_ID, REPORTER_ID, " dismissed ");

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(dispatcher, org.mockito.Mockito.times(3)).notifyAfterCommit(eq(List.of(REPORTER_ID)),
                eq(NotificationType.REPORT_RESOLVED.name()), title.capture(), anyString(),
                isNull(), isNull(), isNull(), isNull(), eq(REPORT_ID));

        assertThat(title.getAllValues()).containsOnly("접수하신 신고가 기각되었습니다");
    }

    @Test
    @DisplayName("알림 종류 코드는 레거시 저장 값과 겹치지 않는 신고 전용 코드다")
    void reportTypesDoNotCollideWithLegacyCodes() {
        assertThat(NotificationType.REPORT_SUBMITTED.name()).isEqualTo("REPORT_SUBMITTED");
        assertThat(NotificationType.REPORT_RESOLVED.name()).isEqualTo("REPORT_RESOLVED");
        assertThat(NotificationType.labelOf("REPORT_SUBMITTED")).isEqualTo("신고 접수");
        assertThat(NotificationType.labelOf("REPORT_RESOLVED")).isEqualTo("신고 처리 결과");
    }

    private static SystemAdminCandidate candidate(Long userId, String role) {
        SystemAdminCandidate candidate = new SystemAdminCandidate();
        candidate.setUserId(userId);
        candidate.setRole(role);
        return candidate;
    }
}
