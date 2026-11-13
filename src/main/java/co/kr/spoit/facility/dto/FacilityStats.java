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
 * {@code popularDay}·{@code peakTime}도 참가 인원이 가장 많은 요일·시간대다.</p>
 *
 * <p>신청 지표는 같은 매칭방에 같은 사람이 여러 번 신청했을 수 있으므로
 * {@code (match_id, applicant_user_id)}별로 가장 최근 신청({@code created_at DESC, application_id DESC})
 * 한 건만 센다. 팀 신청은 팀원이 각자 한 건이고, 서로 다른 매칭방의 신청은 각각 센다. 최신 신청이
 * {@code 대기}면 {@code applicationTotal}에는 들어가지만 승인율·취소율과 분포 차트에서는 빠진다.
 * 분포 차트의 {@code totalCount}는 대기를 뺀 처리 수({@code approvedCount + cancelledCount})다.
 * {@code cancelledCount}는 최신 신청이 {@code 반려}이거나 {@code 취소}인 건수이고,
 * {@code resolvedCount}는 확정·반려·취소를 합친 처리 건수다. {@code approvalRate}는
 * {@code 확정 / resolvedCount}, {@code cancelRate}는 {@code (반려+취소) / resolvedCount}를 반올림한
 * 백분율이며 분모가 0이면 둘 다 0이다.</p>
 *
 * <p>승인율·취소율 계산과 0건 화면 모델 생성은 서비스가 맡고 이 모델은 계산된 값을 그대로 담는다. 상세
 * 집계({@code weekdayDetails}·{@code hourDetails}·{@code weekdayHourDetails})와 신청 분포
 * ({@code applicationWeekdayDetails}·{@code applicationHourDetails}·
 * {@code applicationWeekdayHourDetails})는 화면이 항상 같은 모양을 갖도록 서비스가 0으로 채운 고정
 * 길이 목록을 담는다. {@code weekdayChartMax}·{@code hourChartMax}·{@code weekdayHourChartMax}는 차트
 * 막대 폭 계산용 상한이며 값이 없어도 최소 1이다.</p>
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
    /** 최신 신청 건수(대기 포함) */
    private long applicationTotal;
    /** 개인 신청 수 */
    private long individualCount;
    /** 동호회 신청 수 */
    private long clubCount;
    /** 최신 신청이 확정인 수 */
    private long approvedCount;
    /** 최신 신청이 반려이거나 취소인 수 */
    private long cancelledCount;
    /** 처리 수 = 확정 + 반려 + 취소. 대기 제외 */
    private long resolvedCount;
    /** 승인율(%). 처리 수가 0이면 0 */
    private double approvalRate;
    /** 취소율(%) = (반려+취소) / 처리 수. 처리 수가 0이면 0 */
    private double cancelRate;
    /** 요일별 확정 참가 인원 상세. 화면은 서비스가 만드는 7행(월~일)을 받는다. null이 될 수 없다. */
    private List<WeekdayDetail> weekdayDetails = List.of();
    /** 시작 시간대별 확정 참가 인원 상세. 화면은 서비스가 만드는 24행(00~23시)을 받는다. null이 될 수 없다. */
    private List<HourDetail> hourDetails = List.of();
    /** 요일×시작 시간대 확정 참가 인원 상세. 서비스는 7행(월~일)을 만들고 각 행은 24개(00~23시)다. */
    private List<WeekdayHourDetail> weekdayHourDetails = List.of();
    /** 요일별 최신 신청 상태 분포 상세(7행, 월~일). 막대는 대기를 뺀 처리 수다. null이 될 수 없다. */
    private List<ApplicationDistributionDetail> applicationWeekdayDetails = List.of();
    /** 시작 시간대별 최신 신청 상태 분포 상세(24행, 00~23시). 막대는 대기를 뺀 처리 수다. null이 될 수 없다. */
    private List<ApplicationDistributionDetail> applicationHourDetails = List.of();
    /** 요일×시작 시간대별 최신 신청 상태 분포 상세(7행). 각 행의 hours는 24개이며 대기를 뺀 처리 수다. */
    private List<ApplicationWeekdayHourDetail> applicationWeekdayHourDetails = List.of();
    /** 요일 차트 상한(최소 1) */
    private long weekdayChartMax = 1;
    /** 시간대 차트 상한(최소 1) */
    private long hourChartMax = 1;
    /** 요일×시간대 차트 상한(최소 1) */
    private long weekdayHourChartMax = 1;

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

    /** 요일별 최신 신청 분포 목록. 화면 모델은 서비스가 7행(월~일)으로 채운다. null이 될 수 없다. */
    public List<ApplicationDistributionDetail> getApplicationWeekdayDetails() {
        return applicationWeekdayDetails;
    }

    /** 요일별 최신 신청 분포는 넘긴 값을 불변 복사본으로 보관한다. null은 빈 목록이 된다. */
    public void setApplicationWeekdayDetails(List<ApplicationDistributionDetail> applicationWeekdayDetails) {
        this.applicationWeekdayDetails = applicationWeekdayDetails == null ? List.of()
                : List.copyOf(applicationWeekdayDetails);
    }

    /** 시간대별 최신 신청 분포 목록. 화면 모델은 서비스가 24행(00~23시)으로 채운다. null이 될 수 없다. */
    public List<ApplicationDistributionDetail> getApplicationHourDetails() {
        return applicationHourDetails;
    }

    /** 시간대별 최신 신청 분포는 넘긴 값을 불변 복사본으로 보관한다. null은 빈 목록이 된다. */
    public void setApplicationHourDetails(List<ApplicationDistributionDetail> applicationHourDetails) {
        this.applicationHourDetails = applicationHourDetails == null ? List.of()
                : List.copyOf(applicationHourDetails);
    }

    /** 요일×시간대별 최신 신청 분포 목록. 서비스는 7행(월~일)×24칸으로 채운다. null이 될 수 없다. */
    public List<ApplicationWeekdayHourDetail> getApplicationWeekdayHourDetails() {
        return applicationWeekdayHourDetails;
    }

    /** 요일×시간대별 최신 신청 분포는 넘긴 값을 불변 복사본으로 보관한다. null은 빈 목록이 된다. */
    public void setApplicationWeekdayHourDetails(
            List<ApplicationWeekdayHourDetail> applicationWeekdayHourDetails) {
        this.applicationWeekdayHourDetails = applicationWeekdayHourDetails == null ? List.of()
                : List.copyOf(applicationWeekdayHourDetails);
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

    /**
     * 최신 신청 상태 분포 한 칸(요일 또는 시간대). 차트 막대는 대기를 제외한 처리 수를 쓴다.
     *
     * <p>{@code totalCount}는 그 칸의 처리 수, 즉 {@code approvedCount + cancelledCount}다.
     * 대기 건수는 차트에 넣지 않는다(신청 총계 {@code applicationTotal}에만 포함된다).</p>
     */
    @Getter
    public static class ApplicationDistributionDetail {

        /** 요일이면 "월"~"일", 시간대면 "00시"~"23시" */
        private final String label;
        /** 최신 신청이 확정인 건수 */
        private final long approvedCount;
        /** 최신 신청이 반려 또는 취소인 건수 */
        private final long cancelledCount;
        /** 처리 수(확정+반려+취소). 대기 제외 */
        private final long totalCount;

        public ApplicationDistributionDetail(String label, long approvedCount, long cancelledCount,
                long totalCount) {
            this.label = label;
            this.approvedCount = approvedCount;
            this.cancelledCount = cancelledCount;
            this.totalCount = totalCount;
        }
    }

    /** 요일×시작 시간대별 최신 신청 분포 한 행. {@code hours}는 00~23시 순서로 24개다. */
    @Getter
    public static class ApplicationWeekdayHourDetail {

        /** "월" ~ "일" */
        private final String label;
        /** 00시~23시 순서의 최신 신청 분포 24개(처리 수는 대기 제외). 각 요소의 label은 그 시간대다. */
        private final List<ApplicationDistributionDetail> hours;

        public ApplicationWeekdayHourDetail(String label, List<ApplicationDistributionDetail> hours) {
            this.label = label;
            this.hours = hours == null ? List.of() : List.copyOf(hours);
        }
    }
}
