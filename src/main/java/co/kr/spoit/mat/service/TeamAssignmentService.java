package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.AssignmentMatch;
import co.kr.spoit.mat.domain.AssignmentMember;
import co.kr.spoit.mat.domain.AssignmentMethod;
import co.kr.spoit.mat.domain.MatchParticipant;
import co.kr.spoit.mat.domain.TeamAssignment;
import co.kr.spoit.mat.domain.TeamCandidate;
import co.kr.spoit.mat.dto.AssignmentResult;
import co.kr.spoit.mat.dto.AssignmentResult.Outcome;
import co.kr.spoit.mat.dto.TeamAssignmentView;
import co.kr.spoit.mat.mapper.MatchAssignmentMapper;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 배정 확정·취소. 배정 저장은 이 서비스에서만 한다.
 *
 * <p>저장·취소는 매칭방 행을 잠근 뒤 다시 읽은 값으로 검사하므로,
 * 같은 경기에 확정 요청이 동시에 와도 하나만 저장되고 나머지는 ALREADY_ASSIGNED를 받는다.</p>
 */
@Slf4j
@Service
public class TeamAssignmentService {

    public enum StartResult {
        ASSIGNED,
        CANCELLED,
        SKIPPED
    }

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final String SIDE_A = "A";
    private static final String SIDE_B = "B";

    private final MatchAssignmentMapper assignmentMapper;
    private final MatchParticipantMapper participantMapper;
    private final AutoAssignmentService autoAssignmentService;
    private final TeamCaptainSelector captainSelector;

    public TeamAssignmentService(MatchAssignmentMapper assignmentMapper,
                                 MatchParticipantMapper participantMapper,
                                 AutoAssignmentService autoAssignmentService,
                                 TeamCaptainSelector captainSelector) {
        this.assignmentMapper = assignmentMapper;
        this.participantMapper = participantMapper;
        this.autoAssignmentService = autoAssignmentService;
        this.captainSelector = captainSelector;
    }

    /** 경기가 없으면 empty */
    @Transactional(readOnly = true)
    public Optional<TeamAssignmentView> getView(long matchId, long managerUserId) {
        AssignmentMatch match = assignmentMapper.findAssignmentMatch(matchId).orElse(null);
        if (match == null) {
            return Optional.empty();
        }
        checkManager(match, managerUserId);

        boolean started = isStarted(match);
        boolean assigned = MatchStatus.MATCHED.dbValue().equals(match.getStatus());
        boolean canAssign = !started && autoAssignmentService
                .checkAssignable(match, participantMapper.findByMatchId(matchId)).isEmpty();
        List<TeamAssignmentView.Participant> participants = assignmentMapper
                .findConfirmedMembers(matchId, ParticipationStatus.CONFIRMED.dbValue()).stream()
                .map(m -> new TeamAssignmentView.Participant(m.getParticipantId(), m.getNickname(),
                        m.getEntryMmr(), m.getMatchSide(), isCaptain(match, m)))
                .toList();
        return Optional.of(new TeamAssignmentView(matchId, match.getStartAt(), match.getTeamACapacity(),
                participants.size(), assigned, assigned ? match.getAssignmentMethod() : null,
                canAssign, assigned && !started, participants));
    }

    @Transactional
    public AssignmentResult confirmAuto(long matchId, long managerUserId) {
        AssignmentMatch match = assignmentMapper.lockAssignmentMatch(matchId).orElse(null);
        Optional<AssignmentResult> rejection = checkRequest(match, managerUserId);
        if (rejection.isPresent()) {
            return rejection.get();
        }
        AssignmentResult result = autoAssignmentService.evaluate(match, participantMapper.findByMatchId(matchId));
        if (result.outcome() != Outcome.READY) {
            return result;
        }
        TeamAssignment assignment = result.assignment();
        save(match, assignment.teamAParticipantIds(), assignment.teamBParticipantIds(),
                assignment.captainAParticipantId(), assignment.captainBParticipantId(),
                AssignmentMethod.AUTO, managerUserId, LocalDateTime.now(ZONE));
        return AssignmentResult.assigned();
    }

