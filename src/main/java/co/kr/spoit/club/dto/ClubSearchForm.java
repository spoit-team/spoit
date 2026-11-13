package co.kr.spoit.club.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.format.annotation.DateTimeFormat;

import co.kr.spoit.mat.domain.RatingPolicy;
import lombok.Data;

@Data
public class ClubSearchForm {

    // 티어 하나의 MMR 폭
    private static final int TIER_SIZE = RatingPolicy.TIER_DIVISION_SIZE * 4;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    // 한 번에 보여 줄 동호회 수(ClubPageResponse도 마지막 장 계산에 씀)
    public static final int PAGE_SIZE = 12;

    // 다음 장은 앞 장 끝 3개를 겹쳐 받음(그사이 앞에서 빠진 동호회를 놓치지 않게)
    private static final int OVERLAP = 3;

    // 다음 장 요청 한 번에 받는 수(무한 스크롤 끝 판단에 씀)
    public static final int NEXT_PAGE_SIZE = PAGE_SIZE + OVERLAP;

    // 뒤로 가기로 돌아올 때 첫 화면에 한 번에 그리는 최대 장 수
    private static final int FIRST_SCREEN_MAX_PAGES = 5;

    // 다음 장이 첫 화면 시각을 쓸 수 있는 시간(그보다 오래 머물렀으면 지금 시각)
    private static final int BASE_TIME_MAX_MINUTES = 30;

    private String q;
    private Long sportId;
    private Long regionId;
    private Integer level;
    private boolean recruiting;
    private String sort;

    private Integer page;
    private boolean cardsOnly;

    // 첫 화면 시각: 다음 장 요청 주소에만 붙어 옴(ClubCreateForm 시간 칸과 같은 방식으로 받음)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime at;

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

    // 가져올 개수: 다음 장은 겹칠 3개 포함 15개(1장은 겹칠 앞 장이 없어 12개), 첫 화면은 1장부터 그릴 장까지
    public int getSize() {
        if (cardsOnly) {
            if (getPage() == 1) {
                return PAGE_SIZE;
            }
            return NEXT_PAGE_SIZE;
        }
        return getShownPage() * PAGE_SIZE;
    }

    // 건너뛸 개수: 다음 장은 앞 장들보다 3개 덜(음수면 SQL 오류라 0 아래로 안감), 첫 화면은 처음부터. 아주 큰 page가 와도
    // 넘치지 않게 Long
    public long getSkip() {
        if (cardsOnly) {
            return Math.max(0, (long) (getPage() - 1) * PAGE_SIZE - OVERLAP);
        }
        return 0;
    }

    // 최근 활동순 기준 시각(서울): 다음 장도 첫 화면 시각을 써서 그사이 끝난 경기로 순서가 밀리지 않게.
    // 주소창으로 바꿀 수 있는 값이라 미래이거나 30분 넘게 지난 값은 안 쓰고 지금 시각
    public LocalDateTime getNow() {
        LocalDateTime current = LocalDateTime.now(ZONE);
        if (at != null && !at.isAfter(current) && at.isAfter(current.minusMinutes(BASE_TIME_MAX_MINUTES))) {
            return at;
        }
        return current;
    }

    // 첫 화면: 지금 시각을 한 번만 정해 SQL과 상자가 같이 씀(주소창 at은 덮어씀, 초 아래는 주소가 길어져 버림)
    public void useNowAsBaseTime() {
        at = LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.SECONDS);
    }

    private boolean validLevel() {
        return level != null && level >= 1 && level <= 5;
    }
}
