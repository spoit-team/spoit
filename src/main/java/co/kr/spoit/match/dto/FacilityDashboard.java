package co.kr.spoit.match.dto;

import java.util.List;

import lombok.Getter;

/**
 * 시설 관리자 대시보드의 매칭방 현황 집계 모델이다.
 *
 * <p>집계 범위는 인증 주체가 담당하는 활성 시설 전체이며, 화면의 검색·상태·종목·시설 필터와
 * 무관하다. {@code matchCount}는 담당 시설에 등록된 모든 매칭방(취소·종료 포함)이고,
 * {@code recruitingCount}는 DB 상태가 모집 중인 방 수다. 시간대 차트의 '이용 완료'는 DB 종료
 * 상태만 뜻하며 취소는 포함하지 않는다.</p>
 *
 * <p>최근 매칭방은 스키마에 등록 시각이 없어 매칭방 번호(내림차순)를 등록 순서로 쓴다.
 * 목록은 항상 null이 아닌 불변 리스트이고, 담당 시설이나 매칭방이 없으면 비어 있다.</p>
 */
@Getter
public class FacilityDashboard {

    /** 담당 시설 전체를 뜻하는 화면 범위 문구 */
    private static final String SCOPE_NAME = "담당 시설 전체";

    private final int matchCount;
    private final int recruitingCount;
    private final List<HourlyBar> hourlyBars;
    /** 매칭방 번호 내림차순 최근 등록 순. 항목 타입은 매칭방 목록과 같은 표시 모델이다. */
    private final List<RoomManagementPage.Match> recentMatches;
    /** "2026-11-02(월)" 형식의 오늘 날짜 */
    private final String todayLabel;
    /** "2026-11-02 14:05" 형식의 조회 시각 */
    private final String collectedAt;

    public FacilityDashboard(int matchCount, int recruitingCount, List<HourlyBar> hourlyBars,
            List<RoomManagementPage.Match> recentMatches, String todayLabel, String collectedAt) {
        this.matchCount = matchCount;
        this.recruitingCount = recruitingCount;
        this.hourlyBars = List.copyOf(hourlyBars);
        this.recentMatches = List.copyOf(recentMatches);
        this.todayLabel = todayLabel;
        this.collectedAt = collectedAt;
    }

    /** 담당 시설이 없거나 관리자 식별자가 없을 때 쓰는 0건 집계. */
    public static FacilityDashboard empty(String todayLabel, String collectedAt) {
        return new FacilityDashboard(0, 0, HourlyBar.zeroBars(), List.of(), todayLabel, collectedAt);
    }

    /** 집계 범위 문구. 필터와 무관하게 항상 담당 시설 전체다. */
    public String getScopeName() {
        return SCOPE_NAME;
    }

    /** 민원·점검 도메인은 이번 범위가 아니므로 수치를 만들지 않는다. */
    public Integer getOpenRequestCount() {
        return null;
    }

    /** 점검 도메인은 이번 범위가 아니므로 수치를 만들지 않는다. */
    public Integer getMaintenanceCount() {
        return null;
    }

    /** 최근 요청 피드는 이번 범위가 아니므로 빈 목록이다. */
    public List<Object> getRecentRequests() {
        return List.of();
    }

    /**
     * 오늘 하루 24칸 중 한 시간대의 집계다.
     *
     * <p>{@code matchCount}는 그 시각에 시작하는 모든 상태의 매칭방 수(취소 포함),
     * {@code completedCount}는 그중 DB 종료 상태 수다. 높이는 24칸 전체의 최대 방 수에 대한
     * 상대 백분율(0~100)이며, 모든 칸이 0이면 0이다.</p>
     */
    @Getter
    public static class HourlyBar {

        private final int matchCount;
        private final int completedCount;
        private final int matchHeight;
        private final int completedHeight;
        /** "00시" ~ "23시" */
        private final String label;

        public HourlyBar(int matchCount, int completedCount, int matchHeight, int completedHeight,
                String label) {
            this.matchCount = matchCount;
            this.completedCount = completedCount;
            this.matchHeight = matchHeight;
            this.completedHeight = completedHeight;
            this.label = label;
        }

        /** 00~23시 빈 칸. 집계 대상이 없을 때 화면이 24칸을 그릴 수 있게 한다. */
        static List<HourlyBar> zeroBars() {
            return java.util.stream.IntStream.range(0, 24)
                    .mapToObj(hour -> new HourlyBar(0, 0, 0, 0, String.format("%02d시", hour)))
                    .toList();
        }
    }
}