    @Transactional
    public AssignmentResult confirmManual(long matchId, long managerUserId, List<Long> teamA, List<Long> teamB) {
        AssignmentMatch match = assignmentMapper.lockAssignmentMatch(matchId).orElse(null);
        Optional<AssignmentResult> rejection = checkRequest(match, managerUserId);
        if (rejection.isPresent()) {
            return rejection.get();
        }
        List<MatchParticipant> participants = participantMapper.findByMatchId(matchId);
        rejection = autoAssignmentService.checkAssignable(match, participants);
        if (rejection.isPresent()) {
            return rejection.get();
        }

        Map<Long, MatchParticipant> confirmed = AutoAssignmentService.confirmedOf(participants).stream()
                .collect(Collectors.toMap(MatchParticipant::getParticipantId, Function.identity()));
        if (!isValidTeams(teamA, teamB, confirmed.keySet(), match.getTeamACapacity())) {
            return AssignmentResult.rejected(Outcome.INVALID_TEAMS,
                    "확정 참가자 전원을 A팀과 B팀에 정원만큼 나눠 배정해 주세요.");
        }
        save(match, teamA, teamB, captainOf(teamA, confirmed), captainOf(teamB, confirmed),
                AssignmentMethod.MANUAL, managerUserId, LocalDateTime.now(ZONE));
        return AssignmentResult.assigned();
    }

    @Transactional
    public AssignmentResult reset(long matchId, long managerUserId) {
        AssignmentMatch match = assignmentMapper.lockAssignmentMatch(matchId).orElse(null);
        Optional<AssignmentResult> rejection = checkRequest(match, managerUserId);
        if (rejection.isPresent()) {
            return rejection.get();
        }
        if (!MatchStatus.MATCHED.dbValue().equals(match.getStatus())) {
            return AssignmentResult.rejected(Outcome.NOT_ASSIGNED, "확정된 팀 배정이 없습니다.");
        }
        assignmentMapper.clearMatchSides(matchId, ParticipationStatus.CONFIRMED.dbValue());
        int updated = assignmentMapper.clearAssignment(matchId, AssignmentMethod.MANUAL.name(),
                MatchStatus.RECRUITING.dbValue(), MatchStatus.MATCHED.dbValue());
        if (updated != 1) {
            throw new IllegalStateException("팀 배정 취소에 실패했습니다. matchId=" + matchId);
        }
        return AssignmentResult.reset();
    }

    @Transactional(readOnly = true)
    public List<Long> findStartableMatchIds(LocalDateTime now) {
        return assignmentMapper.findStartableMatchIds(MatchStatus.RECRUITING.dbValue(), now);
    }

    /**
     * 시작 시각이 된 모집 중 경기를 자동 배정하거나 취소한다. 스케줄러가 경기마다 호출한다.
     * 신청·참가자 상태는 바꾸지 않는다. 마이페이지는 경기 상태로 '매치 취소'를 표시한다.
     */
    @Transactional
    public StartResult startMatch(long matchId, LocalDateTime now) {
        AssignmentMatch match = assignmentMapper.lockAssignmentMatch(matchId).orElse(null);
        // 조회와 잠금 사이에 관리자가 확정·취소했거나 시설이 삭제됐을 수 있어 잠근 값으로 다시 본다.
        if (match == null || !MatchStatus.RECRUITING.dbValue().equals(match.getStatus())
                || match.getStartAt().isAfter(now)) {
            log.info("경기 시작 처리 건너뜀: matchId={}, status={}", matchId, match == null ? null : match.getStatus());
            return StartResult.SKIPPED;
        }
        if (!match.getEndAt().isAfter(now)) {
            return cancel(match, now, "종료 시각 경과");
        }

        AssignmentResult result = autoAssignmentService.evaluate(match, participantMapper.findByMatchId(matchId));
        switch (result.outcome()) {
            case READY -> {
                TeamAssignment assignment = result.assignment();
                save(match, assignment.teamAParticipantIds(), assignment.teamBParticipantIds(),
                        assignment.captainAParticipantId(), assignment.captainBParticipantId(),
                        AssignmentMethod.AUTO, null, now);
                log.info("경기 시작 자동 배정: matchId={}, 차이={}", matchId, assignment.mmrDifference());
                return StartResult.ASSIGNED;
            }
            case NOT_ENOUGH_PARTICIPANTS, UNSUPPORTED_CAPACITY -> {
                return cancel(match, now, result.message());
            }
            default -> {
                log.warn("경기 시작 처리 건너뜀: matchId={}, outcome={}, message={}",
                        matchId, result.outcome(), result.message());
                return StartResult.SKIPPED;
            }
        }
    }

