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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.notification.service.ReportNotificationService;
import co.kr.spoit.report.domain.MatchSnapshot;
import co.kr.spoit.report.domain.Report;
import co.kr.spoit.report.domain.ReportMatchRow;
import co.kr.spoit.report.domain.ReportRow;
import co.kr.spoit.report.dto.FacilityReportPage;
import co.kr.spoit.report.mapper.ReportMapper;

/**
 * 시설 관리자 신고 접수의 권한·상태 게이트, 중복 클릭 방어, offset 페이지를 검증한다.
 *
 * <p>핵심 계약: 담당 활성 시설의 {@code 종료} 경기, 그 경기의 {@code 확정} 참가자만 신고할 수 있고,
 * 같은 요청의 10초 이내 재전송은 새 행 없이 기존 번호를 돌려준다. 다른 내용의 사건과 다른 대상은
 * 막지 않는다. 접수 알림은 실제 저장이 일어난 경우에만 호출한다. 경기·신고 목록은 0부터 시작하는
 * offset 페이지이며, 잘못된 페이지 값은 정규화된다.</p>
 */
class FacilityReportServiceTest {

    private static final long MANAGER_ID = 7L;
    private static final long MATCH_ID = 100L;
    private static final long FACILITY_ID = 10L;
    private static final long TARGET_ID = 21L;

    private final ReportMapper mapper = mock(ReportMapper.class);
    private final ReportNotificationService notification = mock(ReportNotificationService.class);
    private final FacilityReportService service = new FacilityReportService(mapper, notification);

