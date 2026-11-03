package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.domain.MatchParticipant;
import co.kr.spoit.mat.domain.MatchResult;
import co.kr.spoit.mat.domain.ReminderTarget;
import co.kr.spoit.mat.domain.ResultMatch;
import co.kr.spoit.mat.domain.ResultSubmission;
import co.kr.spoit.mat.dto.MatchResultResponse.Outcome;
import co.kr.spoit.mat.dto.MatchResultView;
import co.kr.spoit.mat.mapper.MatchAssignmentMapper;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import co.kr.spoit.mat.mapper.MatchResultMapper;
import co.kr.spoit.mat.service.MatchResultService.DeadlineResult;
import co.kr.spoit.notification.domain.NotificationType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    private RatingApplyService ratingApplyService;

    @Mock
    private MatchNotifier matchNotifier;

    private MatchResultService service;

    @BeforeEach
    void setUp() {
        service = new MatchResultService(resultMapper, participantMapper, assignmentMapper, ratingApplyService,
                matchNotifier);
        lenient().when(resultMapper.markRatingProcessed(anyLong(), anyString(), any(), any(), anyString()))
                .thenReturn(1);
    }

    @Test
    void confirmsMatchingResultsAndAppliesRating() {
        givenLockedMatch(match("매칭 완료", "SUBMITTED", "WAITING"));
        givenParticipants();
        givenActiveSubmissions(submission(20L, 2L, "A_WIN", "SUBMITTED"));

        assertThat(service.submit(MATCH_ID, CAPTAIN_A_USER, MatchResult.A_WIN, NOW).outcome())
                .isEqualTo(Outcome.CONFIRMED);

        verify(resultMapper).insertSubmission(MATCH_ID, 1L, "A_WIN", "CONFIRMED", NOW);
        verify(resultMapper).updateSubmissionStatus(20L, "CONFIRMED");
        verify(resultMapper).markResult(MATCH_ID, "CONFIRMED", "A_WIN", NOW);
        verify(ratingApplyService).apply(MATCH_ID, SPORT_ID, MatchResult.A_WIN, NOW);
        verify(resultMapper).markRatingProcessed(MATCH_ID, "APPLIED", NOW, null, "WAITING");
        verifyNoInteractions(matchNotifier);
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

        assertThat(service.submit(MATCH_ID, CAPTAIN_A_USER, MatchResult.B_WIN, NOW).outcome())
                .isEqualTo(Outcome.CONFLICT);
        verify(resultMapper).insertSubmission(MATCH_ID, 1L, "B_WIN", "CONFLICT", NOW);
        verify(resultMapper).updateSubmissionStatus(20L, "CONFLICT");
        verify(resultMapper).markResult(MATCH_ID, "CONFLICT", null, null);
        verify(matchNotifier).notifyAfterCommit(List.of(CAPTAIN_A_USER, CAPTAIN_B_USER), MATCH_ID,
                NotificationType.MATCH_RESULT_CONFLICT, "경기 결과 확인 요청",
                "상대 팀장과 경기 결과가 다릅니다. 제출 마감 전까지 다시 확인해 주세요.");
        verify(ratingApplyService, never()).apply(anyLong(), anyLong(), any(), any());

        LocalDateTime resubmittedAt = NOW.plusMinutes(10);
        assertThat(service.submit(MATCH_ID, CAPTAIN_A_USER, MatchResult.A_WIN, resubmittedAt).outcome())
                .isEqualTo(Outcome.CONFIRMED);
        verify(resultMapper).updateSubmissionStatus(21L, "REPLACED");
        verify(resultMapper).insertSubmission(MATCH_ID, 1L, "A_WIN", "CONFIRMED", resubmittedAt);
        verify(resultMapper).updateSubmissionStatus(20L, "CONFIRMED");
        verify(ratingApplyService).apply(MATCH_ID, SPORT_ID, MatchResult.A_WIN, resubmittedAt);
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

        assertThat(service.closeResult(MATCH_ID, DEADLINE)).isEqualTo(DeadlineResult.EXCLUDED);
        verify(resultMapper).markRatingProcessed(MATCH_ID, "EXCLUDED", DEADLINE, null, "WAITING");
        verify(ratingApplyService, never()).apply(anyLong(), anyLong(), any(), any());
    }

    @Test
    void appliesConfirmedResultAtDeadline() {
        ResultMatch match = match("매칭 완료", "CONFIRMED", "WAITING");
        match.setFinalResult("B_WIN");
        givenLockedMatch(match);

        assertThat(service.closeResult(MATCH_ID, DEADLINE)).isEqualTo(DeadlineResult.APPLIED);
        verify(ratingApplyService).apply(MATCH_ID, SPORT_ID, MatchResult.B_WIN, DEADLINE);
        verify(resultMapper).markRatingProcessed(MATCH_ID, "APPLIED", DEADLINE, null, "WAITING");
    }

    @Test
    void endsOnlyMatchesWhoseRatingIsProcessed() {
        when(resultMapper.findEndedMatchIds("매칭 완료", "APPLIED", "EXCLUDED", NOW)).thenReturn(List.of(MATCH_ID));
        when(resultMapper.markEnded(MATCH_ID, "종료", "매칭 완료", "APPLIED", "EXCLUDED", NOW)).thenReturn(1);

        assertThat(service.findEndedMatchIds(NOW)).containsExactly(MATCH_ID);
        assertThat(service.markEnded(MATCH_ID, NOW)).isTrue();
    }

    @Test
    void remindsCaptainsOfMatchesInCurrentMinuteWindow() {
        // 19:00:30 실행은 (18:59, 19:00] 구간을 맡고, 그보다 23시간 앞에 끝난 경기가 대상이다.
        when(resultMapper.findReminderTargets("매칭 완료", "종료", "WAITING", "확정",
                LocalDateTime.of(2026, 10, 30, 19, 59), LocalDateTime.of(2026, 10, 30, 20, 0)))
                .thenReturn(List.of(target(10L, 11L), target(10L, 12L), target(20L, 21L)));

        service.sendReminders(LocalDateTime.of(2026, 10, 31, 19, 0, 30));

        String body = "제출 마감까지 1시간 남았습니다. 경기 결과를 제출해 주세요.";
        verify(matchNotifier).notifyAfterCommit(List.of(11L, 12L), 10L, NotificationType.MATCH_RESULT_REMINDER,
                "경기 결과 미제출", body);
        verify(matchNotifier).notifyAfterCommit(List.of(21L), 20L, NotificationType.MATCH_RESULT_REMINDER,
                "경기 결과 미제출", body);
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
