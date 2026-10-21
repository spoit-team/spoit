package co.kr.spoit.mat.dto;

import co.kr.spoit.mat.domain.SportRating;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 저장된 내부 MMR 조회 DTO. 티어·모집 실력 단계로 변환하지 않는다. */
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
}
