package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.TeamAssignment;
import co.kr.spoit.mat.domain.TeamCandidate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 확정 참가자를 양 팀 MMR 합계 차이가 가장 작은 두 팀으로 나누고 팀별 팀장을 고른다.
 *
 * <p>참가자를 participantId 순으로 정렬한 뒤 첫 참가자를 A팀에 고정하고, 나머지에서 A팀원을 고르는 조합을 모두 탐색한다.
 * 최적 구성이 여럿이면 A팀 참가자 ID 목록이 사전순으로 가장 앞선 구성을 고르므로,
 * 입력 순서와 관계없이 같은 참가자 집합에는 항상 같은 결과를 낸다.</p>
 *
 * <p>입력은 모두 확정 참가자라고 가정한다. 대기 참가자 제외, 종목별 경기 방식 검증, 배정 저장은 호출하는 서비스가 맡는다.</p>
 */
@Component
public class AutoTeamAssigner {

    /** 11vs11까지 지원한다. 22명이면 C(21,10) = 352,716가지 구성을 탐색한다. */
    public static final int MAX_TEAM_CAPACITY = 11;

    private final TeamCaptainSelector captainSelector;

    public AutoTeamAssigner(TeamCaptainSelector captainSelector) {
        this.captainSelector = captainSelector;
    }

    public TeamAssignment assign(List<TeamCandidate> participants, int teamCapacity) {
        if (teamCapacity < 1 || teamCapacity > MAX_TEAM_CAPACITY) {
            throw new IllegalArgumentException("팀당 인원은 1~" + MAX_TEAM_CAPACITY + "명이어야 합니다: " + teamCapacity);
        }
        if (participants == null) {
            throw new IllegalArgumentException("참가자 목록은 null일 수 없습니다.");
        }
        if (participants.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("참가자 목록에 null이 있습니다.");
        }
        List<TeamCandidate> sorted = participants.stream()
                .sorted(Comparator.comparing(TeamCandidate::participantId))
                .toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).participantId().equals(sorted.get(i - 1).participantId())) {
                throw new IllegalArgumentException("참가자 participantId가 중복됩니다: " + sorted.get(i).participantId());
            }
        }
        if (sorted.size() != teamCapacity * 2) {
            throw new IllegalArgumentException("참가자 수는 팀당 인원의 두 배(" + teamCapacity * 2
                    + "명)여야 합니다. 현재 " + sorted.size() + "명");
        }

        long[] mmrCents = new long[sorted.size()];
        for (int i = 0; i < mmrCents.length; i++) {
            mmrCents[i] = sorted.get(i).mmrCents();
        }
        int teamAMask = new Search(mmrCents, teamCapacity).run();
        return toAssignment(sorted, teamAMask, teamCapacity);
    }

    private TeamAssignment toAssignment(List<TeamCandidate> sorted, int teamAMask, int teamCapacity) {
        List<TeamCandidate> teamA = new ArrayList<>(teamCapacity);
        List<TeamCandidate> teamB = new ArrayList<>(teamCapacity);
        long teamASum = 0;
        long teamBSum = 0;
        for (int i = 0; i < sorted.size(); i++) {
            TeamCandidate candidate = sorted.get(i);
            if (((teamAMask >>> i) & 1) == 1) {
                teamA.add(candidate);
                teamASum += candidate.mmrCents();
            } else {
                teamB.add(candidate);
                teamBSum += candidate.mmrCents();
            }
        }
        return new TeamAssignment(
                teamA.stream().map(TeamCandidate::participantId).toList(),
                teamB.stream().map(TeamCandidate::participantId).toList(),
                BigDecimal.valueOf(teamASum, 2),
                BigDecimal.valueOf(teamBSum, 2),
                average(teamASum, teamCapacity),
                average(teamBSum, teamCapacity),
                BigDecimal.valueOf(Math.abs(teamASum - teamBSum), 2),
                captainSelector.selectCaptain(teamA),
                captainSelector.selectCaptain(teamB));
    }

    private BigDecimal average(long sumCents, int teamCapacity) {
        return BigDecimal.valueOf(sumCents, 2).divide(BigDecimal.valueOf(teamCapacity), 2, RoundingMode.HALF_UP);
    }

    /** 한 번의 배정 계산 상태. 조합마다 객체를 만들지 않도록 A팀 비트마스크와 long 합계만 쓴다. */
    private static final class Search {

        private final long[] mmrCents;
        private final int teamCapacity;
        private final long totalCents;
        private int bestMask;
        private long bestDifference = Long.MAX_VALUE;

        Search(long[] mmrCents, int teamCapacity) {
            this.mmrCents = mmrCents;
            this.teamCapacity = teamCapacity;
            long total = 0;
            for (long value : mmrCents) {
                total += value;
            }
            this.totalCents = total;
        }

        int run() {
            // 0번(가장 작은 participantId)을 A팀에 고정해 A/B만 뒤바뀐 같은 구성을 한 번만 본다.
            visit(1, 1, 1, mmrCents[0]);
            return bestMask;
        }

        /** index번 참가자부터 A팀 포함 여부를 정한다. 포함하는 쪽을 먼저 보므로 A팀 ID 목록의 사전순으로 탐색한다. */
        private void visit(int index, int chosen, int mask, long teamASum) {
            if (bestDifference == 0) {
                // 더 작은 차이는 없고, 먼저 찾은 구성이 사전순으로 앞선다.
                return;
            }
            if (chosen == teamCapacity) {
                // |A - B| = |2A - 전체|
                long difference = Math.abs(2 * teamASum - totalCents);
                if (difference < bestDifference) {
                    bestDifference = difference;
                    bestMask = mask;
                }
                return;
            }
            if (mmrCents.length - index < teamCapacity - chosen) {
                return;
            }
            visit(index + 1, chosen + 1, mask | (1 << index), teamASum + mmrCents[index]);
            visit(index + 1, chosen, mask, teamASum);
        }
    }
}
