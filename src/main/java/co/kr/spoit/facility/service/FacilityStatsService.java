package co.kr.spoit.facility.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.facility.dto.ApplicationCountSummary;
import co.kr.spoit.facility.dto.FacilityStats;
import co.kr.spoit.facility.dto.WeekdayHourApplicationStatusCount;
import co.kr.spoit.facility.dto.WeekdayHourParticipantCount;
import co.kr.spoit.facility.mapper.FacilityMapper;
import co.kr.spoit.facility.mapper.FacilityStatsMapper;

/**
 * 시설 관리자 예약 통계 화면의 집계를 담당한다.
 *
 * <p>집계 범위는 인증 주체가 담당하는 활성 시설 중 화면에서 선택한 시설 하나이며, 기간은 매칭방
 * {@code start_at} 기준이다. 관리자 식별자는 항상 인증 주체에서 받고 클라이언트 값을 신뢰하지
 * 않는다. 시설 미선택·미담당 시설이면 매퍼를 호출하지 않고 0건 통계를 돌려준다.</p>
 *
 * <p>집계 대상은 상태가 {@code 종료}인 매칭방뿐이다(모집 중·매칭 완료·취소 제외). 요일·시간대
 * 분포는 매칭방 수가 아니라 종료 매칭의 확정 참가 인원
 * ({@code match_participant.participation_status = '확정'})을 센다. 참가자는 매칭별로 한 행씩
 * 세므로 같은 사람이 두 경기에 참여하면 2인으로 집계되고, 노쇼 여부는 구분하지 못한다.
 * 요일 합계·시간대 합계는 요일×시간대 집계 한 번에서 파생하며, 상세 비율({@code share})의 분모는
 * 전체 종료 매칭 확정 참가 인원이다.</p>
 *
 * <p>신청 지표는 같은 매칭방에 같은 사람이 여러 번 신청할 수 있으므로
 * {@code (match_id, applicant_user_id)}별 가장 최근 신청 한 건만 센다
 * ({@code created_at DESC, application_id DESC}). 팀 신청은 팀원이 각자 한 건이고, 같은 사람이 서로
 * 다른 매칭방에 신청하면 각각 센다. 최신 신청이 {@code 대기}면 신청 총계에는 들어가지만 승인율·취소율
 * 분모(처리 수)와 분포 차트에서는 빠진다. 처리 수는 {@code 확정 + 반려 + 취소}이고, 승인율은
 * {@code 확정 / 처리 수}, 취소율은 {@code (반려+취소) / 처리 수}를 반올림한 백분율이다(분모 0이면 0).
 * 신청 분포 차트도 같은 최신 신청 기준이며, 막대의 {@code totalCount}는 대기를 뺀 처리 수
 * ({@code approvedCount + cancelledCount})다. 요일·시간대·요일×시간대 목록과 차트 상한(최소 1)을
 * 서비스가 계산해 DTO에 담는다.</p>
 *
 * <p>기간 파라미터는 화면 계약({@code period=7/30/90/custom})을 그대로 받는다. 알 수 없는 값이나
 * 직접 입력 기간의 누락·역전·파싱 실패는 기본 30일로 되돌린다. 경계는 시작일 00:00:00부터
 * 종료일 23:59:59까지 양끝을 포함한다.</p>
 */
@Service
@Transactional(readOnly = true)
public class FacilityStatsService {

    /** 선택 가능한 기간(일). 이 밖의 값은 기본 기간으로 되돌린다. */
    private static final Set<Integer> ALLOWED_PERIODS = Set.of(7, 30, 90);

    /** 기간 파라미터가 없거나 잘못됐을 때 쓰는 기본 일수 */
    static final int DEFAULT_PERIOD = 30;

    /** 직접 입력 기간을 뜻하는 파라미터 값 */
    private static final String CUSTOM_PERIOD = "custom";

    /** 요일 상세 표시 순서(월~일)와 같은 순서의 라벨 */
    private static final List<String> WEEKDAY_LABELS = List.of("월", "화", "수", "목", "금", "토", "일");

    /** 하루 시간대 수(00~23시) */
    private static final int HOURS_PER_DAY = 24;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final FacilityStatsMapper facilityStatsMapper;
    private final FacilityMapper facilityMapper;
    private final Clock clock;

    @Autowired
    public FacilityStatsService(FacilityStatsMapper facilityStatsMapper, FacilityMapper facilityMapper) {
        this(facilityStatsMapper, facilityMapper, Clock.system(ZONE));
    }

