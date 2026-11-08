package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.MatchParticipant;
import co.kr.spoit.mat.domain.MatchResult;
import co.kr.spoit.mat.domain.RatingPolicy;
import co.kr.spoit.mat.domain.RatingStatus;
import co.kr.spoit.mat.domain.ReceivedEvaluation;
import co.kr.spoit.mat.domain.ReminderTarget;
import co.kr.spoit.mat.domain.ResultMatch;
import co.kr.spoit.mat.domain.ResultPageMatch;
import co.kr.spoit.mat.domain.ResultStatus;
import co.kr.spoit.mat.domain.ResultSubmission;
import co.kr.spoit.mat.domain.ResultTeamMember;
import co.kr.spoit.mat.domain.SubmissionStatus;
import co.kr.spoit.mat.dto.MatchResultResponse;
import co.kr.spoit.mat.dto.MatchResultResponse.Outcome;
import co.kr.spoit.mat.dto.MatchResultSummary;
import co.kr.spoit.mat.dto.MatchResultView;
import co.kr.spoit.mat.mapper.MatchAssignmentMapper;
import co.kr.spoit.mat.mapper.MatchEvaluationMapper;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import co.kr.spoit.mat.mapper.MatchResultMapper;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.notification.domain.NotificationType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종료 시각 뒤 처리: 팀장 결과 제출, 레이팅 반영 시점 판단, 제출 마감, 리마인더, 레이팅 제외, 처리가 끝난 경기의 종료 전환.
 *
 * <p>제출·마감·제외는 매칭방 행을 잠근 뒤 다시 읽은 값으로 검사한다. 같은 경기의 요청이 겹쳐도
 * 하나씩 처리되므로 팀장마다 활성 제출 행은 하나만 남는다.</p>
 */
@Slf4j
@Service
public class MatchResultService {

    public enum DeadlineResult {
        APPLIED,
        EXCLUDED,
        SKIPPED
    }

