package co.kr.spoit.match.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** findMatchDetail 조회 결과: 목록 컬럼 + 상세에서만 쓰는 팀 정원·팀장 */
@Getter
@Setter
@NoArgsConstructor
public class MatchDetailRow extends MatchRow {

    private int teamACapacity;
    private int teamBCapacity;
    private Long captainAParticipantId;
    private Long captainBParticipantId;
}
