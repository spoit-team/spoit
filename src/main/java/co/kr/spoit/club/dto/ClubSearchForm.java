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

    // 한 번에 보여 줄 동호회 수(ClubPageResponse도 마지막 장 계산에 씀)
    public static final int PAGE_SIZE = 12;

    // 뒤로 가기로 돌아올 때 첫 화면에 한 번에 그리는 최대 장 수
    private static final int FIRST_SCREEN_MAX_PAGES = 5;

    private String q;
    private Long sportId;
    private Long regionId;
    private Integer level;
    private boolean recruiting;
    private String sort;

    private Integer page;
    private boolean cardsOnly;

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

    // 장 번호, 없거나 1보다 작으면 1
    public Integer getPage() {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    // 첫 화면에 그릴 장 수, 최대 5장
    public int getShownPage() {
        return Math.min(getPage(), FIRST_SCREEN_MAX_PAGES);
    }

    // 가져올 개수: 다음 장은 12개, 첫 화면은 1장부터 그릴 장까지
    public int getSize() {
        if (cardsOnly) {
            return PAGE_SIZE;
        }
        return getShownPage() * PAGE_SIZE;
    }

    // 건너뛸 개수: 다음 장은 앞 장들만큼, 첫 화면은 처음부터. 아주 큰 page가 와도 넘치지 않게 Long
    public long getSkip() {
        if (cardsOnly) {
            return (long) (getPage() - 1) * PAGE_SIZE;
        }
        return 0;
    }

    // 최근 활동순 기준 시각(서울), 주소창 값으로 못 바꾸게 필드 없이 메서드만
    public LocalDateTime getNow() {
        return LocalDateTime.now(ZONE);
    }

    private boolean validLevel() {
        return level != null && level >= 1 && level <= 5;
    }
}
