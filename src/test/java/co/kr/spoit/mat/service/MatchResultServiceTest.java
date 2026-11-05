package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.domain.MatchParticipant;
import co.kr.spoit.mat.domain.MatchResult;
import co.kr.spoit.mat.domain.ReceivedEvaluation;
import co.kr.spoit.mat.domain.ReminderTarget;
import co.kr.spoit.mat.domain.ResultMatch;
import co.kr.spoit.mat.domain.ResultSubmission;
import co.kr.spoit.mat.dto.MatchResultResponse;
import co.kr.spoit.mat.dto.MatchResultResponse.Outcome;
import co.kr.spoit.mat.dto.MatchResultView;
import co.kr.spoit.mat.mapper.MatchAssignmentMapper;
import co.kr.spoit.mat.mapper.MatchEvaluationMapper;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import co.kr.spoit.mat.mapper.MatchResultMapper;
import co.kr.spoit.mat.service.MatchResultService.DeadlineResult;
import co.kr.spoit.notification.domain.NotificationType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class MatchResultServiceTest {

    private static final long MATCH_ID = 10L;
    private static final long SPORT_ID = 2L;
    private static final long FACILITY_ID = 3L;
    private static final long MANAGER_ID = 7L;
    private static final long CAPTAIN_A_USER = 11L;
    private static final long CAPTAIN_B_USER = 12L;
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 10, 30, 20, 0);
    private static final LocalDateTime NOW = END_AT.plusHours(1);
    private static final LocalDateTime DEADLINE = END_AT.plusHours(24);

    @Mock
    private MatchResultMapper resultMapper;

    @Mock
    private MatchParticipantMapper participantMapper;

    @Mock
    private MatchAssignmentMapper assignmentMapper;

    @Mock
    private MatchEvaluationMapper evaluationMapper;

    @Mock
    private RatingApplyService ratingApplyService;

    @Mock
    private MatchNotifier matchNotifier;

    private MatchResultService service;

    @BeforeEach
    void setUp() {
        service = new MatchResultService(resultMapper, participantMapper, assignmentMapper, evaluationMapper,
                ratingApplyService, new RatingCalculator(), matchNotifier);
        lenient().when(resultMapper.markRatingProcessed(anyLong(), anyString(), any(), any(), anyString()))
                .thenReturn(1);
    }

    @Test
    void confirmsMatchingResultsAndAppliesRatingWhenBothCaptainsEvaluated() {
        givenLockedMatch(match("매칭 완료", "SUBMITTED", "WAITING"));
        givenParticipants();
        givenActiveSubmissions(submission(20L, 2L, "A_WIN", "SUBMITTED"));
        givenEvaluators(1L, 2L);
        // 참가자 2는 두 평가의 평균, 3은 관찰한 항목이 없던 평가만 받아 평가 점수가 없다.
        givenReceivedEvaluations(received(1L, "7.00"), received(2L, "7.35"), received(2L, "8.00"), received(3L, null));

        MatchResultResponse response = service.submit(MATCH_ID, CAPTAIN_A_USER, MatchResult.A_WIN, NOW);
        assertThat(response.outcome()).isEqualTo(Outcome.CONFIRMED);
        assertThat(response.message()).isEqualTo("두 팀장의 결과가 같아 경기 결과를 확정하고 레이팅에 반영했습니다.");

        verify(resultMapper).insertSubmission(MATCH_ID, 1L, "A_WIN", "CONFIRMED", NOW);
        verify(resultMapper).updateSubmissionStatus(20L, "CONFIRMED");
        verify(resultMapper).markResult(MATCH_ID, "CONFIRMED", "A_WIN", NOW);
        verify(ratingApplyService).apply(MATCH_ID, SPORT_ID, MatchResult.A_WIN,
                Map.of(1L, new BigDecimal("7.00"), 2L, new BigDecimal("7.68")), NOW);
        verify(resultMapper).markRatingProcessed(MATCH_ID, "APPLIED", NOW, null, "WAITING");
        verifyNoInteractions(matchNotifier);
    }

    @Test
    void confirmsResultButWaitsUntilBothCaptainsEvaluate() {
        givenLockedMatch(match("매칭 완료", "SUBMITTED", "WAITING"));
        givenParticipants();
        givenActiveSubmissions(submission(20L, 2L, "A_WIN", "SUBMITTED"));
        givenEvaluators(2L);

        MatchResultResponse response = service.submit(MATCH_ID, CAPTAIN_A_USER, MatchResult.A_WIN, NOW);
        assertThat(response.outcome()).isEqualTo(Outcome.CONFIRMED);
        assertThat(response.message())
                .isEqualTo("두 팀장의 결과가 같아 경기 결과를 확정했습니다. 두 팀장의 평가가 모두 들어오면 레이팅에 반영됩니다.");

        verify(resultMapper).markResult(MATCH_ID, "CONFIRMED", "A_WIN", NOW);
        verify(ratingApplyService, never()).apply(anyLong(), anyLong(), any(), any(), any());
        verify(resultMapper, never()).markRatingProcessed(anyLong(), anyString(), any(), any(), anyString());
    }

    @Test
    void appliesWhenLastCaptainEvaluatesAfterResultIsConfirmed() {
        ResultMatch match = match("매칭 완료", "CONFIRMED", "WAITING");
        match.setFinalResult("DRAW");
        givenEvaluators(1L, 2L);
        givenReceivedEvaluations();

        assertThat(service.applyRatingIfReady(match, NOW)).isTrue();
        verify(ratingApplyService).apply(MATCH_ID, SPORT_ID, MatchResult.DRAW, Map.of(), NOW);
        verify(resultMapper).markRatingProcessed(MATCH_ID, "APPLIED", NOW, null, "WAITING");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"SUBMITTED", "CONFLICT"})
    void waitsForConfirmedResultEvenIfBothCaptainsEvaluated(String resultStatus) {
        lenient().when(evaluationMapper.findEvaluatorIds(MATCH_ID)).thenReturn(List.of(1L, 2L));

        assertThat(service.applyRatingIfReady(match("매칭 완료", resultStatus, "WAITING"), NOW)).isFalse();
        verify(ratingApplyService, never()).apply(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void appliesAtDeadlineWithPartialEvaluationsThatDoNotTriggerImmediateApply() {
        // 결과는 확정됐고 A팀장(1)만 평가를 냈다. 같은 상태가 마감 전에는 대기, 마감 때는 반영이다.
        ResultMatch match = match("매칭 완료", "CONFIRMED", "WAITING");
        match.setFinalResult("A_WIN");
        givenEvaluators(1L);
        givenReceivedEvaluations(received(2L, "8.00"), received(3L, "6.50"));

        assertThat(service.applyRatingIfReady(match, NOW)).isFalse();
        verify(ratingApplyService, never()).apply(anyLong(), anyLong(), any(), any(), any());

        givenLockedMatch(match);
        assertThat(service.closeResult(MATCH_ID, DEADLINE)).isEqualTo(DeadlineResult.APPLIED);
        verify(ratingApplyService).apply(MATCH_ID, SPORT_ID, MatchResult.A_WIN,
                Map.of(2L, new BigDecimal("8.00"), 3L, new BigDecimal("6.50")), DEADLINE);
        verify(resultMapper).markRatingProcessed(MATCH_ID, "APPLIED", DEADLINE, null, "WAITING");
    }

    @Test
    void confirmsResubmissionAfterConflict() {
        when(resultMapper.lockResultMatch(MATCH_ID))
                .thenReturn(Optional.of(match("매칭 완료", "SUBMITTED", "WAITING")))
                .thenReturn(Optional.of(match("매칭 완료", "CONFLICT", "WAITING")));
        givenParticipants();
        when(resultMapper.findActiveSubmissions(MATCH_ID, "REPLACED"))
                .thenReturn(List.of(submission(20L, 2L, "A_WIN", "SUBMITTED")))
                .thenReturn(List.of(submission(20L, 2L, "A_WIN", "CONFLICT"),
                        submission(21L, 1L, "B_WIN", "CONFLICT")));
        givenEvaluators(1L, 2L);

        assertThat(service.submit(MATCH_ID, CAPTAIN_A_USER, MatchResult.B_WIN, NOW).outcome())
                .isEqualTo(Outcome.CONFLICT);
        verify(resultMapper).insertSubmission(MATCH_ID, 1L, "B_WIN", "CONFLICT", NOW);
        verify(resultMapper).updateSubmissionStatus(20L, "CONFLICT");
        verify(resultMapper).markResult(MATCH_ID, "CONFLICT", null, null);
        verify(matchNotifier).notifyAfterCommit(List.of(CAPTAIN_A_USER, CAPTAIN_B_USER), MATCH_ID,
                NotificationType.MATCH_RESULT_CONFLICT, "경기 결과 확인 요청",
                "상대 팀장과 경기 결과가 다릅니다. 제출 마감 전까지 다시 확인해 주세요.");
        verify(ratingApplyService, never()).apply(anyLong(), anyLong(), any(), any(), any());

        LocalDateTime resubmittedAt = NOW.plusMinutes(10);
        assertThat(service.submit(MATCH_ID, CAPTAIN_A_USER, MatchResult.A_WIN, resubmittedAt).outcome())
                .isEqualTo(Outcome.CONFIRMED);
        verify(resultMapper).updateSubmissionStatus(21L, "REPLACED");
        verify(resultMapper).insertSubmission(MATCH_ID, 1L, "A_WIN", "CONFIRMED", resubmittedAt);
        verify(resultMapper).updateSubmissionStatus(20L, "CONFIRMED");
        verify(ratingApplyService).apply(MATCH_ID, SPORT_ID, MatchResult.A_WIN, Map.of(), resubmittedAt);
    }

    @Test
    void rejectsResubmissionUnlessConflict() {
        givenLockedMatch(match("매칭 완료", "SUBMITTED", "WAITING"));
        givenParticipants();
        givenActiveSubmissions(submission(21L, 1L, "A_WIN", "SUBMITTED"));

        assertThat(service.submit(MATCH_ID, CAPTAIN_A_USER, MatchResult.B_WIN, NOW).outcome())
                .isEqualTo(Outcome.ALREADY_SUBMITTED);
        verify(resultMapper, never()).updateSubmissionStatus(any(), any());
        verify(resultMapper, never()).insertSubmission(any(), any(), any(), any(), any());
    }

    @Test
    void deniesSubmissionFromNonCaptain() {
        givenLockedMatch(match("매칭 완료", null, "WAITING"));
        givenParticipants();

        assertThatThrownBy(() -> service.submit(MATCH_ID, 13L, MatchResult.DRAW, NOW))
                .isInstanceOf(AccessDeniedException.class);
        verify(resultMapper, never()).insertSubmission(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 24 * 60})
    void rejectsSubmissionOutsidePeriod(long minutesAfterEnd) {
        givenLockedMatch(match("매칭 완료", null, "WAITING"));
        givenParticipants();
        givenActiveSubmissions();

        assertThat(service.submit(MATCH_ID, CAPTAIN_B_USER, MatchResult.DRAW, END_AT.plusMinutes(minutesAfterEnd))
                .outcome()).isEqualTo(Outcome.OUT_OF_PERIOD);
        verify(resultMapper, never()).insertSubmission(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLIED", "EXCLUDED"})
    void rejectsSubmissionToEndedMatch(String ratingStatus) {
        givenLockedMatch(match("종료", null, ratingStatus));
        givenParticipants();
        givenActiveSubmissions();

        assertThat(service.submit(MATCH_ID, CAPTAIN_B_USER, MatchResult.DRAW, NOW).outcome())
                .isEqualTo(Outcome.RATING_CLOSED);
        verify(resultMapper, never()).insertSubmission(any(), any(), any(), any(), any());
    }

    @Test
    void showsOpponentResultOnlyOnConflict() {
        when(resultMapper.findResultMatch(MATCH_ID)).thenReturn(Optional.of(match("매칭 완료", "SUBMITTED", "WAITING")));
        givenParticipants();
        when(resultMapper.findActiveSubmissions(MATCH_ID, "REPLACED"))
                .thenReturn(List.of(submission(20L, 2L, "A_WIN", "SUBMITTED")))
                .thenReturn(List.of(submission(20L, 2L, "A_WIN", "CONFLICT"),
                        submission(21L, 1L, "B_WIN", "CONFLICT")));

        MatchResultView waiting = service.getView(MATCH_ID, CAPTAIN_A_USER, NOW).orElseThrow();
        assertThat(waiting.mySide()).isEqualTo("A");
        assertThat(waiting.myResult()).isNull();
        assertThat(waiting.opponentSubmitted()).isTrue();
        assertThat(waiting.conflict()).isFalse();
        assertThat(waiting.opponentResult()).isNull();
        assertThat(waiting.deadlineAt()).isEqualTo(DEADLINE);
        assertThat(waiting.canSubmit()).isTrue();

        MatchResultView conflict = service.getView(MATCH_ID, CAPTAIN_A_USER, NOW).orElseThrow();
        assertThat(conflict.myResult()).isEqualTo("B_WIN");
        assertThat(conflict.conflict()).isTrue();
        assertThat(conflict.opponentResult()).isEqualTo("A_WIN");
        assertThat(conflict.canSubmit()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"SUBMITTED", "CONFLICT"})
    void excludesUnconfirmedMatchAtDeadline(String resultStatus) {
        givenLockedMatch(match("매칭 완료", resultStatus, "WAITING"));
        // 두 팀장이 평가를 냈어도 결과가 확정되지 않으면 제외한다.
        lenient().when(evaluationMapper.findEvaluatorIds(MATCH_ID)).thenReturn(List.of(1L, 2L));

        assertThat(service.closeResult(MATCH_ID, DEADLINE)).isEqualTo(DeadlineResult.EXCLUDED);
        verify(resultMapper).markRatingProcessed(MATCH_ID, "EXCLUDED", DEADLINE, null, "WAITING");
        verify(ratingApplyService, never()).apply(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void appliesConfirmedResultAtDeadlineWithoutEvaluations() {
        ResultMatch match = match("매칭 완료", "CONFIRMED", "WAITING");
        match.setFinalResult("B_WIN");
        givenLockedMatch(match);

        assertThat(service.closeResult(MATCH_ID, DEADLINE)).isEqualTo(DeadlineResult.APPLIED);
        verify(ratingApplyService).apply(MATCH_ID, SPORT_ID, MatchResult.B_WIN, Map.of(), DEADLINE);
        verify(resultMapper).markRatingProcessed(MATCH_ID, "APPLIED", DEADLINE, null, "WAITING");
    }

    @Test
    void endsOnlyMatchesWhoseRatingIsProcessed() {
        when(resultMapper.findEndedMatchIds("매칭 완료", "APPLIED", "EXCLUDED", NOW)).thenReturn(List.of(MATCH_ID));

        assertThat(service.findEndedMatchIds(NOW)).containsExactly(MATCH_ID);
    }

    @Test
    void notifiesConfirmedParticipantsOnceWhenMatchEnds() {
        givenParticipants();
        when(resultMapper.markEnded(MATCH_ID, "종료", "매칭 완료", "APPLIED", "EXCLUDED", NOW)).thenReturn(1);

        assertThat(service.markEnded(MATCH_ID, NOW)).isTrue();

        // 확정 참가자 3명에게 같은 안내를 한 번만 예약한다. 레이팅 반영·제외는 종류를 나누지 않는다.
        verify(matchNotifier).notifyAfterCommit(List.of(CAPTAIN_A_USER, CAPTAIN_B_USER, 13L), MATCH_ID,
                NotificationType.MATCH_RESULT_COMPLETED, "경기 결과 산정 완료",
                "경기 결과 산정이 완료되었습니다. 자세한 내용은 경기 결과 페이지에서 확인해 주세요.");
        verify(matchNotifier, times(1)).notifyAfterCommit(any(), anyLong(), any(), anyString(), anyString());
    }

    @Test
    void doesNotNotifyAgainWhenMatchWasAlreadyEnded() {
        when(resultMapper.markEnded(MATCH_ID, "종료", "매칭 완료", "APPLIED", "EXCLUDED", NOW)).thenReturn(0);

        assertThat(service.markEnded(MATCH_ID, NOW)).isFalse();

        verifyNoInteractions(participantMapper, matchNotifier);
    }

    @Test
    void doesNotNotifyBeforeMatchEnd() {
        // 종료 시각 전이거나 레이팅이 아직 WAITING이면 조건부 UPDATE가 0행이라 알림도 없어야 한다.
        when(resultMapper.markEnded(MATCH_ID, "종료", "매칭 완료", "APPLIED", "EXCLUDED", END_AT.minusMinutes(1)))
                .thenReturn(0);

        assertThat(service.markEnded(MATCH_ID, END_AT.minusMinutes(1))).isFalse();

        verifyNoInteractions(participantMapper, matchNotifier);
    }

    @Test
    void remindsCaptainsOfMatchesInCurrentMinuteWindow() {
        // 19:00:30 실행은 (18:59, 19:00] 구간을 맡고, 그보다 23시간 앞에 끝난 경기가 대상이다.
        when(resultMapper.findReminderTargets("매칭 완료", "종료", "WAITING", "확정",
                LocalDateTime.of(2026, 10, 30, 19, 59), LocalDateTime.of(2026, 10, 30, 20, 0)))
                .thenReturn(List.of(target(10L, 11L), target(10L, 12L), target(20L, 21L)));

        service.sendReminders(LocalDateTime.of(2026, 10, 31, 19, 0, 30));

        String body = "제출 마감까지 1시간 남았습니다. 아직 내지 않은 경기 결과나 평가를 제출해 주세요.";
        verify(matchNotifier).notifyAfterCommit(List.of(11L, 12L), 10L, NotificationType.MATCH_RESULT_REMINDER,
                "경기 결과·평가 미제출", body);
        verify(matchNotifier).notifyAfterCommit(List.of(21L), 20L, NotificationType.MATCH_RESULT_REMINDER,
                "경기 결과·평가 미제출", body);
    }

    @Test
    void summaryShowsOwnSideAndStatusForAnyConfirmedParticipant() {
        ResultMatch match = match("종료", "CONFIRMED", "APPLIED");
        match.setFinalResult("A_WIN");
        when(resultMapper.findResultMatch(MATCH_ID)).thenReturn(Optional.of(match));
        givenParticipants();

        var summary = service.getSummary(MATCH_ID, 13L).orElseThrow();

        // 팀장이 아닌 확정 참가자: 본인 진영과 상태만 받고 제출 결과는 받지 않는다.
        assertThat(summary.matchId()).isEqualTo(MATCH_ID);
        assertThat(summary.status()).isEqualTo("종료");
        assertThat(summary.resultStatus()).isEqualTo("CONFIRMED");
        assertThat(summary.ratingStatus()).isEqualTo("APPLIED");
        assertThat(summary.finalResult()).isEqualTo("A_WIN");
        assertThat(summary.mySide()).isEqualTo("A");
        assertThat(summary.myResult()).isNull();
        verify(resultMapper, never()).findActiveSubmissions(anyLong(), anyString());
    }

    @Test
    void summaryIncludesOwnSubmissionForCaptain() {
        when(resultMapper.findResultMatch(MATCH_ID)).thenReturn(Optional.of(match("매칭 완료", "SUBMITTED", "WAITING")));
        givenParticipants();
        givenActiveSubmissions(submission(20L, 2L, "A_WIN", "SUBMITTED"));

        var summary = service.getSummary(MATCH_ID, CAPTAIN_B_USER).orElseThrow();

        assertThat(summary.mySide()).isEqualTo("B");
        assertThat(summary.myResult()).isEqualTo("A_WIN");
    }

    @Test
    void deniesSummaryForNonParticipant() {
        when(resultMapper.findResultMatch(MATCH_ID)).thenReturn(Optional.of(match("종료", "CONFIRMED", "APPLIED")));
        givenParticipants();

        assertThatThrownBy(() -> service.getSummary(MATCH_ID, 99L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void returnsEmptySummaryForMissingMatch() {
        when(resultMapper.findResultMatch(MATCH_ID)).thenReturn(Optional.empty());

        assertThat(service.getSummary(MATCH_ID, CAPTAIN_A_USER)).isEmpty();
        verifyNoInteractions(participantMapper);
    }

    @Test
    void excludesStartedMatchForFacilityManager() {
        givenLockedMatch(match("매칭 완료", "CONFLICT", "WAITING"));
        when(assignmentMapper.isFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(true);

        assertThat(service.excludeRating(MATCH_ID, MANAGER_ID, NOW).outcome()).isEqualTo(Outcome.EXCLUDED);
        verify(resultMapper).markRatingProcessed(MATCH_ID, "EXCLUDED", NOW, MANAGER_ID, "WAITING");
    }

    @Test
    void deniesExclusionByManagerOfAnotherFacility() {
        givenLockedMatch(match("매칭 완료", null, "WAITING"));
        when(assignmentMapper.isFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.excludeRating(MATCH_ID, MANAGER_ID, NOW))
                .isInstanceOf(AccessDeniedException.class);
        verify(resultMapper, never()).markRatingProcessed(anyLong(), anyString(), any(), any(), anyString());
    }

    @Test
    void rejectsExclusionOfAppliedMatch() {
        givenLockedMatch(match("종료", "CONFIRMED", "APPLIED"));
        when(assignmentMapper.isFacilityManager(FACILITY_ID, MANAGER_ID)).thenReturn(true);

        assertThat(service.excludeRating(MATCH_ID, MANAGER_ID, NOW).outcome()).isEqualTo(Outcome.ALREADY_APPLIED);
        verify(resultMapper, never()).markRatingProcessed(anyLong(), anyString(), any(), any(), anyString());
    }

    private void givenLockedMatch(ResultMatch match) {
        when(resultMapper.lockResultMatch(MATCH_ID)).thenReturn(Optional.of(match));
    }

    /** A팀장 1(회원 11), B팀장 2(회원 12), 팀장이 아닌 확정 참가자 3(회원 13) */
    private void givenParticipants() {
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(List.of(
                participant(1L, CAPTAIN_A_USER, "A"),
                participant(2L, CAPTAIN_B_USER, "B"),
                participant(3L, 13L, "A")));
    }

    private void givenActiveSubmissions(ResultSubmission... submissions) {
        when(resultMapper.findActiveSubmissions(MATCH_ID, "REPLACED")).thenReturn(List.of(submissions));
    }

    private void givenEvaluators(Long... evaluatorParticipantIds) {
        when(evaluationMapper.findEvaluatorIds(MATCH_ID)).thenReturn(List.of(evaluatorParticipantIds));
    }

    private void givenReceivedEvaluations(ReceivedEvaluation... evaluations) {
        when(evaluationMapper.findReceivedEvaluations(MATCH_ID)).thenReturn(List.of(evaluations));
    }

    private static ReceivedEvaluation received(Long targetParticipantId, String overallScore) {
        var evaluation = new ReceivedEvaluation();
        evaluation.setTargetParticipantId(targetParticipantId);
        evaluation.setOverallScore(overallScore == null ? null : new BigDecimal(overallScore));
        return evaluation;
    }

    private static ResultMatch match(String status, String resultStatus, String ratingStatus) {
        var match = new ResultMatch();
        match.setMatchId(MATCH_ID);
        match.setFacilityId(FACILITY_ID);
        match.setSportId(SPORT_ID);
        match.setStartAt(END_AT.minusHours(2));
        match.setEndAt(END_AT);
        match.setStatus(status);
        match.setCaptainAParticipantId(1L);
        match.setCaptainBParticipantId(2L);
        match.setResultStatus(resultStatus);
        match.setRatingStatus(ratingStatus);
        return match;
    }

    private static MatchParticipant participant(Long participantId, Long userId, String side) {
        var participant = new MatchParticipant();
        participant.setParticipantId(participantId);
        participant.setUserId(userId);
        participant.setMatchId(MATCH_ID);
        participant.setParticipationStatus("확정");
        participant.setMatchSide(side);
        participant.setEntryMmr(new BigDecimal("1000.00"));
        return participant;
    }

    private static ResultSubmission submission(Long submissionId, Long captainParticipantId, String result,
                                               String status) {
        var submission = new ResultSubmission();
        submission.setSubmissionId(submissionId);
        submission.setCaptainParticipantId(captainParticipantId);
        submission.setReportedResult(result);
        submission.setSubmissionStatus(status);
        return submission;
    }

    private static ReminderTarget target(Long matchId, Long userId) {
        var target = new ReminderTarget();
        target.setMatchId(matchId);
        target.setUserId(userId);
        return target;
    }
}
