package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.EvaluationItem;
import co.kr.spoit.mat.domain.ItemScore;
import co.kr.spoit.mat.domain.MatchEvaluation;
import co.kr.spoit.mat.domain.ObservationStatus;
import co.kr.spoit.mat.domain.RatingPolicy;
import co.kr.spoit.mat.domain.RatingStatus;
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
import co.kr.spoit.match.domain.ParticipationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀장의 경기 후 평가 조회·제출. 팀장 한 명이 평가 대상 전원의 평가를 한 번에 내고, 낸 평가는 고칠 수 없다.
 *
 * <p>제출은 결과 제출처럼 social_match를 잠근 뒤 다시 읽은 값으로 검사한다. (경기, 평가자) UNIQUE가 없어
 * 같은 팀장의 중복 제출은 이 잠금 아래의 확인으로만 막는다.</p>
 */
@Service
public class MatchEvaluationService {

    private static final String SUBMITTED_STATUS = "제출 완료";
    private static final String SIDE_A = "A";
    private static final String SIDE_B = "B";
    private static final BigDecimal MAX_SCORE = BigDecimal.TEN;

    private final MatchResultMapper resultMapper;
    private final MatchEvaluationMapper evaluationMapper;
    private final MatchResultService matchResultService;
    private final RatingCalculator calculator;

    public MatchEvaluationService(MatchResultMapper resultMapper,
                                  MatchEvaluationMapper evaluationMapper,
                                  MatchResultService matchResultService,
                                  RatingCalculator calculator) {
        this.resultMapper = resultMapper;
        this.evaluationMapper = evaluationMapper;
        this.matchResultService = matchResultService;
        this.calculator = calculator;
    }

    /** 경기가 없으면 empty */
    @Transactional(readOnly = true)
    public Optional<MatchEvaluationView> getView(long matchId, long userId, LocalDateTime now) {
        ResultMatch match = resultMapper.findResultMatch(matchId).orElse(null);
        if (match == null) {
            return Optional.empty();
        }
        Evaluator evaluator = evaluatorOf(match, userId);
        return Optional.of(new MatchEvaluationView(matchId, evaluator.side(),
                evaluator.targets().stream()
                        .map(target -> new MatchEvaluationView.Target(target.getParticipantId(), target.getNickname(),
                                target.getMatchSide()))
                        .toList(),
                evaluator.items().stream()
                        .map(item -> new MatchEvaluationView.Item(item.getItemId(), item.getItemName()))
                        .toList(),
                evaluator.submitted(), checkSubmittable(match, evaluator, now).isEmpty(), deadlineOf(match)));
    }

    @Transactional
    public MatchResultResponse submit(long matchId, long userId, EvaluationSubmitRequest request, LocalDateTime now) {
        // 잠금을 첫 읽기로 둔다. 잠금 전에 일반 SELECT를 하면 그때의 스냅샷을 계속 읽어, 잠금을 기다리는 동안
        // 먼저 커밋된 같은 팀장의 평가를 보지 못하고 한 번 더 저장한다.
        ResultMatch match = resultMapper.lockResultMatch(matchId).orElse(null);
        if (match == null) {
            return MatchResultResponse.notFound();
        }
        Evaluator evaluator = evaluatorOf(match, userId);
        Optional<MatchResultResponse> rejection = checkSubmittable(match, evaluator, now)
                .or(() -> checkRequest(request, evaluator));
        if (rejection.isPresent()) {
            return rejection.get();
        }

        Map<Long, TargetEvaluation> byTarget = request.evaluations().stream()
                .collect(Collectors.toMap(TargetEvaluation::targetParticipantId, Function.identity()));
        for (ResultTeamMember target : evaluator.targets()) {
            List<ItemScore> scores = itemScoresOf(byTarget.get(target.getParticipantId()), evaluator.items());
            var evaluation = new MatchEvaluation();
            evaluation.setMatchId(matchId);
            evaluation.setSportId(match.getSportId());
            evaluation.setEvaluatorParticipantId(evaluator.captain().getParticipantId());
            evaluation.setTargetParticipantId(target.getParticipantId());
            evaluation.setOverallScore(calculator.overallScore(scores));
            evaluation.setStatus(SUBMITTED_STATUS);
            evaluation.setSubmittedAt(now);
            evaluationMapper.insertEvaluation(evaluation);
            evaluationMapper.insertScores(evaluation.getEvaluationId(), match.getSportId(), scores);
        }

        if (matchResultService.applyRatingIfReady(match, now)) {
            return MatchResultResponse.of(Outcome.SUBMITTED,
                    "평가를 제출했습니다. 확정 결과와 두 팀장의 평가가 모두 들어와 레이팅에 반영했습니다.");
        }
        return MatchResultResponse.of(Outcome.SUBMITTED, "평가를 제출했습니다.");
    }

