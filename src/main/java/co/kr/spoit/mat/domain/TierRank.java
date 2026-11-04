package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** MMR로 계산한 표시 티어와 단계(4가 가장 낮음), 단계 안 포인트. 화면은 tier로 티어별 색을 고른다. */
public record TierRank(RatingTier tier, int division, int points) {

    private static final int DIVISIONS_PER_TIER = 4;
    private static final int LAST_INDEX = RatingTier.values().length * DIVISIONS_PER_TIER - 1;
    private static final BigDecimal START_MMR = BigDecimal.valueOf(RatingPolicy.TIER_START_MMR);
    private static final BigDecimal DIVISION_SIZE = BigDecimal.valueOf(RatingPolicy.TIER_DIVISION_SIZE);
    private static final BigDecimal POINTS_PER_DIVISION = BigDecimal.valueOf(RatingPolicy.TIER_POINTS_PER_DIVISION);

    public static TierRank of(BigDecimal mmr) {
        // 시작 MMR 아래에서는 단계와 포인트가 음수가 되므로 첫 단계의 0P로 본다.
        if (mmr.compareTo(START_MMR) < 0) {
            return new TierRank(RatingTier.BRONZE, DIVISIONS_PER_TIER, 0);
        }
        BigDecimal aboveStart = mmr.subtract(START_MMR);
        // 다이아 1이 마지막 단계라 그 위로는 단계를 올리지 않고 포인트만 쌓는다.
        int index = aboveStart.divide(DIVISION_SIZE, 0, RoundingMode.FLOOR)
                .min(BigDecimal.valueOf(LAST_INDEX))
                .intValue();
        BigDecimal intoDivision = aboveStart.subtract(DIVISION_SIZE.multiply(BigDecimal.valueOf(index)));
        int points = intoDivision.multiply(POINTS_PER_DIVISION)
                .divide(DIVISION_SIZE, 0, RoundingMode.FLOOR)
                .intValue();
        return new TierRank(RatingTier.values()[index / DIVISIONS_PER_TIER],
                DIVISIONS_PER_TIER - index % DIVISIONS_PER_TIER, points);
    }

    public String label() {
        return "%s %d · %dP".formatted(tier.label(), division, points);
    }
}