    // 결과 처리는 매칭 완료 경기에서 한다. 종료 경기는 레이팅 처리가 끝났으므로, 상태 오류 대신
    // RATING_CLOSED·ALREADY_APPLIED·ALREADY_EXCLUDED로 답하려고 함께 둔다.
    static final List<String> PLAYED_STATUSES =
            List.of(MatchStatus.MATCHED.dbValue(), MatchStatus.CLOSED.dbValue());
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E) HH:mm", Locale.KOREAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String SIDE_A = "A";
    private static final String SIDE_B = "B";
    // 결과 확정과 레이팅 제외를 따로 알리지 않고, 경기가 종료로 바뀌는 한 번의 전환에서 같은 안내를 보낸다.
    private static final String RESULT_COMPLETED_BODY =
            "경기 결과 산정이 완료되었습니다. 자세한 내용은 경기 결과 페이지에서 확인해 주세요.";

    private final MatchResultMapper resultMapper;
    private final MatchParticipantMapper participantMapper;
    private final MatchAssignmentMapper assignmentMapper;
    private final MatchEvaluationMapper evaluationMapper;
    private final RatingApplyService ratingApplyService;
    private final RatingCalculator calculator;
    private final MatchNotifier matchNotifier;

    public MatchResultService(MatchResultMapper resultMapper,
                              MatchParticipantMapper participantMapper,
                              MatchAssignmentMapper assignmentMapper,
                              MatchEvaluationMapper evaluationMapper,
                              RatingApplyService ratingApplyService,
                              RatingCalculator calculator,
                              MatchNotifier matchNotifier) {
        this.resultMapper = resultMapper;
        this.participantMapper = participantMapper;
        this.assignmentMapper = assignmentMapper;
        this.evaluationMapper = evaluationMapper;
        this.ratingApplyService = ratingApplyService;
        this.calculator = calculator;
        this.matchNotifier = matchNotifier;
    }

    /** 경기가 없으면 empty */
    @Transactional(readOnly = true)
    public Optional<MatchResultView> getView(long matchId, long userId, LocalDateTime now) {
        ResultMatch match = resultMapper.findResultMatch(matchId).orElse(null);
        if (match == null) {
            return Optional.empty();
        }
        Captains captains = captainsOf(match, userId);
        List<ResultSubmission> active = resultMapper.findActiveSubmissions(matchId, SubmissionStatus.REPLACED.name());
        ResultSubmission mine = submissionOf(active, captains.mine());
        ResultSubmission opponent = submissionOf(active, captains.opponent());
        boolean conflict = mine != null && opponent != null
                && SubmissionStatus.CONFLICT.name().equals(mine.getSubmissionStatus());
        return Optional.of(new MatchResultView(matchId, captains.side(),
                mine == null ? null : mine.getReportedResult(),
                opponent != null, conflict,
                conflict ? opponent.getReportedResult() : null,
                match.getFinalResult(), match.getRatingStatus(), deadlineOf(match),
                checkSubmittable(match, mine, now).isEmpty()));
    }

    /**
     * 확정 참가자가 본인 경기의 결과 처리 상태와 화면에 보여 줄 경기 정보 및 양 팀 로스터를 본다.
     * 팀장 전용 {@link #getView}와 달리 상대 제출 결과나 재제출 안내를 담지 않는다.
     * 확정 참가자가 아니면 {@link AccessDeniedException}.
     */
    @Transactional(readOnly = true)
    public Optional<MatchResultSummary> getSummary(long matchId, long userId) {
        ResultMatch match = resultMapper.findResultMatch(matchId).orElse(null);
        if (match == null) {
            return Optional.empty();
        }
        List<ResultTeamMember> confirmed = resultMapper.findConfirmedTeamMembers(matchId,
                ParticipationStatus.CONFIRMED.dbValue());
        ResultTeamMember me = confirmed.stream()
                .filter(p -> Objects.equals(p.getUserId(), userId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("이 경기의 확정 참가자만 결과를 조회할 수 있습니다."));
        String mySide = me.getMatchSide();
        Optional<ResultPageMatch> pageMatch = resultMapper.findResultPageMatch(matchId);
        return Optional.of(new MatchResultSummary(matchId, match.getStatus(), match.getResultStatus(),
                match.getRatingStatus(), match.getFinalResult(), mySide, myResultOf(match, me.getParticipantId()),
                pageMatch.map(ResultPageMatch::getTitle).orElse(null),
                pageMatch.map(ResultPageMatch::getSportName).orElse(null),
                scheduleOf(match), pageMatch.map(ResultPageMatch::getFacilityName).orElse(null),
                deadlineOf(match).format(DATE_TIME),
                List.of(teamOf(SIDE_A, match.getCaptainAParticipantId(), mySide, confirmed),
                        teamOf(SIDE_B, match.getCaptainBParticipantId(), mySide, confirmed))));
    }

    // 두 팀 로스터. 화면에는 확정 참가자의 닉네임과 팀장 여부만 내보내고 회원 번호는 담지 않는다.
    private static MatchResultSummary.Team teamOf(String side, Long captainParticipantId, String mySide,
                                                  List<ResultTeamMember> members) {
        List<MatchResultSummary.Member> team = members.stream()
                .filter(member -> side.equals(member.getMatchSide()))
                .map(member -> new MatchResultSummary.Member(member.getNickname(),
                        Objects.equals(member.getParticipantId(), captainParticipantId)))
                .sorted(Comparator.comparing(MatchResultSummary.Member::captain).reversed())
                .toList();
        return new MatchResultSummary.Team(side, side.equals(mySide), team);
    }

    private static String scheduleOf(ResultMatch match) {
        LocalDateTime start = match.getStartAt();
        LocalDateTime end = match.getEndAt();
        String endText = end.toLocalDate().equals(start.toLocalDate()) ? end.format(TIME) : end.format(DATE_TIME);
        return start.format(DATE_TIME) + " ~ " + endText;
    }

    /** 본인이 팀장일 때만 본인이 제출한 결과를 채운다. 일반 확정 참가자에게는 제출 결과를 노출하지 않는다. */
    private String myResultOf(ResultMatch match, Long myParticipantId) {
        boolean captain = Objects.equals(myParticipantId, match.getCaptainAParticipantId())
                || Objects.equals(myParticipantId, match.getCaptainBParticipantId());
        if (!captain) {
            return null;
        }
        return resultMapper.findActiveSubmissions(match.getMatchId(), SubmissionStatus.REPLACED.name()).stream()
                .filter(s -> Objects.equals(myParticipantId, s.getCaptainParticipantId()))
                .map(ResultSubmission::getReportedResult)
                .findFirst()
                .orElse(null);
    }

    @Transactional
    public MatchResultResponse submit(long matchId, long userId, MatchResult result, LocalDateTime now) {
        if (result == null) {
            return MatchResultResponse.of(Outcome.INVALID_RESULT, "결과는 A_WIN, DRAW, B_WIN 중 하나여야 합니다.");
        }
        ResultMatch match = resultMapper.lockResultMatch(matchId).orElse(null);
        if (match == null) {
            return MatchResultResponse.notFound();
        }
        Captains captains = captainsOf(match, userId);
        List<ResultSubmission> active = resultMapper.findActiveSubmissions(matchId, SubmissionStatus.REPLACED.name());
        ResultSubmission mine = submissionOf(active, captains.mine());
        ResultSubmission opponent = submissionOf(active, captains.opponent());
        Optional<MatchResultResponse> rejection = checkSubmittable(match, mine, now)
                .or(() -> checkSameResubmission(mine, result));
        if (rejection.isPresent()) {
            return rejection.get();
        }

        if (mine != null) {
            resultMapper.updateSubmissionStatus(mine.getSubmissionId(), SubmissionStatus.REPLACED.name());
        }
        Long myParticipantId = captains.mine().getParticipantId();
        if (opponent == null) {
            resultMapper.insertSubmission(matchId, myParticipantId, result.name(),
                    SubmissionStatus.SUBMITTED.name(), now);
            resultMapper.markResult(matchId, ResultStatus.SUBMITTED.name(), null, null);
            return MatchResultResponse.of(Outcome.SUBMITTED, "결과를 제출했습니다. 상대 팀장의 제출을 기다립니다.");
        }

        boolean same = result.name().equals(opponent.getReportedResult());
        String status = (same ? SubmissionStatus.CONFIRMED : SubmissionStatus.CONFLICT).name();
        resultMapper.insertSubmission(matchId, myParticipantId, result.name(), status, now);
        resultMapper.updateSubmissionStatus(opponent.getSubmissionId(), status);
        if (!same) {
            resultMapper.markResult(matchId, ResultStatus.CONFLICT.name(), null, null);
            matchNotifier.notifyAfterCommit(List.of(captains.mine().getUserId(), captains.opponent().getUserId()),
                    matchId, NotificationType.MATCH_RESULT_CONFLICT, "경기 결과 확인 요청",
                    "상대 팀장과 경기 결과가 다릅니다. 제출 마감 전까지 다시 확인해 주세요.");
            return MatchResultResponse.of(Outcome.CONFLICT, "상대 팀장과 결과가 다릅니다. 마감 전까지 다시 제출할 수 있습니다.");
        }

        resultMapper.markResult(matchId, ResultStatus.CONFIRMED.name(), result.name(), now);
        match.setResultStatus(ResultStatus.CONFIRMED.name());
        match.setFinalResult(result.name());
        if (applyRatingIfReady(match, now)) {
            return MatchResultResponse.of(Outcome.CONFIRMED, "두 팀장의 결과가 같아 경기 결과를 확정하고 레이팅에 반영했습니다.");
        }
        return MatchResultResponse.of(Outcome.CONFIRMED,
                "두 팀장의 결과가 같아 경기 결과를 확정했습니다. 두 팀장의 평가가 모두 들어오면 레이팅에 반영됩니다.");
    }

    /**
     * 결과 확정과 평가 제출 뒤에 부른다. 두 팀장의 평가까지 모두 들어왔으면 마감을 기다리지 않고 반영한다.
     * 두 제출 모두 social_match를 잠그고 하나씩 처리되므로, 마지막으로 조건을 채운 제출이 한 번만 반영한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean applyRatingIfReady(ResultMatch lockedMatch, LocalDateTime now) {
        if (!isReadyToApplyNow(lockedMatch)) {
            return false;
        }
        applyRating(lockedMatch, now);
        return true;
    }

    /** 노쇼 경기를 담당 시설 관리자가 레이팅 반영에서 뺀다. */
    @Transactional
    public MatchResultResponse excludeRating(long matchId, long managerUserId, LocalDateTime now) {
        ResultMatch match = resultMapper.lockResultMatch(matchId).orElse(null);
        if (match == null) {
            return MatchResultResponse.notFound();
        }
        if (!assignmentMapper.isFacilityManager(match.getFacilityId(), managerUserId)) {
            throw new AccessDeniedException("담당 시설의 매칭방이 아닙니다.");
        }
        if (!PLAYED_STATUSES.contains(match.getStatus())) {
            return MatchResultResponse.of(Outcome.INVALID_STATUS, "팀 배정이 끝난 경기만 레이팅에서 제외할 수 있습니다.");
        }
        if (match.getStartAt().isAfter(now)) {
            return MatchResultResponse.of(Outcome.NOT_STARTED, "경기가 시작된 뒤에 레이팅에서 제외할 수 있습니다.");
        }
        if (RatingStatus.APPLIED.name().equals(match.getRatingStatus())) {
            return MatchResultResponse.of(Outcome.ALREADY_APPLIED, "이미 레이팅에 반영된 경기입니다.");
        }
        if (RatingStatus.EXCLUDED.name().equals(match.getRatingStatus())) {
            return MatchResultResponse.of(Outcome.ALREADY_EXCLUDED, "이미 레이팅에서 제외된 경기입니다.");
        }
        markRatingProcessed(match, RatingStatus.EXCLUDED, now, managerUserId);
        return MatchResultResponse.of(Outcome.EXCLUDED, "레이팅 반영에서 제외했습니다.");
    }

    @Transactional(readOnly = true)
    public List<Long> findEndedMatchIds(LocalDateTime now) {
        return resultMapper.findEndedMatchIds(MatchStatus.MATCHED.dbValue(), RatingStatus.APPLIED.name(),
                RatingStatus.EXCLUDED.name(), now);
    }

    /**
     * 종료 시각이 지나고 레이팅 반영·제외까지 끝난 매칭 완료 경기를 종료로 바꾼다. 결과·평가 처리 중인 경기는
     * 아직 진행 중으로 보고 매칭 완료로 둔다. 평가는 WAITING일 때만 받으므로 여기서는 레이팅 상태만 본다.
     * 레이팅 상태는 WAITING에서 한 번만 바뀌고, 조건부 UPDATE가 세 조건을 다시 확인하므로 따로 잠그지 않는다.
     *
     * <p>조건부 UPDATE가 1행을 바꾼 실행에서만 확정 참가자에게 결과 산정 완료를 알린다. 이미 종료된 경기는
     * 조건이 맞지 않아 0행이므로, 중복 종료 처리에서는 알림이 나가지 않는다.</p>
     */
    @Transactional
    public boolean markEnded(long matchId, LocalDateTime now) {
        boolean ended = resultMapper.markEnded(matchId, MatchStatus.CLOSED.dbValue(), MatchStatus.MATCHED.dbValue(),
                RatingStatus.APPLIED.name(), RatingStatus.EXCLUDED.name(), now) == 1;
        if (ended) {
            notifyResultCompleted(matchId);
        }
        return ended;
    }

    // 레이팅 반영·제외는 같은 안내로 한 번만 알린다. 수신자는 확정 참가자이며 중복 회원은 제거한다.
    private void notifyResultCompleted(long matchId) {
        List<Long> recipients = AutoAssignmentService.confirmedOf(participantMapper.findByMatchId(matchId)).stream()
                .map(MatchParticipant::getUserId)
                .distinct()
                .toList();
        matchNotifier.notifyAfterCommit(recipients, matchId, NotificationType.MATCH_RESULT_COMPLETED,
                "경기 결과 산정 완료", RESULT_COMPLETED_BODY);
        log.info("경기 결과 산정 완료 알림 예약: matchId={}, 수신자={}명", matchId, recipients.size());
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredMatchIds(LocalDateTime now) {
        return resultMapper.findExpiredMatchIds(RatingStatus.WAITING.name(),
                now.minus(RatingPolicy.RESULT_SUBMIT_PERIOD));
    }

    /** 제출 마감이 지난 경기를 확정 결과와 그때까지 들어온 평가로 반영하거나 레이팅에서 뺀다. 스케줄러가 경기마다 호출한다. */
    @Transactional
    public DeadlineResult closeResult(long matchId, LocalDateTime now) {
        ResultMatch match = resultMapper.lockResultMatch(matchId).orElse(null);
        // 조회와 잠금 사이에 결과가 반영되거나 관리자가 제외했을 수 있어 잠근 값으로 다시 본다.
        if (match == null || !RatingStatus.WAITING.name().equals(match.getRatingStatus())
                || deadlineOf(match).isAfter(now)) {
            log.info("결과 마감 처리 건너뜀: matchId={}", matchId);
            return DeadlineResult.SKIPPED;
        }
        if (isReadyToApplyAtDeadline(match)) {
            applyRating(match, now);
            log.info("결과 마감 레이팅 반영: matchId={}", matchId);
            return DeadlineResult.APPLIED;
        }
        markRatingProcessed(match, RatingStatus.EXCLUDED, now, null);
        log.info("결과 마감 레이팅 제외: matchId={}, resultStatus={}", matchId, match.getResultStatus());
        return DeadlineResult.EXCLUDED;
    }

    /** 마감 전 리마인더 시각이 이번 실행의 1분 구간에 드는 경기에서, 결과나 평가를 아직 내지 않은 팀장에게 알린다. */
    public void sendReminders(LocalDateTime now) {
        // 분 단위로 잘라야 실행이 몇 초 늦어져도 이어지는 실행의 구간이 겹치거나 비지 않는다.
        LocalDateTime windowEnd = now.truncatedTo(ChronoUnit.MINUTES);
        Duration sinceEnd = RatingPolicy.RESULT_SUBMIT_PERIOD.minus(RatingPolicy.REMINDER_BEFORE_DEADLINE);
        List<ReminderTarget> targets = resultMapper.findReminderTargets(
                MatchStatus.MATCHED.dbValue(), MatchStatus.CLOSED.dbValue(), RatingStatus.WAITING.name(),
                ParticipationStatus.CONFIRMED.dbValue(),
                windowEnd.minusMinutes(1).minus(sinceEnd), windowEnd.minus(sinceEnd));
        String body = "제출 마감까지 %d시간 남았습니다. 아직 내지 않은 경기 결과나 평가를 제출해 주세요."
                .formatted(RatingPolicy.REMINDER_BEFORE_DEADLINE.toHours());
        targets.stream()
                .collect(Collectors.groupingBy(ReminderTarget::getMatchId, LinkedHashMap::new,
                        Collectors.mapping(ReminderTarget::getUserId, Collectors.toList())))
                .forEach((matchId, userIds) -> matchNotifier.notifyAfterCommit(userIds, matchId,
                        NotificationType.MATCH_RESULT_REMINDER, "경기 결과·평가 미제출", body));
    }

    // 마감 전에는 두 팀장의 평가까지 기다린다. 결과만 확정됐을 때 반영하면 늦게 낸 팀장의 평가가 빠진다.
    private boolean isReadyToApplyNow(ResultMatch match) {
        if (!isReadyToApplyAtDeadline(match)) {
            return false;
        }
        List<Long> evaluatorIds = evaluationMapper.findEvaluatorIds(match.getMatchId());
        return match.getCaptainAParticipantId() != null && match.getCaptainBParticipantId() != null
                && evaluatorIds.contains(match.getCaptainAParticipantId())
                && evaluatorIds.contains(match.getCaptainBParticipantId());
    }

    // 마감 때는 결과만 확정되면 그때까지 들어온 평가로 반영한다. 여기에 평가 조건을 더하면 평가가 덜 들어온 경기가
    // 제외되므로 즉시 반영 조건과 나눠 둔다.
    private boolean isReadyToApplyAtDeadline(ResultMatch match) {
        return RatingStatus.WAITING.name().equals(match.getRatingStatus())
                && ResultStatus.CONFIRMED.name().equals(match.getResultStatus());
    }

    private void applyRating(ResultMatch match, LocalDateTime now) {
        ratingApplyService.apply(match.getMatchId(), match.getSportId(),
                MatchResult.valueOf(match.getFinalResult()), evaluationScoresOf(match.getMatchId()), now);
        markRatingProcessed(match, RatingStatus.APPLIED, now, null);
    }

    /** 참가자 ID별 평가 점수. 점수를 하나도 받지 못한 참가자는 넣지 않는다. */
    private Map<Long, BigDecimal> evaluationScoresOf(long matchId) {
        Map<Long, List<BigDecimal>> received = evaluationMapper.findReceivedEvaluations(matchId).stream()
                .collect(Collectors.groupingBy(ReceivedEvaluation::getTargetParticipantId,
                        Collectors.mapping(ReceivedEvaluation::getOverallScore, Collectors.toList())));
        Map<Long, BigDecimal> scores = new HashMap<>();
        received.forEach((participantId, overallScores) -> {
            BigDecimal score = calculator.playerScore(overallScores);
            if (score != null) {
                scores.put(participantId, score);
            }
        });
        return scores;
    }

    private void markRatingProcessed(ResultMatch match, RatingStatus status, LocalDateTime now, Long reviewedBy) {
        int updated = resultMapper.markRatingProcessed(match.getMatchId(), status.name(), now, reviewedBy,
                match.getRatingStatus());
        // 잠금 아래에서 상태를 확인했으므로 행 수가 다르면 데이터 오류다. 예외로 전체를 롤백한다.
        if (updated != 1) {
            throw new IllegalStateException("레이팅 처리 상태 저장에 실패했습니다. matchId=" + match.getMatchId());
        }
    }

    private Optional<MatchResultResponse> checkSubmittable(ResultMatch match, ResultSubmission mine,
                                                           LocalDateTime now) {
        if (!PLAYED_STATUSES.contains(match.getStatus())) {
            return Optional.of(MatchResultResponse.of(Outcome.INVALID_STATUS, "결과를 제출할 수 있는 경기가 아닙니다."));
        }
        if (now.isBefore(match.getEndAt()) || !now.isBefore(deadlineOf(match))) {
            return Optional.of(MatchResultResponse.of(Outcome.OUT_OF_PERIOD, "경기 종료 후 %d시간 안에만 결과를 제출할 수 있습니다."
                    .formatted(RatingPolicy.RESULT_SUBMIT_PERIOD.toHours())));
        }
        if (!RatingStatus.WAITING.name().equals(match.getRatingStatus())) {
            return Optional.of(MatchResultResponse.of(Outcome.RATING_CLOSED, "레이팅 처리가 끝난 경기입니다."));
        }
        if (mine != null && !SubmissionStatus.CONFLICT.name().equals(mine.getSubmissionStatus())) {
            return Optional.of(MatchResultResponse.of(Outcome.ALREADY_SUBMITTED,
                    "이미 결과를 제출했습니다. 상대 팀장과 결과가 다를 때만 다시 제출할 수 있습니다."));
        }
        return Optional.empty();
    }

    // 불일치 상태에서 직전과 같은 결과를 다시 내면 상태는 그대로인데 두 팀장에게 알림만 반복된다.
    // 조회 화면의 canSubmit은 다른 결과를 낼 수 있어 참이어야 하므로 checkSubmittable과 나눠 둔다.
    private static Optional<MatchResultResponse> checkSameResubmission(ResultSubmission mine, MatchResult result) {
        if (mine != null && SubmissionStatus.CONFLICT.name().equals(mine.getSubmissionStatus())
                && result.name().equals(mine.getReportedResult())) {
            return Optional.of(MatchResultResponse.of(Outcome.ALREADY_SUBMITTED,
                    "이미 같은 결과를 제출했습니다. 결과를 바꾸려면 다른 결과를 선택해 주세요."));
        }
        return Optional.empty();
    }

    private LocalDateTime deadlineOf(ResultMatch match) {
        return match.getEndAt().plus(RatingPolicy.RESULT_SUBMIT_PERIOD);
    }

    private Captains captainsOf(ResultMatch match, long userId) {
        Map<Long, MatchParticipant> confirmed = AutoAssignmentService
                .confirmedOf(participantMapper.findByMatchId(match.getMatchId())).stream()
                .collect(Collectors.toMap(MatchParticipant::getParticipantId, Function.identity()));
        MatchParticipant captainA = confirmed.get(match.getCaptainAParticipantId());
        MatchParticipant captainB = confirmed.get(match.getCaptainBParticipantId());
        if (captainA != null && Objects.equals(captainA.getUserId(), userId)) {
            return new Captains(SIDE_A, captainA, captainB);
        }
        if (captainB != null && Objects.equals(captainB.getUserId(), userId)) {
            return new Captains(SIDE_B, captainB, captainA);
        }
        throw new AccessDeniedException("이 경기의 팀장만 결과를 조회하고 제출할 수 있습니다.");
    }

    private static ResultSubmission submissionOf(List<ResultSubmission> submissions, MatchParticipant captain) {
        if (captain == null) {
            return null;
        }
        return submissions.stream()
                .filter(s -> captain.getParticipantId().equals(s.getCaptainParticipantId()))
                .findFirst()
                .orElse(null);
    }

    /** 확정 참가 중인 팀장. 상대 팀장이 없으면 opponent는 null이다. */
    private record Captains(String side, MatchParticipant mine, MatchParticipant opponent) {
    }
}
