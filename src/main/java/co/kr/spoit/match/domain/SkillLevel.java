package co.kr.spoit.match.domain;

import co.kr.spoit.mat.domain.RatingTier;

/**
 * social_match.required_skill에 저장되는 추천 티어.
 *
 * <p>화면 선택지와 DB 값이 같고, 값은 RatingTier의 표시 이름(브론즈·실버·골드·플래티넘·다이아)이다.
 * 신청 조건이 아닌 안내용이라 신청자의 레이팅과 비교하지 않는다.</p>
 */
public final class SkillLevel {

    private SkillLevel() {
    }

    /** DB 값과 정확히 일치하는 티어, 없으면 null */
    public static RatingTier fromDbValue(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.strip();
        for (RatingTier tier : RatingTier.values()) {
            if (tier.label().equals(normalized)) {
                return tier;
            }
        }
        return null;
    }
}
