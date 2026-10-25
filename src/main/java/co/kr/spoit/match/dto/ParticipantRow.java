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
}
