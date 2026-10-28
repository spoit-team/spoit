package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.AssignmentMatch;
import co.kr.spoit.mat.domain.MatchParticipant;
import co.kr.spoit.mat.domain.TeamCandidate;
import co.kr.spoit.mat.dto.AutoAssignmentResult;
import co.kr.spoit.mat.dto.AutoAssignmentResult.Outcome;
import co.kr.spoit.mat.mapper.MatchAssignmentMapper;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정 참가자로 자동 배정 결과를 만든다. 저장은 하지 않는다.
 *
 * <p>정원 부족, 이미 배정된 경기 같은 업무 상태는 결과로 돌려주고
 * 정원 초과나 잘못된 MMR처럼 데이터가 어긋난 경우에만 예외를 던진다.</p>
 */
@Service
@Transactional(readOnly = true)
public class AutoAssignmentService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MatchAssignmentMapper assignmentMapper;
    private final MatchParticipantMapper participantMapper;
    private final AutoTeamAssigner autoTeamAssigner;

    public AutoAssignmentService(MatchAssignmentMapper assignmentMapper,
                                 MatchParticipantMapper participantMapper,
                                 AutoTeamAssigner autoTeamAssigner) {
        this.assignmentMapper = assignmentMapper;
        this.participantMapper = participantMapper;
        this.autoTeamAssigner = autoTeamAssigner;
    }

    /** 시설 관리자 화면의 자동 배정 미리보기 */
    public AutoAssignmentResult preview(long matchId, long managerUserId) {
        AssignmentMatch match = assignmentMapper.findAssignmentMatch(matchId).orElse(null);
        if (match == null) {
            return AutoAssignmentResult.rejected(Outcome.NOT_FOUND, "매칭방 정보를 찾을 수 없습니다.");
        }
        if (!assignmentMapper.isFacilityManager(match.getFacilityId(), managerUserId)) {
            throw new AccessDeniedException("담당 시설의 매칭방이 아닙니다.");
        }
        // 시작 후 변경 금지는 화면 요청에만 적용한다. 시작 시점에 도는 스케줄러는 evaluate를 바로 호출한다.
        if (!match.getStartAt().isAfter(LocalDateTime.now(ZONE))) {
            return AutoAssignmentResult.rejected(Outcome.STARTED, "이미 시작한 경기는 팀 배정을 바꿀 수 없습니다.");
        }
        return evaluate(match, participantMapper.findByMatchId(matchId));
    }

    /**
     * 관리자 요청과 스케줄러가 같이 쓰는 판정.
     * 저장까지 하는 쪽은 매칭방 행을 잠근 뒤 다시 읽은 값으로 호출해야 한다.
     */
    public AutoAssignmentResult evaluate(AssignmentMatch match, List<MatchParticipant> participants) {
        boolean matched = MatchStatus.MATCHED.dbValue().equals(match.getStatus());
        if (!matched && !MatchStatus.RECRUITING.dbValue().equals(match.getStatus())) {
            return AutoAssignmentResult.rejected(Outcome.NOT_RECRUITING, "모집 중인 매칭방이 아닙니다.");
        }

        List<MatchParticipant> confirmed = participants.stream()
                .filter(p -> ParticipationStatus.CONFIRMED.dbValue().equals(p.getParticipationStatus()))
                .toList();
        // 취소된 참가자 행에는 예전 match_side가 남아 있을 수 있어 확정 참가자만 본다.
        if (matched || match.getAssignmentConfirmedAt() != null
                || confirmed.stream().anyMatch(p -> p.getMatchSide() != null)) {
            return AutoAssignmentResult.rejected(Outcome.ALREADY_ASSIGNED, "이미 팀 배정이 끝난 매칭방입니다.");
        }

        int teamCapacity = match.getTeamACapacity();
        if (teamCapacity != match.getTeamBCapacity()
                || teamCapacity < 1 || teamCapacity > AutoTeamAssigner.MAX_TEAM_CAPACITY) {
            return AutoAssignmentResult.rejected(Outcome.UNSUPPORTED_CAPACITY,
                    "A팀과 B팀 정원이 같고 팀당 1~" + AutoTeamAssigner.MAX_TEAM_CAPACITY
                            + "명인 경기만 자동 배정할 수 있습니다.");
        }

        int required = teamCapacity * 2;
        if (confirmed.size() < required) {
            return AutoAssignmentResult.rejected(Outcome.NOT_ENOUGH_PARTICIPANTS,
                    "확정 참가자가 " + required + "명이어야 자동 배정할 수 있습니다. 현재 " + confirmed.size() + "명");
        }
        // 신청 접수 단계에서 정원을 넘겨 받지 않으므로 초과는 데이터 오류로 본다.
        if (confirmed.size() > required) {
            throw new IllegalStateException("확정 참가자가 정원보다 많습니다. matchId=" + match.getMatchId()
                    + ", 정원=" + required + ", 확정=" + confirmed.size());
        }

        List<TeamCandidate> candidates = confirmed.stream()
                .map(p -> new TeamCandidate(p.getParticipantId(), p.getEntryMmr()))
                .toList();
        return AutoAssignmentResult.ready(autoTeamAssigner.assign(candidates, teamCapacity));
    }
}
