package co.kr.spoit.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.notification.service.ReportNotificationService;
import co.kr.spoit.report.domain.MatchSnapshot;
import co.kr.spoit.report.domain.Report;
import co.kr.spoit.report.domain.ReportParticipantRow;
import co.kr.spoit.report.dto.ReportSubmitRequest;
import co.kr.spoit.report.dto.ReportTargetResponse;
import co.kr.spoit.report.mapper.ReportMapper;

/**
 * 회원 대 회원 신고 접수의 권한·상태 게이트와 중복 클릭 방어를 검증한다.
 *
 * <p>핵심 계약: {@code 종료} 경기의 {@code 확정} 참가자만 신고할 수 있고, 신고자와 대상은 서로 달라야
 * 한다. 신고자 번호는 서비스 인자(인증 주체)로만 받으며, 대상 선택지에는 본인이 빠진다. 같은 요청의
 * 10초 이내 재전송은 새 행 없이 기존 번호를 돌려주고 알림을 다시 보내지 않는다.</p>
 */
class MemberReportServiceTest {

    private static final long REPORTER_ID = 7L;
    private static final long MATCH_ID = 100L;
    private static final long FACILITY_ID = 10L;
    private static final long TARGET_ID = 21L;

    private final ReportMapper mapper = mock(ReportMapper.class);
    private final ReportNotificationService notification = mock(ReportNotificationService.class);
    private final MemberReportService service = new MemberReportService(mapper, notification);