    FacilityStatsService(FacilityStatsMapper facilityStatsMapper, FacilityMapper facilityMapper, Clock clock) {
        this.facilityStatsMapper = facilityStatsMapper;
        this.facilityMapper = facilityMapper;
        this.clock = clock;
    }

    /**
     * 선택 시설·기간의 예약 통계를 집계한다.
     *
     * @param managerUserId 인증 주체의 회원 ID. null·양수 아님·미담당 시설이면 0건 통계
     * @param facilityId    통계 시설 ID. null·양수 아님·미담당 시설이면 0건 통계
     * @param period        "7"/"30"/"90"/"custom". 그 밖의 값은 기본 30일
     * @param periodFrom    직접 입력 시작일(yyyy-MM-dd). custom일 때만 쓰고 실패하면 기본 기간
     * @param periodTo      직접 입력 종료일(yyyy-MM-dd). custom일 때만 쓰고 실패하면 기본 기간
     */
    public FacilityStats load(Long managerUserId, Long facilityId, String period, String periodFrom,
            String periodTo) {
        DateRange range = resolveRange(period, periodFrom, periodTo);
        FacilityStats empty = emptyStats(range.period(), range.label());
        empty.setRequestedPeriod(range.requestedPeriod());
        if (!hasManager(managerUserId) || facilityId == null || facilityId <= 0) {
            return empty;
        }
        // 담당자로 매핑된 시설만 집계한다. 삭제된 시설은 매퍼의 deleted_at 조건 때문에 0건이 된다.
        if (!facilityMapper.existsFacilityManager(facilityId, managerUserId)) {
            return empty;
        }

        LocalDateTime startAt = range.startAt();
        LocalDateTime endAt = range.endAt();

        long bookingCount = facilityStatsMapper.countFinishedMatches(managerUserId, facilityId, startAt, endAt);
        long participantCount = facilityStatsMapper.countConfirmedParticipants(managerUserId, facilityId, startAt,
                endAt);
        ApplicationCountSummary applications = facilityStatsMapper.countApplications(managerUserId, facilityId,
                startAt, endAt);
        List<WeekdayHourParticipantCount> weekdayHourCounts =
                facilityStatsMapper.countParticipantsByWeekdayAndHour(managerUserId, facilityId, startAt, endAt);
        List<WeekdayHourApplicationStatusCount> applicationStatusCounts =
                facilityStatsMapper.countApplicationStatusesByWeekdayAndHour(managerUserId, facilityId, startAt,
                        endAt);

        long[][] participantsByDayAndHour = participantsByDayAndHour(weekdayHourCounts);
        long[] participantsByDay = sumByWeekday(participantsByDayAndHour);
        long[] participantsByHour = sumByHour(participantsByDayAndHour);
        long participantsTotal = Math.max(0, participantCount);

        List<FacilityStats.WeekdayDetail> weekdayDetails =
                buildWeekdayDetails(participantsByDay, participantsTotal);
        List<FacilityStats.HourDetail> hourDetails =
                buildHourDetails(participantsByHour, participantsTotal);
        List<FacilityStats.WeekdayHourDetail> weekdayHourDetails =
                buildWeekdayHourDetails(participantsByDayAndHour);

        ApplicationDistribution applicationDistribution =
                applicationDistributionByDayAndHour(applicationStatusCounts);
        // 차트의 totalCount는 대기를 뺀 처리 수(확정+반려+취소)다. 신청 총계(applicationTotal)만 대기를 포함한다.
        long[][] applicationResolvedByDayAndHour = applicationDistribution.resolved();
        long[][] applicationApprovedByDayAndHour = applicationDistribution.approved();
        long[][] applicationCancelledByDayAndHour = applicationDistribution.cancelled();

        List<FacilityStats.ApplicationDistributionDetail> applicationWeekdayDetails =
                buildApplicationWeekdayDetails(applicationResolvedByDayAndHour, applicationApprovedByDayAndHour,
                        applicationCancelledByDayAndHour);
        List<FacilityStats.ApplicationDistributionDetail> applicationHourDetails =
                buildApplicationHourDetails(applicationResolvedByDayAndHour, applicationApprovedByDayAndHour,
                        applicationCancelledByDayAndHour);
        List<FacilityStats.ApplicationWeekdayHourDetail> applicationWeekdayHourDetails =
                buildApplicationWeekdayHourDetails(applicationResolvedByDayAndHour, applicationApprovedByDayAndHour,
                        applicationCancelledByDayAndHour);

        long applicationTotal = applications == null ? 0 : applications.getTotal();
        long individualCount = applications == null ? 0 : applications.getIndividualCount();
        long clubCount = applications == null ? 0 : applications.getClubCount();
        long approvedCount = applications == null ? 0 : applications.getApprovedCount();
        long cancelledCount = applications == null ? 0 : applications.getCancelledCount();
        long resolvedCount = Math.max(0, approvedCount) + Math.max(0, cancelledCount);

        FacilityStats stats = new FacilityStats(
                range.period(),
                range.label(),
                Math.max(0, bookingCount),
                Math.max(0, participantCount),
                popularWeekdayLabel(participantsByDay),
                peakHourLabel(participantsByHour),
                Math.max(0, applicationTotal),
                Math.max(0, individualCount),
                Math.max(0, clubCount),
                Math.max(0, approvedCount),
                approvalRate(approvedCount, resolvedCount));
        // 화면의 직접 입력 기간 칸이 실제 조회 구간을 그대로 보여 주도록 계산된 경계일을 채운다.
        stats.setPeriodFrom(range.startDate().toString());
        stats.setPeriodTo(range.endDate().toString());
        stats.setRequestedPeriod(range.requestedPeriod());
        stats.setWeekdayDetails(weekdayDetails);
        stats.setHourDetails(hourDetails);
        stats.setWeekdayHourDetails(weekdayHourDetails);
        stats.setCancelledCount(Math.max(0, cancelledCount));
        stats.setResolvedCount(resolvedCount);
        stats.setCancelRate(cancelRate(cancelledCount, resolvedCount));
        stats.setApplicationWeekdayDetails(applicationWeekdayDetails);
        stats.setApplicationHourDetails(applicationHourDetails);
        stats.setApplicationWeekdayHourDetails(applicationWeekdayHourDetails);
        stats.setWeekdayChartMax(weekdayChartMax(applicationApprovedByDayAndHour,
                applicationCancelledByDayAndHour));
        stats.setHourChartMax(hourChartMax(applicationApprovedByDayAndHour,
                applicationCancelledByDayAndHour));
        stats.setWeekdayHourChartMax(weekdayHourChartMax(applicationApprovedByDayAndHour,
                applicationCancelledByDayAndHour));
        return stats;
    }