    private Evaluator evaluatorOf(ResultMatch match, long userId) {
        List<ResultTeamMember> members = resultMapper.findConfirmedTeamMembers(match.getMatchId(),
                ParticipationStatus.CONFIRMED.dbValue());
        // 결과 API와 같이 확정 참가 중인 팀장만 인정한다.
        ResultTeamMember captain = members.stream()
                .filter(member -> Objects.equals(member.getUserId(), userId))
                .filter(member -> Objects.equals(member.getParticipantId(), match.getCaptainAParticipantId())
                        || Objects.equals(member.getParticipantId(), match.getCaptainBParticipantId()))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("이 경기의 팀장만 평가를 조회하고 제출할 수 있습니다."));
        String side = captain.getParticipantId().equals(match.getCaptainAParticipantId()) ? SIDE_A : SIDE_B;
        List<ResultTeamMember> targets = members.stream()
                .filter(member -> member.getMatchSide() != null)
                .filter(member -> !member.getParticipantId().equals(captain.getParticipantId()))
                .toList();
        boolean submitted = evaluationMapper.findEvaluatorIds(match.getMatchId())
                .contains(captain.getParticipantId());
        return new Evaluator(captain, side, targets, evaluationMapper.findActiveItems(match.getSportId()), submitted);
    }

    private Optional<MatchResultResponse> checkSubmittable(ResultMatch match, Evaluator evaluator,
                                                           LocalDateTime now) {
        // 종료 경기는 레이팅 처리가 끝났으므로 실제로 받는 경기는 매칭 완료뿐이다. 거절 사유는 결과 제출과 맞춘다.
        if (!MatchResultService.PLAYED_STATUSES.contains(match.getStatus())) {
            return Optional.of(MatchResultResponse.of(Outcome.INVALID_STATUS, "평가를 제출할 수 있는 경기가 아닙니다."));
        }
        if (now.isBefore(match.getEndAt()) || !now.isBefore(deadlineOf(match))) {
            return Optional.of(MatchResultResponse.of(Outcome.OUT_OF_PERIOD, "경기 종료 후 %d시간 안에만 평가를 제출할 수 있습니다."
                    .formatted(RatingPolicy.RESULT_SUBMIT_PERIOD.toHours())));
        }
        if (!RatingStatus.WAITING.name().equals(match.getRatingStatus())) {
            return Optional.of(MatchResultResponse.of(Outcome.RATING_CLOSED, "레이팅 처리가 끝난 경기입니다."));
        }
        if (evaluator.submitted()) {
            return Optional.of(MatchResultResponse.of(Outcome.ALREADY_SUBMITTED,
                    "이미 평가를 제출했습니다. 제출한 평가는 고칠 수 없습니다."));
        }
        // 대상이 없으면 제출했다는 행을 남길 수 없고, 항목이 없으면 매길 점수가 없다.
        // 이런 경기도 결과가 확정됐으면 마감 때 평가 없이 반영된다.
        if (evaluator.targets().isEmpty() || evaluator.items().isEmpty()) {
            return Optional.of(MatchResultResponse.of(Outcome.INVALID_STATUS,
                    "평가할 참가자나 평가 항목이 없어 평가를 제출할 수 없습니다."));
        }
        return Optional.empty();
    }

    private static Optional<MatchResultResponse> checkRequest(EvaluationSubmitRequest request, Evaluator evaluator) {
        List<TargetEvaluation> evaluations = request == null ? null : request.evaluations();
        if (evaluations == null || evaluations.stream().anyMatch(Objects::isNull)
                || !coversEachOnce(evaluations.stream().map(TargetEvaluation::targetParticipantId).toList(),
                        evaluator.targets().stream().map(ResultTeamMember::getParticipantId).toList())) {
            return invalid("평가 대상 전원을 한 번씩 평가해야 합니다. 빠지거나 겹치거나 이 경기의 대상이 아닌 참가자가 있습니다.");
        }
        List<Long> itemIds = evaluator.items().stream().map(EvaluationItem::getItemId).toList();
        for (TargetEvaluation evaluation : evaluations) {
            List<Score> scores = evaluation.scores();
            if (scores == null || scores.stream().anyMatch(Objects::isNull)
                    || !coversEachOnce(scores.stream().map(Score::itemId).toList(), itemIds)) {
                return invalid("대상마다 이 종목의 평가 항목 전부에 한 번씩 점수를 매겨야 합니다.");
            }
            if (!scores.stream().allMatch(MatchEvaluationService::isValidScore)) {
                return invalid("점수는 0~10 정수로 매기거나 관찰 불가로 두어야 합니다.");
            }
        }
        return Optional.empty();
    }

    // 개수가 같고 집합이 같으면 빠짐·중복·다른 값이 모두 없다.
    private static boolean coversEachOnce(List<Long> requested, Collection<Long> expected) {
        return requested.size() == expected.size() && new HashSet<>(requested).equals(new HashSet<>(expected));
    }

    // DB CHECK는 OBSERVED에 score가 NULL이면 통과시키므로 여기서 관찰 상태와 점수를 함께 확인한다.
    private static boolean isValidScore(Score score) {
        BigDecimal value = score.score();
        if (score.observationStatus() == ObservationStatus.NOT_OBSERVED) {
            return value == null;
        }
        return score.observationStatus() == ObservationStatus.OBSERVED && value != null
                && value.signum() >= 0 && value.compareTo(MAX_SCORE) <= 0
                && value.stripTrailingZeros().scale() <= 0;
    }

    private static List<ItemScore> itemScoresOf(TargetEvaluation evaluation, List<EvaluationItem> items) {
        Map<Long, Score> byItem = evaluation.scores().stream()
                .collect(Collectors.toMap(Score::itemId, Function.identity()));
        return items.stream()
                .map(item -> {
                    Score score = byItem.get(item.getItemId());
                    Integer value = score.score() == null ? null : score.score().intValueExact();
                    return new ItemScore(item.getItemId(), item.getWeight(), score.observationStatus(), value);
                })
                .toList();
    }

    private static Optional<MatchResultResponse> invalid(String message) {
        return Optional.of(MatchResultResponse.of(Outcome.INVALID_EVALUATION, message));
    }

    private static LocalDateTime deadlineOf(ResultMatch match) {
        return match.getEndAt().plus(RatingPolicy.RESULT_SUBMIT_PERIOD);
    }

    /** 평가하는 팀장과 그 팀장이 평가할 대상·항목. */
    private record Evaluator(ResultTeamMember captain, String side, List<ResultTeamMember> targets,
                             List<EvaluationItem> items, boolean submitted) {
    }
}
