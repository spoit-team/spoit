package co.kr.spoit.facility.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시설 관리자 예약 통계 화면의 집계 모델이다.
 *
 * <p>집계 범위는 인증 주체가 담당하는 활성 시설 중 선택한 시설 하나이며, 기간은 매칭방
 * {@code start_at} 기준이다. 집계 대상은 상태가 {@code 종료}인 매칭방뿐이고, 모집 중·매칭
 * 완료·취소는 매칭방 수·참가 인원·요일·시간대 집계에서 모두 제외한다({@code FacilityStatsMapper}
 * 참고). 인원 지표는 {@code match_participant}의 {@code participation_status = '확정'} 행만
 * 세므로 같은 사람이 두 경기에 참여하면 2인으로 집계되고 노쇼는 구분하지 못한다.</p>
 *
 * <p>요일·시간대 분포의 {@code count}는 매칭방 수가 아니라 확정 참가 인원이고, 상세
 * {@code share}는 전체 종료 매칭 확정 참가 인원({@code participantCount}) 대비 반올림 백분율이다.
 * {@code popularDay}·{@code peakTime}도 참가 인원이 가장 많은 요일·시간대다. 신청 지표는 대상
 * 종료 매칭방에 접수된 대표 신청 행을 상태와 무관하게 세고, 승인 수는 그중 확정 상태다.</p>
 *
 * <p>승인율 계산은 서비스가 맡고 이 모델은 계산된 값을 그대로 담는다. 리스트는 항상 null이
 * 아닌 불변 리스트이며, 요일 차트는 7행을 유지하고 집계 대상이 없으면 비어 있는 시간대
 * 목록을 쓴다. 상세 집계({@code weekdayDetails}·{@code hourDetails}·{@code weekdayHourDetails})는
 * 기존 차트와 같은 집계 결과를 0으로 채운 고정 길이 목록으로 담아 화면이 항상 같은 모양을
 * 갖게 한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class FacilityStats {

    /** 요일 차트의 표시 순서. DAYOFWEEK 2(월)~7(토), 1(일) 순으로 매핑한다. */
    private static final List<String> WEEKDAY_LABELS =
            List.of("월", "화", "수", "목", "금", "토", "일");

    /** 하루 시간대 수(00~23시). 상세 집계 목록의 길이 계약이다. */
    private static final int HOURS_PER_DAY = 24;

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
    /** 선택 기간 내 종료 매칭방 수 */
    private long bookingCount;
    /** 선택 기간 내 종료 매칭의 확정 참가자 행 수 (매칭별 인원 합) */
    private long participantCount;
    /** 확정 참가 인원이 가장 많은 요일 라벨. 집계 대상이 없으면 null */
    private String popularDay;
    /** 확정 참가 인원이 가장 많은 시간대 라벨. 집계 대상이 없으면 null */
    private String peakTime;
    /** 요일별 확정 참가 인원 비중 7행(월~일). null이 될 수 없다. */
    private List<WeekdayShare> weekdays = List.of();
    /** 시간대별 확정 참가 인원. null이 될 수 없다. */
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
    /** 요일별 확정 참가 인원 상세 7행(월~일). null이 될 수 없다. */
    private List<WeekdayDetail> weekdayDetails = List.of();
    /** 시작 시간대별 확정 참가 인원 상세 24행(00~23시). null이 될 수 없다. */
    private List<HourDetail> hourDetails = List.of();
    /** 요일×시작 시간대 확정 참가 인원 상세 7행(월~일). 각 행의 시간대 목록은 24개(00~23시)다. */
    private List<WeekdayHourDetail> weekdayHourDetails = List.of();

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

    /**
     * 담당 시설이 없거나 시설 미선택일 때 쓰는 0건 집계. 요일 차트 7행과 상세 집계
     * (요일 7행·시간대 24행·요일×시간대 7행)도 0으로 채워 화면이 항상 같은 모양을 갖게 한다.
     */
    public static FacilityStats empty(int period, String periodLabel) {
        List<WeekdayShare> weekdays = WEEKDAY_LABELS.stream()
                .map(label -> new WeekdayShare(label, "", 0))
                .toList();
        FacilityStats stats = new FacilityStats(period, periodLabel, 0, 0, null, null, weekdays,
                List.of(), 0, 0, 0, 0, 0);
        stats.setWeekdayDetails(zeroWeekdayDetails());
        stats.setHourDetails(zeroHourDetails());
        stats.setWeekdayHourDetails(zeroWeekdayHourDetails());
        return stats;
    }

    /** 요일 상세 7행(월~일)을 모두 0으로 채운다. */
    private static List<WeekdayDetail> zeroWeekdayDetails() {
        List<WeekdayDetail> rows = new ArrayList<>(WEEKDAY_LABELS.size());
        for (String label : WEEKDAY_LABELS) {
            rows.add(new WeekdayDetail(label, 0L, 0));
        }
        return rows;
    }

    /** 시간대 상세 24행(00~23시)을 모두 0으로 채운다. */
    private static List<HourDetail> zeroHourDetails() {
        List<HourDetail> rows = new ArrayList<>(HOURS_PER_DAY);
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            rows.add(new HourDetail(hourLabel(hour), 0L, 0));
        }
        return rows;
    }

    /** 요일×시간대 상세 7행을 만들고 각 행의 시간대 24칸을 0으로 채운다. */
    private static List<WeekdayHourDetail> zeroWeekdayHourDetails() {
        List<WeekdayHourDetail> rows = new ArrayList<>(WEEKDAY_LABELS.size());
        for (String label : WEEKDAY_LABELS) {
            List<Long> hourlyCounts = new ArrayList<>(HOURS_PER_DAY);
            for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
                hourlyCounts.add(0L);
            }
            rows.add(new WeekdayHourDetail(label, hourlyCounts));
        }
        return rows;
    }

    /** "00시" ~ "23시". 인기 시간대 차트와 같은 표기다. */
    private static String hourLabel(int hour) {
        return String.format("%02d시", hour);
    }

    /** 리스트는 항상 불변 복사본으로 보관한다. null은 빈 목록으로 바꾼다. */
    public void setWeekdays(List<WeekdayShare> weekdays) {
        this.weekdays = weekdays == null ? List.of() : List.copyOf(weekdays);
    }

    /** 리스트는 항상 불변 복사본으로 보관한다. null은 빈 목록으로 바꾼다. */
    public void setPopularHours(List<PopularHour> popularHours) {
        this.popularHours = popularHours == null ? List.of() : List.copyOf(popularHours);
    }

    /** 요일 상세 7행(월~일). null이 될 수 없다. */
    public List<WeekdayDetail> getWeekdayDetails() {
        return weekdayDetails;
    }

    /** 요일 상세는 7행 계약을 지키기 위해 이 호출자가 넘긴 값을 불변 복사본으로 보관한다. */
    public void setWeekdayDetails(List<WeekdayDetail> weekdayDetails) {
        this.weekdayDetails = weekdayDetails == null ? List.of() : List.copyOf(weekdayDetails);
    }

    /** 시간대 상세 24행(00~23시). null이 될 수 없다. */
    public List<HourDetail> getHourDetails() {
        return hourDetails;
    }

    /** 시간대 상세는 24행 계약을 지키기 위해 이 호출자가 넘긴 값을 불변 복사본으로 보관한다. */
    public void setHourDetails(List<HourDetail> hourDetails) {
        this.hourDetails = hourDetails == null ? List.of() : List.copyOf(hourDetails);
    }

    /** 요일×시간대 상세 7행(월~일). null이 될 수 없다. */
    public List<WeekdayHourDetail> getWeekdayHourDetails() {
        return weekdayHourDetails;
    }

    /** 요일×시간대 상세는 7행 계약을 지키기 위해 이 호출자가 넘긴 값을 불변 복사본으로 보관한다. */
    public void setWeekdayHourDetails(List<WeekdayHourDetail> weekdayHourDetails) {
        this.weekdayHourDetails = weekdayHourDetails == null ? List.of()
                : List.copyOf(weekdayHourDetails);
    }

    /** 요일 차트 한 행. {@code percent}는 확정 참가 인원이 최대인 요일 대비 상대 비중(0~100)이다. */
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

    /** 인기 시간대 차트 한 행. {@code height}는 참가 인원이 최대인 시간대 대비 상대 백분율(0~100)이다. */
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

    /** 요일 상세 집계 한 행. {@code share}는 전체 종료 매칭 확정 참가 인원 대비 반올림 백분율(0~100)이다. */
    @Getter
    public static class WeekdayDetail {

        /** "월" ~ "일" */
        private final String label;
        private final long count;
        private final int share;

        public WeekdayDetail(String label, long count, int share) {
            this.label = label;
            this.count = count;
            this.share = share;
        }
    }

    /** 시작 시간대 상세 집계 한 행. {@code share}는 전체 종료 매칭 확정 참가 인원 대비 반올림 백분율(0~100)이다. */
    @Getter
    public static class HourDetail {

        /** "00시" ~ "23시" */
        private final String label;
        private final long count;
        private final int share;

        public HourDetail(String label, long count, int share) {
            this.label = label;
            this.count = count;
            this.share = share;
        }
    }

    /** 요일×시작 시간대 상세 집계 한 행. 시간대 목록은 00~23시 순서로 24개다. */
    @Getter
    public static class WeekdayHourDetail {

        /** "월" ~ "일" */
        private final String label;
        /** 00시~23시 순서의 확정 참가 인원 24개. null이 될 수 없다. */
        private final List<Long> hourlyCounts;

        public WeekdayHourDetail(String label, List<Long> hourlyCounts) {
            this.label = label;
            this.hourlyCounts = hourlyCounts == null ? List.of() : List.copyOf(hourlyCounts);
        }
    }
}
