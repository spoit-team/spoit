package co.kr.spoit.mat.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 팀 배정 화면 조회 응답. assignmentMethod는 배정이 확정됐을 때만 채운다. */
public record TeamAssignmentView(
        Long matchId,
        LocalDateTime startAt,
        int teamCapacity,
        int confirmedCount,
        boolean assigned,
        String assignmentMethod,
        boolean canAssign,
        boolean canReset,
        List<Participant> participants) {

    /** clubName은 동호회 신청으로 들어온 참가자만 채운다. */
    public record Participant(Long participantId, String nickname, String clubName, BigDecimal entryMmr,
            String side, boolean captain) {
    }
}
