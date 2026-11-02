package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 참가자 한 명의 sport_rating_history 행. 평가 보정이 없어 elo_delta가 곧 최종 증감이다. */
public record RatingChange(
        Long matchId,
        Long participantId,
        Long userId,
        Long sportId,
        BigDecimal mmrBefore,
        BigDecimal mmrAfter,
        BigDecimal eloDelta,
        BigDecimal actualScore,
        BigDecimal expectedScore,
        BigDecimal teamAvgMmrA,
        BigDecimal teamAvgMmrB,
        String algorithmVersion,
        LocalDateTime calculatedAt
) {
}
