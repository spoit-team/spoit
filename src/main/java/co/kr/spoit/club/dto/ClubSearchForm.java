package co.kr.spoit.club.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;

import co.kr.spoit.mat.domain.RatingPolicy;
import lombok.Data;

@Data
public class ClubSearchForm {

    // 티어 하나의 MMR 폭
    private static final int TIER_SIZE = RatingPolicy.TIER_DIVISION_SIZE * 4;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

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

    public String getLikeQuery() {
        String query = getQuery();
        if (query == null) {
            return null;
        }
        StringBuilder escaped = new StringBuilder();
        for (char c : query.toCharArray()) {
            if (c == '!' || c == '%' || c == '_') {
                escaped.append('!');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    public boolean isRecruitingOnly() {
        return recruiting;
    }

    // 실력 구간 (이상), 브론즈는 최저 없음
    public Integer getMinMmr() {
        if (!validLevel() || level == 1) {
            return null;
        }
        return tierStartMmr(level);
    }

    // 실력 구간 (미만), 다이아는 최대 없음
    public Integer getMaxMmr() {
        if (!validLevel() || level == 5) {
            return null;
        }
        return tierStartMmr(level + 1);
    }

    // 단계가 시작하는 MMR: 2=실버 1000, 3=골드 1200, 4=플래티넘 1400, 5=다이아 1600
    private static int tierStartMmr(int level) {
        return RatingPolicy.TIER_START_MMR + TIER_SIZE * (level - 1);
    }

    // 최근 활동순 기준 시각(서울), 주소창 값으로 못 바꾸게 필드 없이 메서드만
    public LocalDateTime getNow() {
        return LocalDateTime.now(ZONE);
    }

    private boolean validLevel() {
        return level != null && level >= 1 && level <= 5;
    }
}
