package co.kr.spoit.facility.dto;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시설 관리자 예약 통계 화면의 집계 모델이다.
 *
 * <p>집계 범위는 인증 주체가 담당하는 활성 시설 중 선택한 시설 하나이며, 기간은 매칭방
 * {@code start_at} 기준이다. 취소된 매칭방은 매칭방 수·참가 인원·요일·시간대 집계에서
 * 제외한다({@code FacilityStatsMapper} 참고). 신청 지표는 대상 매칭방에 접수된 대표 신청
 * 행을 상태와 무관하게 세고, 승인 수는 그중 확정 상태다.</p>
 *
 * <p>승인율 계산은 서비스가 맡고 이 모델은 계산된 값을 그대로 담는다. 리스트는 항상 null이
 * 아닌 불변 리스트이며, 요일 차트는 7행을 유지하고 집계 대상이 없으면 비어 있는 시간대
 * 목록을 쓴다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class FacilityStats {

    /** 요일 차트의 표시 순서. DAYOFWEEK 2(월)~7(토), 1(일) 순으로 매핑한다. */
    private static final List<String> WEEKDAY_LABELS =
            List.of("월", "화", "수", "목", "금", "토", "일");

    /** 선택 기간 일수(7/30/90) 또는 직접 입력 기간의 일수 */
    private int period;
    /** "최근 30일" 형식의 기간 문구 */
    private String periodLabel;
    /** 화면이 요청한 기간 종류("7"/"30"/"90"/"custom"). 기간 select의 선택 상태 복원에 쓴다. */
    private String requestedPeriod = "30";
    /** 실제 조회 구간 시작일(ISO {@code yyyy-MM-dd}). custom이 아니어도 서버가 계산해 채운다. */
    private String periodFrom;
    /** 실제 조회 구간 종료일(ISO {@code yyyy-MM-dd}). custom이 아니어도 서버가 계산해 채운다. */
    private String periodTo;
    /** 선택 기간 내 취소 제외 매칭방 수 */
    private long bookingCount;
    /** 대상 매칭방의 대기·확정 참가자 합계 */
    private long participantCount;
    /** 가장 인기 있는 요일 라벨. 집계 대상이 없으면 null */
    private String popularDay;
    /** 가장 인기 있는 시간대 라벨. 집계 대상이 없으면 null */
    private String peakTime;
    /** 요일별 비중 7행(월~일). null이 될 수 없다. */
    private List<WeekdayShare> weekdays = List.of();
    /** 시간대별 매칭방 수. null이 될 수 없다. */
    private List<PopularHour> popularHours = List.of();
    /** 대표 신청 행 총계(모든 상태) */
    private long applicationTotal;
    /** 개인 신청 수 */
    private long individualCount;
    /** 동호회 신청 수 */
    private long clubCount;
    /** 확정 상태 신청 수 */
    private long approvedCount;
    /** 승인율(%). 신청 총계가 0이면 0 */
    private double approvalRate;

    public FacilityStats(int period, String periodLabel, long bookingCount, long participantCount,
            String popularDay, String peakTime, List<WeekdayShare> weekdays,
            List<PopularHour> popularHours, long applicationTotal, long individualCount,
            long clubCount, long approvedCount, double approvalRate) {
        this.period = period;
        this.periodLabel = periodLabel;
        this.bookingCount = bookingCount;
        this.participantCount = participantCount;
        this.popularDay = popularDay;
        this.peakTime = peakTime;
        setWeekdays(weekdays);
        setPopularHours(popularHours);
        this.applicationTotal = applicationTotal;
        this.individualCount = individualCount;
        this.clubCount = clubCount;
        this.approvedCount = approvedCount;
        this.approvalRate = approvalRate;
    }

    /** 담당 시설이 없거나 시설 미선택일 때 쓰는 0건 집계. 요일 차트 7행은 유지한다. */
    public static FacilityStats empty(int period, String periodLabel) {
        List<WeekdayShare> weekdays = WEEKDAY_LABELS.stream()
                .map(label -> new WeekdayShare(label, "", 0))
                .toList();
        return new FacilityStats(period, periodLabel, 0, 0, null, null, weekdays, List.of(), 0, 0, 0,
                0, 0);
    }

    /** 리스트는 항상 불변 복사본으로 보관한다. null은 빈 목록으로 바꾼다. */
    public void setWeekdays(List<WeekdayShare> weekdays) {
        this.weekdays = weekdays == null ? List.of() : List.copyOf(weekdays);
    }

    /** 리스트는 항상 불변 복사본으로 보관한다. null은 빈 목록으로 바꾼다. */
    public void setPopularHours(List<PopularHour> popularHours) {
        this.popularHours = popularHours == null ? List.of() : List.copyOf(popularHours);
    }

    /** 요일 차트 한 행. {@code percent}는 최대 요일 대비 상대 비중(0~100)이다. */
    @Getter
    public static class WeekdayShare {

        /** "월" ~ "일" */
        private final String label;
        /** progress-fill 색상 클래스(good/warn/bad). 없으면 빈 문자열 */
        private final String tone;
        private final int percent;

        public WeekdayShare(String label, String tone, int percent) {
            this.label = label;
            this.tone = tone;
            this.percent = percent;
        }
    }

    /** 인기 시간대 차트 한 행. {@code height}는 최대 시간대 대비 상대 백분율(0~100)이다. */
    @Getter
    public static class PopularHour {

        /** "0시" ~ "23시" */
        private final String label;
        private final long count;
        private final int height;

        public PopularHour(String label, long count, int height) {
            this.label = label;
            this.count = count;
            this.height = height;
        }
    }
}
