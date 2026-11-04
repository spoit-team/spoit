package co.kr.spoit.mat.dto;

import co.kr.spoit.mat.domain.RatingTier;
import co.kr.spoit.mat.domain.SportRating;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 저장된 내부 MMR 조회 DTO. 표시 티어는 tierLabel()로 계산하고, 모집 실력 단계로는 변환하지 않는다. */
public record SportRatingResponse(
        Long userId,
        Long sportId,
        BigDecimal currentMmr,
        Integer ratedMatchCount,
        LocalDateTime updatedAt) {

    public static SportRatingResponse from(SportRating rating) {
        return new SportRatingResponse(
                rating.getUserId(),
                rating.getSportId(),
                rating.getCurrentMmr(),
                rating.getRatedMatchCount(),
                rating.getUpdatedAt());
    }

    public String tierLabel() {
        return RatingTier.labelOf(currentMmr, ratedMatchCount);
    }
}
