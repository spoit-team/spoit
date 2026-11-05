package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.domain.EvaluationItem;
import co.kr.spoit.mat.domain.ItemScore;
import co.kr.spoit.mat.domain.MatchEvaluation;
import co.kr.spoit.mat.domain.ObservationStatus;
import co.kr.spoit.mat.domain.ResultMatch;
import co.kr.spoit.mat.domain.ResultTeamMember;
import co.kr.spoit.mat.dto.EvaluationSubmitRequest;
import co.kr.spoit.mat.dto.EvaluationSubmitRequest.Score;
import co.kr.spoit.mat.dto.EvaluationSubmitRequest.TargetEvaluation;
import co.kr.spoit.mat.dto.MatchEvaluationView;
import co.kr.spoit.mat.dto.MatchResultResponse;
import co.kr.spoit.mat.dto.MatchResultResponse.Outcome;
import co.kr.spoit.mat.mapper.MatchEvaluationMapper;
import co.kr.spoit.mat.mapper.MatchResultMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class MatchEvaluationServiceTest {

    private static final long MATCH_ID = 10L;
    private static final long SPORT_ID = 2L;
    private static final long CAPTAIN_A_USER = 11L;
    private static final long CAPTAIN_B_USER = 12L;
    private static final LocalDateTime END_AT = LocalDateTime.of(2026, 10, 30, 20, 0);
    private static final LocalDateTime NOW = END_AT.plusHours(1);
    private static final LocalDateTime DEADLINE = END_AT.plusHours(24);

    @Mock
    private MatchResultMapper resultMapper;

    @Mock
    private MatchEvaluationMapper evaluationMapper;

    @Mock
    private MatchResultService matchResultService;

    private MatchEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new MatchEvaluationService(resultMapper, evaluationMapper, matchResultService,
                new RatingCalculator());
    }

    @Test
    void showsOtherAssignedParticipantsAndActiveItemsToCaptain() {
        when(resultMapper.findResultMatch(MATCH_ID)).thenReturn(Optional.of(match("WAITING")));
        givenMembers();
        givenItems();
        givenEvaluators(2L);

        MatchEvaluationView view = service.getView(MATCH_ID, CAPTAIN_A_USER, NOW).orElseThrow();

        // 자기 자신(1)과 팀이 배정되지 않은 확정 참가자(4)는 대상이 아니다.
        assertThat(view.mySide()).isEqualTo("A");
        assertThat(view.targets())
                .extracting(MatchEvaluationView.Target::participantId, MatchEvaluationView.Target::nickname,
                        MatchEvaluationView.Target::matchSide)
                .containsExactly(tuple(2L, "B팀장", "B"), tuple(3L, "A팀원", "A"));
        assertThat(view.items()).extracting(MatchEvaluationView.Item::itemId).containsExactly(101L, 102L);
        assertThat(view.submitted()).isFalse();
        assertThat(view.canSubmit()).isTrue();
        assertThat(view.deadlineAt()).isEqualTo(DEADLINE);

        MatchEvaluationView submitted = service.getView(MATCH_ID, CAPTAIN_B_USER, NOW).orElseThrow();
        assertThat(submitted.mySide()).isEqualTo("B");
        assertThat(submitted.submitted()).isTrue();
        assertThat(submitted.canSubmit()).isFalse();
    }

    @Test
    void deniesViewToNonCaptainLikeResultView() {
        when(resultMapper.findResultMatch(MATCH_ID)).thenReturn(Optional.of(match("WAITING")));
        when(resultMapper.findResultMatch(999L)).thenReturn(Optional.empty());
        givenMembers();

        assertThatThrownBy(() -> service.getView(MATCH_ID, 13L, NOW)).isInstanceOf(AccessDeniedException.class);
        assertThat(service.getView(999L, CAPTAIN_A_USER, NOW)).isEmpty();
    }

    @Test
    void deniesParticipantWhoIsNotCaptain() {
        givenLockedMatch(match("WAITING"));
        givenMembers();

        assertThatThrownBy(() -> service.submit(MATCH_ID, 13L, validRequest(), NOW))
                .isInstanceOf(AccessDeniedException.class);
        verify(evaluationMapper, never()).insertEvaluation(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 24 * 60})
    void rejectsSubmissionOutsidePeriod(long minutesAfterEnd) {
        givenSubmittableState();

        assertThat(service.submit(MATCH_ID, CAPTAIN_A_USER, validRequest(), END_AT.plusMinutes(minutesAfterEnd))
                .outcome()).isEqualTo(Outcome.OUT_OF_PERIOD);
        verify(evaluationMapper, never()).insertEvaluation(any());
    }

    @Test
    void rejectsSecondSubmissionBySameCaptain() {
        givenLockedMatch(match("WAITING"));
        givenMembers();
        givenItems();
        givenEvaluators(1L);

        assertThat(service.submit(MATCH_ID, CAPTAIN_A_USER, validRequest(), NOW).outcome())
                .isEqualTo(Outcome.ALREADY_SUBMITTED);
        verify(evaluationMapper, never()).insertEvaluation(any());
        verify(matchResultService, never()).applyRatingIfReady(any(), any());
    }

    @ParameterizedTest
    @MethodSource("requestsNotCoveringEveryTargetAndItemExactly")
    void rejectsRequestNotCoveringEveryTargetAndItemExactly(EvaluationSubmitRequest request) {
        givenSubmittableState();

        assertThat(service.submit(MATCH_ID, CAPTAIN_A_USER, request, NOW).outcome())
                .isEqualTo(Outcome.INVALID_EVALUATION);
        verify(evaluationMapper, never()).insertEvaluation(any());
        verify(evaluationMapper, never()).insertScores(any(), any(), any());
    }

    static Stream<EvaluationSubmitRequest> requestsNotCoveringEveryTargetAndItemExactly() {
        Score[] full = {observed(101L, "8"), observed(102L, "6")};
        return Stream.of(
                new EvaluationSubmitRequest(null),
                // 대상: 빠짐, 중복, 팀 미배정 참가자, 자기 자신
                request(target(2L, full)),
                request(target(2L, full), target(2L, full)),
                request(target(2L, full), target(3L, full), target(4L, full)),
                request(target(1L, full), target(3L, full)),
                // 항목: 빠짐, 중복, 다른 종목 항목
                request(target(2L, full), target(3L, observed(101L, "8"))),
                request(target(2L, full), target(3L, observed(101L, "8"), observed(101L, "6"))),
                request(target(2L, full), target(3L, observed(101L, "8"), observed(999L, "6"))),
                // 점수: 관찰했는데 점수 없음, 관찰 불가인데 점수 있음, 범위 밖, 소수, 관찰 상태 없음
                request(target(2L, full), target(3L, observed(101L, "8"),
                        new Score(102L, ObservationStatus.OBSERVED, null))),
                request(target(2L, full), target(3L, observed(101L, "8"),
                        new Score(102L, ObservationStatus.NOT_OBSERVED, new BigDecimal("5")))),
                request(target(2L, full), target(3L, observed(101L, "11"), observed(102L, "6"))),
                request(target(2L, full), target(3L, observed(101L, "-1"), observed(102L, "6"))),
                request(target(2L, full), target(3L, observed(101L, "7.5"), observed(102L, "6"))),
                request(target(2L, full), target(3L, observed(101L, "8"), new Score(102L, null, new BigDecimal("6")))));
    }

    @Test
    void savesEvaluationOfEveryTargetWithWeightedOverallScore() {
        ResultMatch match = givenSubmittableState();
        givenGeneratedEvaluationIds();

        MatchResultResponse response = service.submit(MATCH_ID, CAPTAIN_A_USER, request(
                target(3L, notObserved(102L), notObserved(101L)),
                target(2L, observed(102L, "6"), observed(101L, "8"))), NOW);

        assertThat(response.outcome()).isEqualTo(Outcome.SUBMITTED);
        assertThat(response.message()).isEqualTo("평가를 제출했습니다.");
        // 저장은 대상 순서와 항목 표시 순서를 따른다. 대상 3은 관찰한 항목이 없어 점수가 없다.
        ArgumentCaptor<MatchEvaluation> saved = ArgumentCaptor.forClass(MatchEvaluation.class);
        verify(evaluationMapper, times(2)).insertEvaluation(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(MatchEvaluation::getMatchId, MatchEvaluation::getSportId,
                        MatchEvaluation::getEvaluatorParticipantId, MatchEvaluation::getTargetParticipantId,
                        MatchEvaluation::getOverallScore, MatchEvaluation::getStatus, MatchEvaluation::getSubmittedAt)
                .containsExactly(
                        tuple(MATCH_ID, SPORT_ID, 1L, 2L, new BigDecimal("6.80"), "제출 완료", NOW),
                        tuple(MATCH_ID, SPORT_ID, 1L, 3L, null, "제출 완료", NOW));
        verify(evaluationMapper).insertScores(500L, SPORT_ID, List.of(
                new ItemScore(101L, new BigDecimal("0.40"), ObservationStatus.OBSERVED, 8),
                new ItemScore(102L, new BigDecimal("0.60"), ObservationStatus.OBSERVED, 6)));
        verify(evaluationMapper).insertScores(501L, SPORT_ID, List.of(
                new ItemScore(101L, new BigDecimal("0.40"), ObservationStatus.NOT_OBSERVED, null),
                new ItemScore(102L, new BigDecimal("0.60"), ObservationStatus.NOT_OBSERVED, null)));

        // 잠근 뒤에 제출 여부를 읽고, 저장을 마친 뒤 반영 여부를 판단한다.
        InOrder order = inOrder(resultMapper, evaluationMapper, matchResultService);
        order.verify(resultMapper).lockResultMatch(MATCH_ID);
        order.verify(evaluationMapper).findEvaluatorIds(MATCH_ID);
        order.verify(evaluationMapper).insertScores(any(), any(), any());
        order.verify(matchResultService).applyRatingIfReady(match, NOW);
    }

    @Test
    void reportsRatingAppliedWhenSubmissionCompletesEvaluations() {
        ResultMatch match = givenSubmittableState();
        givenGeneratedEvaluationIds();
        when(matchResultService.applyRatingIfReady(match, NOW)).thenReturn(true);

        assertThat(service.submit(MATCH_ID, CAPTAIN_A_USER, validRequest(), NOW).message())
                .isEqualTo("평가를 제출했습니다. 확정 결과와 두 팀장의 평가가 모두 들어와 레이팅에 반영했습니다.");
    }

    private ResultMatch givenSubmittableState() {
        ResultMatch match = match("WAITING");
        givenLockedMatch(match);
        givenMembers();
        givenItems();
        givenEvaluators();
        return match;
    }

    private void givenLockedMatch(ResultMatch match) {
        when(resultMapper.lockResultMatch(MATCH_ID)).thenReturn(Optional.of(match));
    }

    /** A팀장 1(회원 11), B팀장 2(회원 12), A팀원 3(회원 13), 팀 미배정 확정 참가자 4(회원 14) */
    private void givenMembers() {
        when(resultMapper.findConfirmedTeamMembers(MATCH_ID, "확정")).thenReturn(List.of(
                member(1L, CAPTAIN_A_USER, "A팀장", "A"),
                member(2L, CAPTAIN_B_USER, "B팀장", "B"),
                member(3L, 13L, "A팀원", "A"),
                member(4L, 14L, "미배정", null)));
    }

    private void givenItems() {
        when(evaluationMapper.findActiveItems(SPORT_ID)).thenReturn(List.of(item(101L, "0.40"), item(102L, "0.60")));
    }

    private void givenEvaluators(Long... evaluatorParticipantIds) {
        when(evaluationMapper.findEvaluatorIds(MATCH_ID)).thenReturn(List.of(evaluatorParticipantIds));
    }

    private void givenGeneratedEvaluationIds() {
        long[] nextId = {500L};
        doAnswer(invocation -> {
            invocation.<MatchEvaluation>getArgument(0).setEvaluationId(nextId[0]++);
            return null;
        }).when(evaluationMapper).insertEvaluation(any());
    }

    private static EvaluationSubmitRequest validRequest() {
        return request(target(2L, observed(101L, "8"), observed(102L, "6")),
                target(3L, observed(101L, "7"), notObserved(102L)));
    }

    private static EvaluationSubmitRequest request(TargetEvaluation... evaluations) {
        return new EvaluationSubmitRequest(List.of(evaluations));
    }

    private static TargetEvaluation target(Long participantId, Score... scores) {
        return new TargetEvaluation(participantId, List.of(scores));
    }

    private static Score observed(Long itemId, String score) {
        return new Score(itemId, ObservationStatus.OBSERVED, new BigDecimal(score));
    }

    private static Score notObserved(Long itemId) {
        return new Score(itemId, ObservationStatus.NOT_OBSERVED, null);
    }

    private static ResultMatch match(String ratingStatus) {
        var match = new ResultMatch();
        match.setMatchId(MATCH_ID);
        match.setSportId(SPORT_ID);
        match.setStartAt(END_AT.minusHours(2));
        match.setEndAt(END_AT);
        match.setStatus("매칭 완료");
        match.setCaptainAParticipantId(1L);
        match.setCaptainBParticipantId(2L);
        match.setRatingStatus(ratingStatus);
        return match;
    }

    private static ResultTeamMember member(Long participantId, Long userId, String nickname, String side) {
        var member = new ResultTeamMember();
        member.setParticipantId(participantId);
        member.setUserId(userId);
        member.setNickname(nickname);
        member.setMatchSide(side);
        return member;
    }

    private static EvaluationItem item(Long itemId, String weight) {
        var item = new EvaluationItem();
        item.setItemId(itemId);
        item.setItemName("항목 " + itemId);
        item.setWeight(new BigDecimal(weight));
        return item;
    }
}