    /* ---------------- 0건 화면 모델 ---------------- */

    /**
     * 담당 시설이 없거나 시설 미선택일 때 쓰는 0건 집계를 만든다.
     *
     * <p>화면이 항상 같은 모양을 갖도록 상세 집계(요일 7행·시간대 24행·요일×시간대 7행)와 신청 분포
     * (요일 7행·시간대 24행·요일×시간대 7행)도 0으로 채우고, 차트 상한은 최소 1로 둔다. 기간
     * 표시값만 호출자가 정하고 나머지 지표·인기 라벨은 0·null이며, 실제 조회 구간
     * ({@code periodFrom}·{@code periodTo})은 채우지 않는다.</p>
     */
    private static FacilityStats emptyStats(int period, String periodLabel) {
        FacilityStats stats = new FacilityStats(period, periodLabel, 0, 0, null, null, 0, 0, 0, 0, 0);
        stats.setWeekdayDetails(buildWeekdayDetails(new long[WEEKDAY_LABELS.size()], 0));
        stats.setHourDetails(buildHourDetails(new long[HOURS_PER_DAY], 0));
        stats.setWeekdayHourDetails(
                buildWeekdayHourDetails(new long[WEEKDAY_LABELS.size()][HOURS_PER_DAY]));
        long[][] zeroCounts = new long[WEEKDAY_LABELS.size()][HOURS_PER_DAY];
        stats.setApplicationWeekdayDetails(
                buildApplicationWeekdayDetails(zeroCounts, zeroCounts, zeroCounts));
        stats.setApplicationHourDetails(
                buildApplicationHourDetails(zeroCounts, zeroCounts, zeroCounts));
        stats.setApplicationWeekdayHourDetails(
                buildApplicationWeekdayHourDetails(zeroCounts, zeroCounts, zeroCounts));
        stats.setWeekdayChartMax(1);
        stats.setHourChartMax(1);
        stats.setWeekdayHourChartMax(1);
        return stats;
    }

