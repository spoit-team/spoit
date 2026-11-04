package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.MatchParticipant;
import co.kr.spoit.mat.domain.MatchResult;
import co.kr.spoit.mat.domain.RatingPolicy;
import co.kr.spoit.mat.domain.RatingStatus;
import co.kr.spoit.mat.domain.ReminderTarget;
import co.kr.spoit.mat.domain.ResultMatch;
import co.kr.spoit.mat.domain.ResultStatus;
import co.kr.spoit.mat.domain.ResultSubmission;
import co.kr.spoit.mat.domain.SubmissionStatus;
import co.kr.spoit.mat.dto.MatchResultResponse;
import co.kr.spoit.mat.dto.MatchResultResponse.Outcome;
import co.kr.spoit.mat.dto.MatchResultView;
import co.kr.spoit.mat.mapper.MatchAssignmentMapper;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import co.kr.spoit.mat.mapper.MatchResultMapper;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.notification.domain.NotificationType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종료 시각 뒤 처리: 팀장 결과 제출, 제출 마감, 리마인더, 레이팅 제외, 처리가 끝난 경기의 종료 전환.
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
    private static final String SIDE_A = "A";
    private static final String SIDE_B = "B";

    private final MatchResultMapper resultMapper;
    private final MatchParticipantMapper participantMapper;
    private final MatchAssignmentMapper assignmentMapper;
    private final RatingApplyService ratingApplyService;
    private final MatchNotifier matchNotifier;

    public MatchResultService(MatchResultMapper resultMapper,
                              MatchParticipantMapper participantMapper,
                              MatchAssignmentMapper assignmentMapper,
                              RatingApplyService ratingApplyService,
                              MatchNotifier matchNotifier) {
        this.resultMapper = resultMapper;
        this.participantMapper = participantMapper;
        this.assignmentMapper = assignmentMapper;
        this.ratingApplyService = ratingApplyService;
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
        Optional<MatchResultResponse> rejection = checkSubmittable(match, mine, now);
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
        if (isRatingReady(match)) {
            applyRating(match, now);
        }
        return MatchResultResponse.of(Outcome.CONFIRMED, "두 팀장의 결과가 같아 경기 결과를 확정했습니다.");
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
     * 아직 진행 중으로 보고 매칭 완료로 둔다. 평가 완료 조건은 isRatingReady에 더해지므로 여기서는 레이팅 상태만 본다.
     * 레이팅 상태는 WAITING에서 한 번만 바뀌고, 조건부 UPDATE가 세 조건을 다시 확인하므로 따로 잠그지 않는다.
     */
    @Transactional
    public boolean markEnded(long matchId, LocalDateTime now) {
        return resultMapper.markEnded(matchId, MatchStatus.CLOSED.dbValue(), MatchStatus.MATCHED.dbValue(),
                RatingStatus.APPLIED.name(), RatingStatus.EXCLUDED.name(), now) == 1;
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredMatchIds(LocalDateTime now) {
        return resultMapper.findExpiredMatchIds(RatingStatus.WAITING.name(),
                now.minus(RatingPolicy.RESULT_SUBMIT_PERIOD));
    }

    /** 제출 마감이 지난 경기를 확정 결과로 반영하거나 레이팅에서 뺀다. 스케줄러가 경기마다 호출한다. */
    @Transactional
    public DeadlineResult closeResult(long matchId, LocalDateTime now) {
        ResultMatch match = resultMapper.lockResultMatch(matchId).orElse(null);
        // 조회와 잠금 사이에 결과가 반영되거나 관리자가 제외했을 수 있어 잠근 값으로 다시 본다.
        if (match == null || !RatingStatus.WAITING.name().equals(match.getRatingStatus())
                || deadlineOf(match).isAfter(now)) {
            log.info("결과 마감 처리 건너뜀: matchId={}", matchId);
            return DeadlineResult.SKIPPED;
        }
        if (isRatingReady(match)) {
            applyRating(match, now);
            log.info("결과 마감 레이팅 반영: matchId={}", matchId);
            return DeadlineResult.APPLIED;
        }
        markRatingProcessed(match, RatingStatus.EXCLUDED, now, null);
        log.info("결과 마감 레이팅 제외: matchId={}, resultStatus={}", matchId, match.getResultStatus());
        return DeadlineResult.EXCLUDED;
    }

    /** 마감 전 리마인더 시각이 이번 실행의 1분 구간에 드는 경기에서, 제출 행이 없는 팀장에게 알린다. */
    public void sendReminders(LocalDateTime now) {
        // 분 단위로 잘라야 실행이 몇 초 늦어져도 이어지는 실행의 구간이 겹치거나 비지 않는다.
        LocalDateTime windowEnd = now.truncatedTo(ChronoUnit.MINUTES);
        Duration sinceEnd = RatingPolicy.RESULT_SUBMIT_PERIOD.minus(RatingPolicy.REMINDER_BEFORE_DEADLINE);
        List<ReminderTarget> targets = resultMapper.findReminderTargets(
                MatchStatus.MATCHED.dbValue(), MatchStatus.CLOSED.dbValue(), RatingStatus.WAITING.name(),
                ParticipationStatus.CONFIRMED.dbValue(),
                windowEnd.minusMinutes(1).minus(sinceEnd), windowEnd.minus(sinceEnd));
        String body = "제출 마감까지 %d시간 남았습니다. 경기 결과를 제출해 주세요."
                .formatted(RatingPolicy.REMINDER_BEFORE_DEADLINE.toHours());
        targets.stream()
                .collect(Collectors.groupingBy(ReminderTarget::getMatchId, LinkedHashMap::new,
                        Collectors.mapping(ReminderTarget::getUserId, Collectors.toList())))
                .forEach((matchId, userIds) -> matchNotifier.notifyAfterCommit(userIds, matchId,
                        NotificationType.MATCH_RESULT_REMINDER, "경기 결과 미제출", body));
    }

    // 레이팅 반영 조건은 여기서만 판단한다. 평가 보정이 들어오면 평가 완료 조건을 이 메서드에 더한다.
    private boolean isRatingReady(ResultMatch match) {
        return RatingStatus.WAITING.name().equals(match.getRatingStatus())
                && ResultStatus.CONFIRMED.name().equals(match.getResultStatus());
    }

    private void applyRating(ResultMatch match, LocalDateTime now) {
        ratingApplyService.apply(match.getMatchId(), match.getSportId(),
                MatchResult.valueOf(match.getFinalResult()), now);
        markRatingProcessed(match, RatingStatus.APPLIED, now, null);
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
