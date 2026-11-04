package co.kr.spoit.club.dto;

import co.kr.spoit.mat.domain.RatingPolicy;
import lombok.Data;

@Data
public class ClubSearchForm {

    // 실력 단계별 MMR: 1=브론즈 ~ 5=다이아 (0은 비워둠)
    private static final Integer[] MIN_MMR = { null, null, RatingPolicy.SILVER_MIN_MMR,
            RatingPolicy.GOLD_MIN_MMR, RatingPolicy.PLATINUM_MIN_MMR, RatingPolicy.DIAMOND_MIN_MMR };
    private static final Integer[] MAX_MMR = { null, RatingPolicy.SILVER_MIN_MMR,
            RatingPolicy.GOLD_MIN_MMR, RatingPolicy.PLATINUM_MIN_MMR, RatingPolicy.DIAMOND_MIN_MMR, null };

    private String q;
    private Long sportId;
    private Long regionId;
    private Integer level;
    private boolean recruiting;
    private String sort;

    public String getQuery() {
        if (q == null || q.isBlank()) {
            return null;
        }
        return q.trim();
    }

    public boolean isRecruitingOnly() {
        return recruiting;
    }

    // 실력 구간 (이상), 브론즈는 최저 없음
    public Integer getMinMmr() {
        return validLevel() ? MIN_MMR[level] : null;
    }

    // 실력 구간 (미만), 다이아 최대 없음
    public Integer getMaxMmr() {
        return validLevel() ? MAX_MMR[level] : null;
    }

    private boolean validLevel() {
        return level != null && level >= 1 && level <= 5;
    }
}