    /* ---------------- 기간 정규화 ---------------- */

    /**
     * 화면 기간 파라미터를 실제 조회 구간으로 바꾼다.
     *
     * <p>{@code period}가 7/30/90이면 오늘을 포함한 과거 N일이고, {@code custom}이면 입력한
     * from~to를 쓴다. custom의 값이 없거나 파싱할 수 없거나 from이 to보다 뒤면 기본 30일로
     * 되돌린다.</p>
     */
    private DateRange resolveRange(String period, String periodFrom, String periodTo) {
        String normalized = period == null ? "" : period.strip();
        if (CUSTOM_PERIOD.equalsIgnoreCase(normalized)) {
            LocalDate from = parseDate(periodFrom);
            LocalDate to = parseDate(periodTo);
            if (from != null && to != null && !from.isAfter(to)) {
                return DateRange.of(from, to, CUSTOM_PERIOD);
            }
            return DateRange.lastDays(today(), DEFAULT_PERIOD);
        }
        Integer days = parseAllowedPeriod(normalized);
        return DateRange.lastDays(today(), days == null ? DEFAULT_PERIOD : days);
    }

    /** 7/30/90만 인정하고 그 밖의 값(공백·비숫자 포함)은 null이다. */
    private static Integer parseAllowedPeriod(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            return ALLOWED_PERIODS.contains(value) ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 잘못된 날짜 문자열은 없는 값으로 처리한다. */
    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * 조회 구간과 화면 문구를 함께 담는다.
     *
     * <p>{@code lastDays}는 종료일(오늘)을 포함해 N일을 뒤로 잡는다. 경계는 시작일 00:00:00부터
     * 종료일 23:59:59까지다.</p>
     */
    private record DateRange(int period, String label, String requestedPeriod, LocalDateTime startAt,
            LocalDateTime endAt) {

        static DateRange lastDays(LocalDate today, int days) {
            int safeDays = Math.max(1, days);
            LocalDate start = today.minusDays(safeDays - 1L);
            return new DateRange(safeDays, label(safeDays), String.valueOf(safeDays),
                    start.atStartOfDay(), endOfDay(today));
        }

        /** 직접 입력은 일수가 7/30/90과 같아도 select가 "직접 입력"을 유지하도록 표시값을 따로 둔다. */
        static DateRange of(LocalDate from, LocalDate to, String requestedPeriod) {
            int days = (int) (to.toEpochDay() - from.toEpochDay()) + 1;
            return new DateRange(days, label(days), requestedPeriod, from.atStartOfDay(), endOfDay(to));
        }

        /** 화면 직접 입력 칸에 되돌려 줄 시작일 */
        LocalDate startDate() {
            return startAt.toLocalDate();
        }

        /** 화면 직접 입력 칸에 되돌려 줄 종료일 */
        LocalDate endDate() {
            return endAt.toLocalDate();
        }

        private static LocalDateTime endOfDay(LocalDate date) {
            return date.atTime(23, 59, 59);
        }

        private static String label(int days) {
            return "최근 " + days + "일";
        }
    }

    /* ---------------- 차트 계산 ---------------- */

    /**
     * 요일×시간대 집계를 월~일 × 00~23시 배열로 옮긴다.
     *
     * <p>알 수 없는 요일·시간대 값은 버리고, 같은 칸의 값은 버리지 않고 합산한다.</p>
     */
    private static long[][] participantsByDayAndHour(List<WeekdayHourParticipantCount> counts) {
        long[][] participantsByDayAndHour = new long[WEEKDAY_LABELS.size()][HOURS_PER_DAY];
        for (WeekdayHourParticipantCount row : nullSafe(counts)) {
            if (row == null) {
                continue;
            }
            int dayIndex = weekdayIndex(row.getWeekday());
            int hour = row.getHour();
            if (dayIndex < 0 || hour < 0 || hour >= HOURS_PER_DAY) {
                continue;
            }
            participantsByDayAndHour[dayIndex][hour] += Math.max(0, row.getParticipantCount());
        }
        return participantsByDayAndHour;
    }

    /** 요일×시간대 배열에서 요일별 합계를 월~일 순서로 파생한다. */
    private static long[] sumByWeekday(long[][] participantsByDayAndHour) {
        long[] participantsByDay = new long[WEEKDAY_LABELS.size()];
        for (int dayIndex = 0; dayIndex < participantsByDay.length; dayIndex++) {
            long sum = 0;
            for (long count : participantsByDayAndHour[dayIndex]) {
                sum += count;
            }
            participantsByDay[dayIndex] = sum;
        }
        return participantsByDay;
    }

    /** 요일×시간대 배열에서 시간대별 합계를 00~23시 순서로 파생한다. */
    private static long[] sumByHour(long[][] participantsByDayAndHour) {
        long[] participantsByHour = new long[HOURS_PER_DAY];
        for (long[] hourlyCounts : participantsByDayAndHour) {
            for (int hour = 0; hour < participantsByHour.length; hour++) {
                participantsByHour[hour] += hourlyCounts[hour];
            }
        }
        return participantsByHour;
    }

    /* ---------------- 최신 신청 분포 계산 ---------------- */

    /**
     * 요일×시간대 신청 상태 건수를 월~일 × 00~23시 배열 세 벌(처리·확정·반려+취소)로 옮긴다.
     *
     * <p>차트 계약상 처리 수는 대기를 뺀 {@code 확정 + 반려+취소}다. SQL 행의 전체 건수
     * ({@code totalCount}, 대기 포함)는 차트에 쓰지 않는다. 알 수 없는 요일·시간대 값은 버리고,
     * 같은 칸의 값은 합산한다. 음수는 0으로 올린다.</p>
     */
    private static ApplicationDistribution applicationDistributionByDayAndHour(
            List<WeekdayHourApplicationStatusCount> counts) {
        long[][] resolved = new long[WEEKDAY_LABELS.size()][HOURS_PER_DAY];
        long[][] approved = new long[WEEKDAY_LABELS.size()][HOURS_PER_DAY];
        long[][] cancelled = new long[WEEKDAY_LABELS.size()][HOURS_PER_DAY];
        for (WeekdayHourApplicationStatusCount row : nullSafe(counts)) {
            if (row == null) {
                continue;
            }
            int dayIndex = weekdayIndex(row.getWeekday());
            int hour = row.getHour();
            if (dayIndex < 0 || hour < 0 || hour >= HOURS_PER_DAY) {
                continue;
            }
            approved[dayIndex][hour] += Math.max(0, row.getApprovedCount());
            cancelled[dayIndex][hour] += Math.max(0, row.getCancelledCount());
        }
        // 같은 칸의 중복 행을 합산한 뒤 처리 수를 확정+반려+취소로 다시 계산한다(대기 제외).
        for (int dayIndex = 0; dayIndex < WEEKDAY_LABELS.size(); dayIndex++) {
            for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
                resolved[dayIndex][hour] = approved[dayIndex][hour] + cancelled[dayIndex][hour];
            }
        }
        return new ApplicationDistribution(resolved, approved, cancelled);
    }

