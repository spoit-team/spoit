package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * 자동 팀 배정 계산 결과. 저장 전 값이며 DB 상태와 무관하다.
 *
 * <p>팀별 참가자 ID는 오름차순이다. 합계와 차이는 입력 MMR을 그대로 더한 값이고,
 * 평균만 소수 셋째 자리에서 반올림(HALF_UP)한다.</p>
 */
public record TeamAssignment(
        List<Long> teamAParticipantIds,
        List<Long> teamBParticipantIds,
        BigDecimal teamAMmrSum,
        BigDecimal teamBMmrSum,
        BigDecimal teamAMmrAverage,
        BigDecimal teamBMmrAverage,
        BigDecimal mmrDifference,
        Long captainAParticipantId,
        Long captainBParticipantId) {

    public TeamAssignment {
        teamAParticipantIds = List.copyOf(teamAParticipantIds);
        teamBParticipantIds = List.copyOf(teamBParticipantIds);
    }
}