    @BeforeEach
    void stubHappyPath() {
        when(mapper.findMatchSnapshotForUpdate(MATCH_ID)).thenReturn(match(FACILITY_ID, "종료"));
        when(mapper.existsFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(true);
        when(mapper.existsParticipant(MATCH_ID, TARGET_ID, ParticipationStatus.CONFIRMED.dbValue()))
                .thenReturn(true);
        when(mapper.findRecentDuplicate(anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(null);
        when(mapper.insertReport(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            report.setReportId(500L);
            return 1;
        });
        when(mapper.findReportableMatches(anyLong(), anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.findReportsForFacilityManager(anyLong(), anyLong(), anyInt(), anyInt()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("담당 활성 시설의 종료 경기 확정 참가자를 신고하면 접수 상태로 저장하고 알림을 예약한다")
    void submitsReportAndNotifies() {
        long reportId = service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "노쇼", "  경기에 나오지 않았습니다.  ");

        assertThat(reportId).isEqualTo(500L);
        verify(mapper).insertReport(any(Report.class));
        verify(notification).notifySubmitted(500L);
    }

    @Test
    @DisplayName("저장하는 신고는 접수 상태·정규화된 원문·요청 유형으로 만든다")
    void persistsReceivedStatusAndTrimmedSummary() {
        service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "폭력·싸움", "  몸싸움이 있었습니다.  ");

        org.mockito.ArgumentCaptor<Report> captor = org.mockito.ArgumentCaptor.forClass(Report.class);
        verify(mapper).insertReport(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("접수");
        assertThat(saved.getCategory()).isEqualTo("폭력·싸움");
        assertThat(saved.getSummary()).isEqualTo("몸싸움이 있었습니다.");
        assertThat(saved.getReporterUserId()).isEqualTo(MANAGER_ID);
        assertThat(saved.getTargetUserId()).isEqualTo(TARGET_ID);
    }

    @Test
    @DisplayName("종료되지 않은 경기는 신고를 받지 않고 저장·알림을 하지 않는다")
    void rejectsMatchNotClosed() {
        when(mapper.findMatchSnapshotForUpdate(MATCH_ID)).thenReturn(match(FACILITY_ID, "매칭 완료"));

        assertThatThrownBy(() -> service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "노쇼", "내용"))
                .isInstanceOf(ReportAccessDeniedException.class)
                .hasMessageContaining("이용이 끝난 경기");

        verify(mapper, never()).insertReport(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("담당하지 않은 시설의 경기는 신고할 수 없다")
    void rejectsOtherFacility() {
        when(mapper.existsFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "노쇼", "내용"))
                .isInstanceOf(ReportAccessDeniedException.class)
                .hasMessageContaining("담당 시설");

        verify(mapper, never()).insertReport(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("확정 참가자가 아닌 대상은 신고할 수 없다")
    void rejectsNonConfirmedTarget() {
        when(mapper.existsParticipant(MATCH_ID, TARGET_ID, ParticipationStatus.CONFIRMED.dbValue()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "노쇼", "내용"))
                .isInstanceOf(ReportAccessDeniedException.class)
                .hasMessageContaining("확정 참가자");

        verify(mapper, never()).insertReport(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("10초 이내 동일 요청은 새 행 없이 기존 신고 번호를 돌려주고 알림을 다시 보내지 않는다")
    void duplicateRequestReturnsExistingReport() {
        when(mapper.findRecentDuplicate(MANAGER_ID, MATCH_ID, TARGET_ID, "기타", "같은 내용",
                FacilityReportService.DUPLICATE_WINDOW_SECONDS)).thenReturn(777L);
        when(mapper.lockReportById(777L)).thenReturn(777L);

        long reportId = service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "기타", "같은 내용");

        assertThat(reportId).isEqualTo(777L);
        verify(mapper, never()).insertReport(any());
        verifyNoInteractions(notification);
    }

    @Test
    @DisplayName("내용이 다르면 최근 다른 신고가 있어도 새 신고로 접수한다")
    void differentContentIsNotTreatedAsDuplicate() {
        service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "기타", "다른 내용");

        verify(mapper).findRecentDuplicate(eq(MANAGER_ID), eq(MATCH_ID), eq(TARGET_ID), eq("기타"),
                eq("다른 내용"), eq(FacilityReportService.DUPLICATE_WINDOW_SECONDS));
        verify(mapper).insertReport(any(Report.class));
        verify(notification).notifySubmitted(500L);
    }

    @Test
    @DisplayName("중복 후보가 잠금 확인에서 사라지면 새 신고로 접수한다")
    void vanishedDuplicateCandidateFallsThrough() {
        when(mapper.findRecentDuplicate(MANAGER_ID, MATCH_ID, TARGET_ID, "기타", "경계 내용",
                FacilityReportService.DUPLICATE_WINDOW_SECONDS)).thenReturn(778L);
        when(mapper.lockReportById(778L)).thenReturn(null);

        long reportId = service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "기타", "경계 내용");

        assertThat(reportId).isEqualTo(500L);
        verify(mapper).insertReport(any(Report.class));
        verify(notification).notifySubmitted(500L);
    }

    @Test
    @DisplayName("중복 조회는 서비스 시각이 아니라 DB 시계 기준 창 초만 넘긴다")
    void duplicateLookupUsesDbWindowSeconds() {
        service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "기타", "시계 무관");

        verify(mapper).findRecentDuplicate(MANAGER_ID, MATCH_ID, TARGET_ID, "기타", "시계 무관", 10);
    }

    @Test
    @DisplayName("유형·원문이 비었거나 허용 밖 유형이면 저장 전에 거부한다")
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "없는 유형", "내용"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(MANAGER_ID, MATCH_ID, TARGET_ID, "기타", "   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.submit(MANAGER_ID, null, TARGET_ID, "기타", "내용"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(notification);
        verify(mapper, never()).insertReport(any());
    }

    @Test
    @DisplayName("경기 선택지는 담당 시설의 종료 상태를 pageSize+1 offset 페이지로 조회한다")
    void loadsReportableMatchesWithPaging() {
        when(mapper.findReportableMatches(MANAGER_ID, MatchStatus.CLOSED.dbValue(),
                FacilityReportService.PAGE_SIZE + 1, FacilityReportService.PAGE_SIZE))
                .thenReturn(List.of(matchRow(MATCH_ID)));

        FacilityReportPage page = service.loadPage(MANAGER_ID, null, null, null, 1, 0);

        assertThat(page.getMatches()).hasSize(1);
        assertThat(page.getMatches().get(0).getStartAt()).isEqualTo("2026-11-01 10:00");
        assertThat(page.getMatchPage()).isEqualTo(1);
        assertThat(page.isHasNextMatches()).isFalse();
        assertThat(page.getSelectedMatchId()).isNull();
    }

    @Test
    @DisplayName("pageSize+1건이면 다음 페이지가 있다고 표시하고 pageSize만 노출한다")
    void exposesHasNextWhenExtraRowReturned() {
        List<ReportMatchRow> rows = new ArrayList<>();
        for (long id = 1; id <= FacilityReportService.PAGE_SIZE + 1; id++) {
            rows.add(matchRow(2000L + id));
        }
        when(mapper.findReportableMatches(MANAGER_ID, MatchStatus.CLOSED.dbValue(),
                FacilityReportService.PAGE_SIZE + 1, 0)).thenReturn(rows);

        FacilityReportPage page = service.loadPage(MANAGER_ID, null, null, null, 0, 0);

        assertThat(page.getMatchPage()).isZero();
        assertThat(page.isHasNextMatches()).isTrue();
        assertThat(page.getMatches()).hasSize(FacilityReportService.PAGE_SIZE);
    }

    @Test
    @DisplayName("신고 목록도 pageSize+1 offset 페이지로 조회하고 다음 페이지 여부를 계산한다")
    void reportListUsesPaging() {
        List<ReportRow> rows = new ArrayList<>();
        for (int i = 0; i <= FacilityReportService.PAGE_SIZE; i++) {
            rows.add(reportRow(1000L + i));
        }
        when(mapper.findReportsForFacilityManager(MANAGER_ID, MANAGER_ID,
                FacilityReportService.PAGE_SIZE + 1, FacilityReportService.PAGE_SIZE * 2)).thenReturn(rows);

        FacilityReportPage page = service.loadPage(MANAGER_ID, null, null, null, 0, 2);

        assertThat(page.getReports()).hasSize(FacilityReportService.PAGE_SIZE);
        assertThat(page.getReportPage()).isEqualTo(2);
        assertThat(page.isHasNextReports()).isTrue();
    }

    @Test
    @DisplayName("음수·비정상 페이지는 0으로, 과대 페이지는 안전 상한으로 정규화한다")
    void normalizesUnsafePageValues() {
        assertThat(FacilityReportService.normalizePage(-5)).isZero();
        assertThat(FacilityReportService.normalizePage(0)).isZero();
        assertThat(FacilityReportService.normalizePage(Integer.MAX_VALUE))
                .isEqualTo(FacilityReportService.MAX_PAGE);
        // offset은 pageSize × 상한을 넘지 않아 int overflow가 없다.
        assertThat(FacilityReportService.offset(FacilityReportService.MAX_PAGE))
                .isEqualTo(FacilityReportService.PAGE_SIZE * FacilityReportService.MAX_PAGE);
    }

    @Test
    @DisplayName("페이지 밖 종료 경기를 링크로 고르면 단건 소유권·종료 확인을 통과해 선택지에 추가한다")
    void linkedOutOfPageMatchIsSelectable() {
        long linkedMatchId = 900L;
        when(mapper.findReportableMatches(MANAGER_ID, MatchStatus.CLOSED.dbValue(),
                FacilityReportService.PAGE_SIZE + 1, 0)).thenReturn(List.of(matchRow(MATCH_ID)));
        when(mapper.findReportableMatch(MANAGER_ID, linkedMatchId, MatchStatus.CLOSED.dbValue()))
                .thenReturn(matchRow(linkedMatchId));
        when(mapper.findParticipants(linkedMatchId, ParticipationStatus.CONFIRMED.dbValue()))
                .thenReturn(List.of(participantRow(TARGET_ID, "대상 참가자")));

        FacilityReportPage page = service.loadPage(MANAGER_ID, linkedMatchId, TARGET_ID, null, 0, 0);

        assertThat(page.getMatches()).extracting(co.kr.spoit.report.dto.ReportMatchOption::getMatchId)
                .contains(linkedMatchId);
        assertThat(page.getSelectedMatchId()).isEqualTo(linkedMatchId);
        assertThat(page.getParticipants()).hasSize(1);
        assertThat(page.getSelectedTargetUserId()).isEqualTo(TARGET_ID);
    }

    @Test
    @DisplayName("링크로 고른 경기가 담당 밖이거나 미종료면 단건 확인에서 null이라 선택되지 않는다")
    void linkedOutOfScopeMatchIsNotSelectable() {
        when(mapper.findReportableMatch(MANAGER_ID, 901L, MatchStatus.CLOSED.dbValue())).thenReturn(null);

        FacilityReportPage page = service.loadPage(MANAGER_ID, 901L, TARGET_ID, null, 0, 0);

        assertThat(page.getSelectedMatchId()).isNull();
        assertThat(page.getParticipants()).isEmpty();
        // 다른 경기의 참가자·정보를 조회하지 않는다.
        verify(mapper, never()).findParticipants(901L, ParticipationStatus.CONFIRMED.dbValue());
        verify(mapper, never()).findParticipants(anyLong(), anyString());
    }

    @Test
    @DisplayName("확정 참가자 선택지는 임의 200 절단 없이 전부 조회한다")
    void participantsAreNotTruncated() {
        List<co.kr.spoit.report.domain.ReportParticipantRow> rows = new ArrayList<>();
        for (long userId = 1; userId <= 250; userId++) {
            rows.add(participantRow(userId, "참가자 " + userId));
        }
        when(mapper.findReportableMatches(MANAGER_ID, MatchStatus.CLOSED.dbValue(),
                FacilityReportService.PAGE_SIZE + 1, 0)).thenReturn(List.of(matchRow(MATCH_ID)));
        when(mapper.findParticipants(MATCH_ID, ParticipationStatus.CONFIRMED.dbValue())).thenReturn(rows);

        FacilityReportPage page = service.loadPage(MANAGER_ID, MATCH_ID, null, null, 0, 0);

        assertThat(page.getParticipants()).hasSize(250);
    }

    @Test
    @DisplayName("담당 범위 밖 reportId는 404 예외로 처리한다")
    void outOfScopeReportIsNotFound() {
        when(mapper.findFacilityManagerReport(MANAGER_ID, MANAGER_ID, 999L)).thenReturn(null);

        assertThatThrownBy(() -> service.loadPage(MANAGER_ID, null, null, 999L, 0, 0))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    @DisplayName("목록 모델에는 신고 원문이 담기지 않는다")
    void listModelOmitsSummary() {
        when(mapper.findReportsForFacilityManager(eq(MANAGER_ID), eq(MANAGER_ID), anyInt(), anyInt()))
                .thenReturn(List.of(reportRow(1L)));

        FacilityReportPage page = service.loadPage(MANAGER_ID, null, null, null, 0, 0);

        assertThat(page.getReports()).hasSize(1);
        assertThat(page.getReports().get(0).getReportId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("인증 주체가 없으면 아무 것도 조회하지 않는다")
    void anonymousLoadsNothing() {
        FacilityReportPage page = service.loadPage(null, MATCH_ID, TARGET_ID, 1L, 0, 0);

        assertThat(page.getMatches()).isEmpty();
        assertThat(page.getSelectedReport()).isNull();
        verifyNoInteractions(mapper);
    }

    private static ReportMatchRow matchRow(long matchId) {
        ReportMatchRow row = new ReportMatchRow();
        row.setMatchId(matchId);
        row.setFacilityName("테스트 시설");
        row.setTitle("테스트 경기");
        row.setStartAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return row;
    }

    private static co.kr.spoit.report.domain.ReportParticipantRow participantRow(long userId, String nickname) {
        co.kr.spoit.report.domain.ReportParticipantRow row = new co.kr.spoit.report.domain.ReportParticipantRow();
        row.setUserId(userId);
        row.setNickname(nickname);
        return row;
    }

    private static ReportRow reportRow(long reportId) {
        ReportRow row = new ReportRow();
        row.setReportId(reportId);
        row.setCategory("기타");
        row.setStatus("접수");
        row.setSummary("민감한 신고 원문");
        return row;
    }

    private static MatchSnapshot match(long facilityId, String status) {
        MatchSnapshot snapshot = new MatchSnapshot();
        snapshot.setMatchId(MATCH_ID);
        snapshot.setFacilityId(facilityId);
        snapshot.setStatus(status);
        return snapshot;
    }
}