    @BeforeEach
    void stubHappyPath() {
        when(mapper.findMatchSnapshot(MATCH_ID)).thenReturn(match("종료"));
        when(mapper.findMatchSnapshotForUpdate(MATCH_ID)).thenReturn(match("종료"));
        when(mapper.existsParticipant(MATCH_ID, REPORTER_ID, ParticipationStatus.CONFIRMED.dbValue()))
                .thenReturn(true);
        when(mapper.existsParticipant(MATCH_ID, TARGET_ID, ParticipationStatus.CONFIRMED.dbValue()))
                .thenReturn(true);
        when(mapper.findRecentDuplicate(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(null);
        when(mapper.insertReport(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setReportId(500L);
            return 1;
        });
    }

    @Test
    @DisplayName("같은 경기 확정 참가자를 신고하면 접수 상태로 저장하고 알림을 예약한다")
    void submitsReportAndNotifies() {
        long reportId = service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "노쇼", "  경기에 나오지 않았습니다.  "));

        assertThat(reportId).isEqualTo(500L);
        org.mockito.ArgumentCaptor<Report> captor = org.mockito.ArgumentCaptor.forClass(Report.class);
        verify(mapper).insertReport(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("접수");
        assertThat(saved.getCategory()).isEqualTo("노쇼");
        assertThat(saved.getSummary()).isEqualTo("경기에 나오지 않았습니다.");
        // 신고자는 요청 본문이 아니라 인증 주체에서만 정해진다.
        assertThat(saved.getReporterUserId()).isEqualTo(REPORTER_ID);
        assertThat(saved.getTargetUserId()).isEqualTo(TARGET_ID);
        verify(notification).notifySubmitted(500L);
    }

    @Test
    @DisplayName("대상 선택지는 확정 참가자 중 본인을 뺀 목록만 돌려준다")
    void targetsExcludeSelf() {
        when(mapper.findParticipants(MATCH_ID, ParticipationStatus.CONFIRMED.dbValue()))
                .thenReturn(List.of(participantRow(REPORTER_ID, "나"), participantRow(TARGET_ID, "상대")));

        List<ReportTargetResponse> targets = service.loadTargets(REPORTER_ID, MATCH_ID);

        assertThat(targets).extracting(ReportTargetResponse::userId).containsExactly(TARGET_ID);
        assertThat(targets).extracting(ReportTargetResponse::nickname).containsExactly("상대");
    }

    @Test
    @DisplayName("확정 참가자가 아닌 회원은 대상 선택지를 조회할 수 없고 참가자 목록을 읽지 않는다")
    void nonParticipantCannotLoadTargets() {
        when(mapper.existsParticipant(MATCH_ID, REPORTER_ID, ParticipationStatus.CONFIRMED.dbValue()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.loadTargets(REPORTER_ID, MATCH_ID))
                .isInstanceOf(ReportAccessDeniedException.class)
                .hasMessageContaining("확정 참가자");

        verify(mapper, never()).findParticipants(anyLong(), anyString());
    }

    @Test
    @DisplayName("종료되지 않은 경기는 대상 선택지도 신고도 허용하지 않는다")
    void rejectsMatchNotClosed() {
        when(mapper.findMatchSnapshot(MATCH_ID)).thenReturn(match("모집 중"));
        when(mapper.findMatchSnapshotForUpdate(MATCH_ID)).thenReturn(match("모집 중"));

        assertThatThrownBy(() -> service.loadTargets(REPORTER_ID, MATCH_ID))
                .isInstanceOf(ReportAccessDeniedException.class)
                .hasMessageContaining("이용이 끝난 경기");
        assertThatThrownBy(() -> service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "노쇼", "내용")))
                .isInstanceOf(ReportAccessDeniedException.class)
                .hasMessageContaining("이용이 끝난 경기");

        verify(mapper, never()).findParticipants(anyLong(), anyString());
        verify(mapper, never()).insertReport(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("없는 경기는 404 예외로 처리한다")
    void missingMatchIsNotFound() {
        when(mapper.findMatchSnapshot(MATCH_ID)).thenReturn(null);
        when(mapper.findMatchSnapshotForUpdate(MATCH_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.loadTargets(REPORTER_ID, MATCH_ID))
                .isInstanceOf(ReportNotFoundException.class);
        assertThatThrownBy(() -> service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "노쇼", "내용")))
                .isInstanceOf(ReportAccessDeniedException.class);
    }

    @Test
    @DisplayName("확정 참가자가 아닌 신고자는 신고할 수 없다")
    void rejectsNonParticipantReporter() {
        when(mapper.existsParticipant(MATCH_ID, REPORTER_ID, ParticipationStatus.CONFIRMED.dbValue()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "노쇼", "내용")))
                .isInstanceOf(ReportAccessDeniedException.class)
                .hasMessageContaining("확정 참가자");

        verify(mapper, never()).insertReport(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("본인을 대상으로 지정하면 신고할 수 없다")
    void rejectsSelfTarget() {
        assertThatThrownBy(() -> service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(REPORTER_ID, "노쇼", "내용")))
                .isInstanceOf(ReportAccessDeniedException.class);

        verify(mapper, never()).insertReport(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("확정 참가자가 아닌 대상을 지정하면 신고할 수 없다")
    void rejectsNonParticipantTarget() {
        when(mapper.existsParticipant(MATCH_ID, TARGET_ID, ParticipationStatus.CONFIRMED.dbValue()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "노쇼", "내용")))
                .isInstanceOf(ReportAccessDeniedException.class)
                .hasMessageContaining("확정 참가자");

        verify(mapper, never()).insertReport(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("허용 밖 유형이나 1~1000자 규칙을 벗어난 원문은 저장 전에 거부한다")
    void rejectsInvalidContentOrCategory() {
        assertThatThrownBy(() -> service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "없는 유형", "내용")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "기타", "   ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "기타", "가".repeat(1001))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(null, "기타", "내용")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mapper, never()).insertReport(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("10초 이내 동일 요청은 새 행 없이 기존 신고 번호를 돌려주고 알림을 다시 보내지 않는다")
    void duplicateRequestReturnsExistingReport() {
        when(mapper.findRecentDuplicate(REPORTER_ID, MATCH_ID, TARGET_ID, "기타", "같은 내용",
                MemberReportService.DUPLICATE_WINDOW_SECONDS)).thenReturn(777L);
        when(mapper.lockReportById(777L)).thenReturn(777L);

        long reportId = service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "기타", "같은 내용"));

        assertThat(reportId).isEqualTo(777L);
        verify(mapper, never()).insertReport(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("내용이 다르면 최근 다른 신고가 있어도 새 신고로 접수한다")
    void differentContentIsNotTreatedAsDuplicate() {
        service.submit(REPORTER_ID, MATCH_ID, new ReportSubmitRequest(TARGET_ID, "기타", "다른 내용"));

        verify(mapper).findRecentDuplicate(eq(REPORTER_ID), eq(MATCH_ID), eq(TARGET_ID), eq("기타"),
                eq("다른 내용"), eq(MemberReportService.DUPLICATE_WINDOW_SECONDS));
        verify(mapper).insertReport(any(Report.class));
        verify(notification).notifySubmitted(500L);
    }

    @Test
    @DisplayName("중복 후보가 잠금 확인에서 사라지면 새 신고로 접수한다")
    void vanishedDuplicateCandidateFallsThrough() {
        when(mapper.findRecentDuplicate(REPORTER_ID, MATCH_ID, TARGET_ID, "기타", "경계 내용",
                MemberReportService.DUPLICATE_WINDOW_SECONDS)).thenReturn(778L);
        when(mapper.lockReportById(778L)).thenReturn(null);

        long reportId = service.submit(REPORTER_ID, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "기타", "경계 내용"));

        assertThat(reportId).isEqualTo(500L);
        verify(mapper).insertReport(any(Report.class));
        verify(notification).notifySubmitted(500L);
    }

    @Test
    @DisplayName("인증 주체가 없으면 대상 조회·접수를 진행하지 않는다")
    void anonymousDoesNothing() {
        assertThatThrownBy(() -> service.loadTargets(null, MATCH_ID))
                .isInstanceOf(ReportAccessDeniedException.class);
        assertThatThrownBy(() -> service.submit(null, MATCH_ID,
                new ReportSubmitRequest(TARGET_ID, "기타", "내용")))
                .isInstanceOf(ReportAccessDeniedException.class);

        verifyNoInteractions(mapper);
        verifyNoInteractions(notification);
    }

    private static MatchSnapshot match(String status) {
        MatchSnapshot snapshot = new MatchSnapshot();
        snapshot.setMatchId(MATCH_ID);
        snapshot.setFacilityId(FACILITY_ID);
        snapshot.setStatus(status);
        return snapshot;
    }

    private static ReportParticipantRow participantRow(long userId, String nickname) {
        ReportParticipantRow row = new ReportParticipantRow();
        row.setUserId(userId);
        row.setNickname(nickname);
        return row;
    }
}
