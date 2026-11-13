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
        return "%s · %dP".formatted(divisionLabel(), points);
    }

    /** 포인트 없이 티어와 단계만, 예: "실버 4" */
    public String divisionLabel() {
        return "%s %d".formatted(tier.label(), division);
    }

    /** 브론즈 4 · 0P부터 쌓은 포인트. 화면의 P 변동은 반영 전후 이 값의 차이로 보여 줘야 표시된 P와 어긋나지 않는다. */
    public int totalPoints() {
        return stepIndex() * RatingPolicy.TIER_POINTS_PER_DIVISION + points;
    }

    /** MMR 증감을 포인트로 바꿔 반올림한다. 누적 포인트 차이로 구할 수 없는 평가 보정 몫처럼 증감 일부만 보여 줄 때 쓴다. */
    public static int roundedPoints(BigDecimal mmrDelta) {
        return mmrDelta.multiply(POINTS_PER_DIVISION)
                .divide(DIVISION_SIZE, 0, RatingPolicy.ROUNDING)
                .intValue();
    }

    /** 브론즈 4가 0인 단계 순번. 승급·강등은 이 값을 비교한다. */
    public int stepIndex() {
        return tier.ordinal() * DIVISIONS_PER_TIER + DIVISIONS_PER_TIER - division;
    }
}
