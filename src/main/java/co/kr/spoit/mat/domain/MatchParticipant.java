package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 경기 참가자와 신청 당시 MMR을 나타낸다. */
@Getter
@Setter
@NoArgsConstructor
public class MatchParticipant {

    private Long participantId;
    private Long applicationId;
    private Long matchId;
    private Long userId;
    private String participationStatus;
    private String matchSide;
    private BigDecimal entryMmr;
    private LocalDateTime joinedAt;
    private LocalDateTime cancelledAt;
}
