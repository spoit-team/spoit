package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.TeamCandidate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 구성이 끝난 한 팀에서 팀장을 고른다. 자동·수동 배정이 같은 규칙을 쓰도록 배정 방식과 분리했다.
 *
 * <p>MMR이 가장 높은 참가자를 고르고, 최고 MMR이 같으면 participantId가 작은 참가자를 고른다.</p>
 */
@Component
public class TeamCaptainSelector {

    private static final Comparator<TeamCandidate> CAPTAIN_ORDER =
            Comparator.comparing(TeamCandidate::mmr, Comparator.reverseOrder())
                    .thenComparing(TeamCandidate::participantId);

    /** 팀장의 participantId를 반환한다. 입력 순서는 결과에 영향을 주지 않는다. */
    public Long selectCaptain(List<TeamCandidate> teamMembers) {
        if (teamMembers == null || teamMembers.isEmpty()) {
            throw new IllegalArgumentException("팀원이 한 명 이상 있어야 팀장을 정할 수 있습니다.");
        }
        Set<Long> ids = new HashSet<>();
        for (TeamCandidate member : teamMembers) {
            if (member == null) {
                throw new IllegalArgumentException("팀원 목록에 null이 있습니다.");
            }
            if (!ids.add(member.participantId())) {
                throw new IllegalArgumentException("팀원 participantId가 중복됩니다: " + member.participantId());
            }
        }
        return teamMembers.stream().min(CAPTAIN_ORDER).orElseThrow().participantId();
    }
}
