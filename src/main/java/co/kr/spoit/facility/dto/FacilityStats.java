package co.kr.spoit.facility.dto;

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
 * <p>승인율 계산과 0건 화면 모델 생성은 서비스가 맡고 이 모델은 계산된 값을 그대로 담는다. 상세
 * 집계({@code weekdayDetails}·{@code hourDetails}·{@code weekdayHourDetails})는 화면이 항상 같은
 * 모양을 갖도록 서비스가 0으로 채운 고정 길이 목록을 담는다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class FacilityStats {

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
    /** 요일별 확정 참가 인원 상세. 화면은 서비스가 만드는 7행(월~일)을 받는다. null이 될 수 없다. */
    private List<WeekdayDetail> weekdayDetails = List.of();
    /** 시작 시간대별 확정 참가 인원 상세. 화면은 서비스가 만드는 24행(00~23시)을 받는다. null이 될 수 없다. */
    private List<HourDetail> hourDetails = List.of();
    /** 요일×시작 시간대 확정 참가 인원 상세. 서비스는 7행(월~일)을 만들고 각 행은 24개(00~23시)다. */
    private List<WeekdayHourDetail> weekdayHourDetails = List.of();

    public FacilityStats(int period, String periodLabel, long bookingCount, long participantCount,
            String popularDay, String peakTime, long applicationTotal, long individualCount,
            long clubCount, long approvedCount, double approvalRate) {
        this.period = period;
        this.periodLabel = periodLabel;
        this.bookingCount = bookingCount;
        this.participantCount = participantCount;
        this.popularDay = popularDay;
        this.peakTime = peakTime;
        this.applicationTotal = applicationTotal;
        this.individualCount = individualCount;
        this.clubCount = clubCount;
        this.approvedCount = approvedCount;
        this.approvalRate = approvalRate;
    }

    /** 요일 상세 목록. 화면 모델은 서비스가 7행(월~일)으로 채운다. null이 될 수 없다. */
    public List<WeekdayDetail> getWeekdayDetails() {
        return weekdayDetails;
    }

    /** 요일 상세는 넘긴 값을 불변 복사본으로 보관한다. null은 빈 목록이 된다. */
    public void setWeekdayDetails(List<WeekdayDetail> weekdayDetails) {
        this.weekdayDetails = weekdayDetails == null ? List.of() : List.copyOf(weekdayDetails);
    }

    /** 시간대 상세 목록. 화면 모델은 서비스가 24행(00~23시)으로 채운다. null이 될 수 없다. */
    public List<HourDetail> getHourDetails() {
        return hourDetails;
    }

    /** 시간대 상세는 넘긴 값을 불변 복사본으로 보관한다. null은 빈 목록이 된다. */
    public void setHourDetails(List<HourDetail> hourDetails) {
        this.hourDetails = hourDetails == null ? List.of() : List.copyOf(hourDetails);
    }

    /** 요일×시간대 상세 목록. 화면 모델은 서비스가 7행(월~일)×24칸으로 채운다. null이 될 수 없다. */
    public List<WeekdayHourDetail> getWeekdayHourDetails() {
        return weekdayHourDetails;
    }

    /** 요일×시간대 상세는 넘긴 값을 불변 복사본으로 보관한다. null은 빈 목록이 된다. */
    public void setWeekdayHourDetails(List<WeekdayHourDetail> weekdayHourDetails) {
        this.weekdayHourDetails = weekdayHourDetails == null ? List.of()
                : List.copyOf(weekdayHourDetails);
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
