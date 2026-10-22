package co.kr.spoit.mat.dto;

import co.kr.spoit.mat.domain.MatchParticipant;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 참가 상태와 신청 당시 MMR을 그대로 전달하는 조회 DTO이다. */
public record MatchParticipantResponse(
        Long participantId,
        Long applicationId,
        Long matchId,
        Long userId,
        String participationStatus,
        String matchSide,
        BigDecimal entryMmr,
        LocalDateTime joinedAt,
        LocalDateTime cancelledAt) {

    public static MatchParticipantResponse from(MatchParticipant participant) {
        return new MatchParticipantResponse(
                participant.getParticipantId(),
                participant.getApplicationId(),
                participant.getMatchId(),
                participant.getUserId(),
                participant.getParticipationStatus(),
                participant.getMatchSide(),
                participant.getEntryMmr(),
                participant.getJoinedAt(),
                participant.getCancelledAt());
    }
}
