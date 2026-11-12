package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.util.Locale;

/** MMR로 계산하는 표시 등급. 저장하지 않으며, TierRank가 선언 순서를 단계 순서로 쓰므로 낮은 등급부터 선언한다. */
public enum RatingTier {

    BRONZE("브론즈"),
    SILVER("실버"),
    GOLD("골드"),
    PLATINUM("플래티넘"),
    DIAMOND("다이아");

    private final String label;

    RatingTier(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 화면의 티어 색 클래스(is-tier-silver 등)에 쓰는 값 */
    public String cssKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static String labelOf(BigDecimal mmr, int ratedMatchCount) {
        if (ratedMatchCount < RatingPolicy.PLACEMENT_MATCHES) {
            return "배치 중 (%d/%d)".formatted(ratedMatchCount, RatingPolicy.PLACEMENT_MATCHES);
        }
        return TierRank.of(mmr).label();
    }

    /** labelOf와 같은 배치 기준으로, 포인트 없이 "실버 4"나 "배치 중"만 보여 줄 때 쓴다. */
    public static String tierNameOf(BigDecimal mmr, int ratedMatchCount) {
        if (ratedMatchCount < RatingPolicy.PLACEMENT_MATCHES) {
            return "배치 중";
        }
        return TierRank.of(mmr).divisionLabel();
    }
}
