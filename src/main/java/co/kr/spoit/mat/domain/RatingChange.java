package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 참가자 한 명의 sport_rating_history 행. 평가를 받지 못했으면 evaluationScore는 null, evaluationDelta는 0이다. */
public record RatingChange(
        Long matchId,
        Long participantId,
        Long userId,
        Long sportId,
        BigDecimal mmrBefore,
        BigDecimal mmrAfter,
        BigDecimal eloDelta,
        BigDecimal evaluationDelta,
        BigDecimal finalDelta,
        BigDecimal evaluationScore,
        BigDecimal actualScore,
        BigDecimal expectedScore,
        BigDecimal teamAvgMmrA,
        BigDecimal teamAvgMmrB,
        String algorithmVersion,
        LocalDateTime calculatedAt
) {
}
