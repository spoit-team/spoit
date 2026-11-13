package co.kr.spoit.match.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** findParticipants 조회 결과 */
@Getter
@Setter
@NoArgsConstructor
public class ParticipantRow {

    private Long participantId;
    private Long matchId;
    private Long userId;
    private String nickname;
    private String participationStatus;
    /** A, B, 미배정이면 null */
    private String matchSide;
    /** 신청 당시 MMR 스냅샷 */
    private BigDecimal entryMmr;
    private LocalDateTime joinedAt;
    /** 신청 시점 반영 경기 수. 상세 조회(findDetailParticipants)만 채우고 다른 조회에서는 null */
    private Integer entryRatedMatchCount;
}
