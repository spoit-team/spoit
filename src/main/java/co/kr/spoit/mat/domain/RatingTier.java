package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.util.Arrays;

/** MMR로 계산하는 표시 등급. 저장하지 않으며, 높은 등급부터 선언해 첫 일치를 고른다. */
public enum RatingTier {

    DIAMOND("다이아", RatingPolicy.DIAMOND_MIN_MMR),
    PLATINUM("플래티넘", RatingPolicy.PLATINUM_MIN_MMR),
    GOLD("골드", RatingPolicy.GOLD_MIN_MMR),
    SILVER("실버", RatingPolicy.SILVER_MIN_MMR),
    BRONZE("브론즈", Integer.MIN_VALUE);

    private final String label;
    private final BigDecimal minMmr;

    RatingTier(String label, int minMmr) {
        this.label = label;
        this.minMmr = BigDecimal.valueOf(minMmr);
    }

    public static String labelOf(BigDecimal mmr, int ratedMatchCount) {
        if (ratedMatchCount < RatingPolicy.PLACEMENT_MATCHES) {
            return "배치 중 (%d/%d)".formatted(ratedMatchCount, RatingPolicy.PLACEMENT_MATCHES);
        }
        return Arrays.stream(values())
                .filter(tier -> mmr.compareTo(tier.minMmr) >= 0)
                .findFirst()
                .orElseThrow()
                .label;
    }
}