    /**
     * 요일별 최신 신청 분포 7행(월~일)을 만든다. 각 행은 그 요일의 확정·반려+취소·처리(확정+반려+취소)
     * 합계이며 대기는 세지 않는다.
     */
    private static List<FacilityStats.ApplicationDistributionDetail> buildApplicationWeekdayDetails(
            long[][] resolved, long[][] approved, long[][] cancelled) {
        List<FacilityStats.ApplicationDistributionDetail> rows = new ArrayList<>(WEEKDAY_LABELS.size());
        for (int dayIndex = 0; dayIndex < WEEKDAY_LABELS.size(); dayIndex++) {
            rows.add(new FacilityStats.ApplicationDistributionDetail(
                    WEEKDAY_LABELS.get(dayIndex),
                    sum(approved[dayIndex]),
                    sum(cancelled[dayIndex]),
                    sum(resolved[dayIndex])));
        }
        return rows;
    }

    /**
     * 시간대별 최신 신청 분포 24행(00~23시)을 만든다. 각 행은 그 시간대의 확정·반려+취소·
     * 처리(확정+반려+취소) 합계이며 대기는 세지 않는다.
     */
    private static List<FacilityStats.ApplicationDistributionDetail> buildApplicationHourDetails(
            long[][] resolved, long[][] approved, long[][] cancelled) {
        List<FacilityStats.ApplicationDistributionDetail> rows = new ArrayList<>(HOURS_PER_DAY);
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            long approvedSum = 0;
            long cancelledSum = 0;
            long resolvedSum = 0;
            for (int dayIndex = 0; dayIndex < WEEKDAY_LABELS.size(); dayIndex++) {
                approvedSum += approved[dayIndex][hour];
                cancelledSum += cancelled[dayIndex][hour];
                resolvedSum += resolved[dayIndex][hour];
            }
            rows.add(new FacilityStats.ApplicationDistributionDetail(hourLabel(hour), approvedSum,
                    cancelledSum, resolvedSum));
        }
        return rows;
    }

    /**
     * 요일×시간대 최신 신청 분포 7행(월~일)을 만든다. 각 행의 {@code hours}는 00~23시 순서 24개이고
     * 각 요소의 label은 그 시간대이며, 처리 수는 대기를 뺀 확정+반려+취소다.
     */
    private static List<FacilityStats.ApplicationWeekdayHourDetail> buildApplicationWeekdayHourDetails(
            long[][] resolved, long[][] approved, long[][] cancelled) {
        List<FacilityStats.ApplicationWeekdayHourDetail> rows = new ArrayList<>(WEEKDAY_LABELS.size());
        for (int dayIndex = 0; dayIndex < WEEKDAY_LABELS.size(); dayIndex++) {
            List<FacilityStats.ApplicationDistributionDetail> hours = new ArrayList<>(HOURS_PER_DAY);
            for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
                hours.add(new FacilityStats.ApplicationDistributionDetail(hourLabel(hour),
                        approved[dayIndex][hour], cancelled[dayIndex][hour], resolved[dayIndex][hour]));
            }
            rows.add(new FacilityStats.ApplicationWeekdayHourDetail(WEEKDAY_LABELS.get(dayIndex), hours));
        }
        return rows;
    }

    /**
     * 요일 차트 상한. 각 요일의 처리 건수(확정+반려+취소) 중 최대값이며 최소 1이다.
     * 대기는 막대에서 빠지므로 상한에도 넣지 않는다.
     */
    private static long weekdayChartMax(long[][] approved, long[][] cancelled) {
        long max = 0;
        for (int dayIndex = 0; dayIndex < WEEKDAY_LABELS.size(); dayIndex++) {
            max = Math.max(max, sum(approved[dayIndex]) + sum(cancelled[dayIndex]));
        }
        return Math.max(1, max);
    }

    /** 시간대 차트 상한. 각 시간대의 처리 건수(확정+반려+취소) 중 최대값이며 최소 1이다. */
    private static long hourChartMax(long[][] approved, long[][] cancelled) {
        long max = 0;
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            long approvedSum = 0;
            long cancelledSum = 0;
            for (int dayIndex = 0; dayIndex < WEEKDAY_LABELS.size(); dayIndex++) {
                approvedSum += approved[dayIndex][hour];
                cancelledSum += cancelled[dayIndex][hour];
            }
            max = Math.max(max, approvedSum + cancelledSum);
        }
        return Math.max(1, max);
    }

    /** 요일×시간대 차트 상한. 한 칸의 처리 건수(확정+반려+취소) 최대값이며 최소 1이다. */
    private static long weekdayHourChartMax(long[][] approved, long[][] cancelled) {
        long max = 0;
        for (int dayIndex = 0; dayIndex < WEEKDAY_LABELS.size(); dayIndex++) {
            for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
                max = Math.max(max, approved[dayIndex][hour] + cancelled[dayIndex][hour]);
            }
        }
        return Math.max(1, max);
    }

    /** 배열 원소의 합. */
    private static long sum(long[] values) {
        long sum = 0;
        for (long value : values) {
            sum += value;
        }
        return sum;
    }

    /** 최신 신청 상태를 월~일 × 00~23시 배열 세 벌(처리·확정·반려+취소)로 담는다. */
    private record ApplicationDistribution(long[][] resolved, long[][] approved, long[][] cancelled) {
    }

    /**
     * 확정 참가 인원이 가장 많은 요일 라벨(월~일 순서)을 고른다. 동률이면 먼저 나오는 요일이고,
     * 집계 대상이 없으면 null이다.
     */
    private static String popularWeekdayLabel(long[] participantsByDay) {
        int popularIndex = -1;
        long max = 0;
        for (int index = 0; index < participantsByDay.length; index++) {
            if (participantsByDay[index] > max) {
                max = participantsByDay[index];
                popularIndex = index;
            }
        }
        return popularIndex < 0 ? null : WEEKDAY_LABELS.get(popularIndex);
    }

    /**
     * 확정 참가 인원이 가장 많은 시간대 라벨(00~23시 순서)을 고른다. 동률이면 이른 시간대이고,
     * 집계 대상이 없으면 null이다.
     */
    private static String peakHourLabel(long[] participantsByHour) {
        int peakHour = -1;
        long max = 0;
        for (int hour = 0; hour < participantsByHour.length; hour++) {
            if (participantsByHour[hour] > max) {
                max = participantsByHour[hour];
                peakHour = hour;
            }
        }
        return peakHour < 0 ? null : hourLabel(peakHour);
    }

    /** MySQL DAYOFWEEK(1=일, 2=월, … 7=토)를 월~일 인덱스로 바꾼다. 범위 밖은 -1이다. */
    private static int weekdayIndex(long dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            return -1;
        }
        return (int) (dayOfWeek == 1 ? 6 : dayOfWeek - 2);
    }

    /** 요일 상세 7행(월~일). share는 전체 확정 참가 인원 대비 반올림 백분율이며 전체가 0이면 0이다. */
    private static List<FacilityStats.WeekdayDetail> buildWeekdayDetails(long[] participantsByDay,
            long participantsTotal) {
        List<FacilityStats.WeekdayDetail> rows = new ArrayList<>(WEEKDAY_LABELS.size());
        for (int index = 0; index < participantsByDay.length; index++) {
            rows.add(new FacilityStats.WeekdayDetail(WEEKDAY_LABELS.get(index), participantsByDay[index],
                    share(participantsByDay[index], participantsTotal)));
        }
        return rows;
    }

    /** 시간대 상세 24행(00~23시). share는 전체 확정 참가 인원 대비 반올림 백분율이며 전체가 0이면 0이다. */
    private static List<FacilityStats.HourDetail> buildHourDetails(long[] participantsByHour,
            long participantsTotal) {
        List<FacilityStats.HourDetail> rows = new ArrayList<>(participantsByHour.length);
        for (int hour = 0; hour < participantsByHour.length; hour++) {
            rows.add(new FacilityStats.HourDetail(hourLabel(hour), participantsByHour[hour],
                    share(participantsByHour[hour], participantsTotal)));
        }
        return rows;
    }

    /** 요일×시간대 상세 7행(월~일)의 시간대 24칸(00~23시)을 집계값으로 채운다. */
    private static List<FacilityStats.WeekdayHourDetail> buildWeekdayHourDetails(
            long[][] participantsByDayAndHour) {
        List<FacilityStats.WeekdayHourDetail> rows = new ArrayList<>(WEEKDAY_LABELS.size());
        for (int dayIndex = 0; dayIndex < WEEKDAY_LABELS.size(); dayIndex++) {
            List<Long> hourlyCounts = new ArrayList<>(HOURS_PER_DAY);
            for (long count : participantsByDayAndHour[dayIndex]) {
                hourlyCounts.add(count);
            }
            rows.add(new FacilityStats.WeekdayHourDetail(WEEKDAY_LABELS.get(dayIndex), hourlyCounts));
        }
        return rows;
    }

    /** 전체 대비 비율(%). 전체가 0 이하이면 0이고, 그 밖에는 반올림한 정수 백분율이다. */
    private static int share(long count, long total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round(Math.max(0, count) * 100.0 / total);
    }

    /** "00시" ~ "23시". 대시보드 시간대 차트와 같은 표기다. */
    private static String hourLabel(int hour) {
        return String.format("%02d시", hour);
    }

    /** 승인율(%) = 확정 / 처리 수. 처리 수가 0이면 0이고, 그 밖에는 반올림한 정수 백분율이다. */
    private static double approvalRate(long approvedCount, long resolvedCount) {
        if (resolvedCount <= 0) {
            return 0;
        }
        return Math.round(Math.max(0, approvedCount) * 100.0 / resolvedCount);
    }

    /** 취소율(%) = (반려+취소) / 처리 수. 처리 수가 0이면 0이고, 그 밖에는 반올림한 정수 백분율이다. */
    private static double cancelRate(long cancelledCount, long resolvedCount) {
        if (resolvedCount <= 0) {
            return 0;
        }
        return Math.round(Math.max(0, cancelledCount) * 100.0 / resolvedCount);
    }

    /* ---------------- 공통 ---------------- */

    private static boolean hasManager(Long managerUserId) {
        return managerUserId != null && managerUserId > 0;
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