    private StartResult cancel(AssignmentMatch match, LocalDateTime now, String reason) {
        int updated = assignmentMapper.markCancelled(match.getMatchId(), now,
                MatchStatus.CANCELLED.dbValue(), MatchStatus.RECRUITING.dbValue());
        if (updated != 1) {
            throw new IllegalStateException("경기 취소에 실패했습니다. matchId=" + match.getMatchId());
        }
        log.info("경기 시작 자동 취소: matchId={}, 사유={}", match.getMatchId(), reason);
        return StartResult.CANCELLED;
    }

    private void save(AssignmentMatch match, List<Long> teamA, List<Long> teamB, Long captainA, Long captainB,
                      AssignmentMethod method, Long assignedBy, LocalDateTime confirmedAt) {
        Long matchId = match.getMatchId();
        String confirmedStatus = ParticipationStatus.CONFIRMED.dbValue();
        int sides = assignmentMapper.updateMatchSide(matchId, teamA, SIDE_A, confirmedStatus)
                + assignmentMapper.updateMatchSide(matchId, teamB, SIDE_B, confirmedStatus);
        int matches = assignmentMapper.markAssigned(matchId, method.name(), assignedBy, confirmedAt,
                captainA, captainB, MatchStatus.MATCHED.dbValue(), MatchStatus.RECRUITING.dbValue());
        // 잠금 아래에서 검사를 마쳤으므로 행 수가 다르면 데이터 오류다. 예외로 전체를 롤백한다.
        if (sides != teamA.size() + teamB.size() || matches != 1) {
            throw new IllegalStateException("팀 배정 저장 행 수가 맞지 않습니다. matchId=" + matchId
                    + ", 참가자=" + sides + ", 매칭방=" + matches);
        }
    }

    private Optional<AssignmentResult> checkRequest(AssignmentMatch match, long managerUserId) {
        if (match == null) {
            return Optional.of(AssignmentResult.notFound());
        }
        checkManager(match, managerUserId);
        if (isStarted(match)) {
            return Optional.of(AssignmentResult.rejected(Outcome.STARTED, "이미 시작한 경기는 팀 배정을 바꿀 수 없습니다."));
        }
        return Optional.empty();
    }

    private void checkManager(AssignmentMatch match, long managerUserId) {
        if (!assignmentMapper.isFacilityManager(match.getFacilityId(), managerUserId)) {
            throw new AccessDeniedException("담당 시설의 매칭방이 아닙니다.");
        }
    }

    private boolean isStarted(AssignmentMatch match) {
        return !match.getStartAt().isAfter(LocalDateTime.now(ZONE));
    }

    private boolean isValidTeams(List<Long> teamA, List<Long> teamB, Set<Long> confirmedIds, int teamCapacity) {
        if (teamA == null || teamB == null || teamA.size() != teamCapacity || teamB.size() != teamCapacity) {
            return false;
        }
        Set<Long> requested = new HashSet<>(teamA);
        requested.addAll(teamB);
        // 합친 크기가 정원 합과 같으면 중복이 없고, 확정 참가자 ID 집합과 같으면 누락·외부 ID가 없다.
        return requested.size() == teamCapacity * 2 && requested.equals(confirmedIds);
    }

    private Long captainOf(List<Long> team, Map<Long, MatchParticipant> confirmed) {
        return captainSelector.selectCaptain(team.stream()
                .map(confirmed::get)
                .map(p -> new TeamCandidate(p.getParticipantId(), p.getEntryMmr()))
                .toList());
    }

    private boolean isCaptain(AssignmentMatch match, AssignmentMember member) {
        return Objects.equals(member.getParticipantId(), match.getCaptainAParticipantId())
                || Objects.equals(member.getParticipantId(), match.getCaptainBParticipantId());
    }
}
