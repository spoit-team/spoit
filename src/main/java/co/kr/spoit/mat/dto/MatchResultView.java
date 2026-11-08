package co.kr.spoit.mat.dto;

import java.time.LocalDateTime;

/** 팀장의 경기 결과 조회 응답. opponentResult는 두 팀장의 결과가 다를 때만 채운다. */
public record MatchResultView(
        Long matchId,
        String mySide,
        String myResult,
        boolean opponentSubmitted,
        boolean conflict,
        String opponentResult,
        String finalResult,
        String ratingStatus,
        LocalDateTime deadlineAt,
        boolean canSubmit) {
}
