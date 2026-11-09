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
import co.kr.spoit.facility.dto.HourParticipantCount;
import co.kr.spoit.facility.dto.WeekdayHourParticipantCount;
import co.kr.spoit.facility.dto.WeekdayParticipantCount;
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
 * 상세 비율({@code share})의 분모는 전체 종료 매칭 확정 참가 인원이다.</p>
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

    /** 요일 차트 표시 순서(월~일)와 같은 순서의 라벨 */
    private static final List<String> WEEKDAY_LABELS = List.of("월", "화", "수", "목", "금", "토", "일");

    private static final String TONE_GOOD = "good";

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
        FacilityStats empty = FacilityStats.empty(range.period(), range.label());
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
        List<WeekdayParticipantCount> weekdayCounts = facilityStatsMapper.countParticipantsByWeekday(
                managerUserId, facilityId, startAt, endAt);
        List<HourParticipantCount> hourCounts = facilityStatsMapper.countParticipantsByHour(managerUserId,
                facilityId, startAt, endAt);
        List<WeekdayHourParticipantCount> weekdayHourCounts =
                facilityStatsMapper.countParticipantsByWeekdayAndHour(managerUserId, facilityId, startAt, endAt);

        long[] participantsByDay = participantsByWeekday(weekdayCounts);
        long[] participantsByHour = participantsByHour(hourCounts);
        long participantsTotal = Math.max(0, participantCount);

        WeekdayChart weekdayChart = buildWeekdayChart(participantsByDay);
        HourChart hourChart = buildHourChart(participantsByHour);
        List<FacilityStats.WeekdayDetail> weekdayDetails =
                buildWeekdayDetails(participantsByDay, participantsTotal);
        List<FacilityStats.HourDetail> hourDetails =
                buildHourDetails(participantsByHour, participantsTotal);
        List<FacilityStats.WeekdayHourDetail> weekdayHourDetails =
                buildWeekdayHourDetails(weekdayHourCounts);

        long applicationTotal = applications == null ? 0 : applications.getTotal();
        long individualCount = applications == null ? 0 : applications.getIndividualCount();
        long clubCount = applications == null ? 0 : applications.getClubCount();
        long approvedCount = applications == null ? 0 : applications.getApprovedCount();

        FacilityStats stats = new FacilityStats(
                range.period(),
                range.label(),
                Math.max(0, bookingCount),
                Math.max(0, participantCount),
                weekdayChart.popularDay(),
                hourChart.peakTime(),
                weekdayChart.rows(),
                hourChart.rows(),
                Math.max(0, applicationTotal),
                Math.max(0, individualCount),
                Math.max(0, clubCount),
                Math.max(0, approvedCount),
                approvalRate(approvedCount, applicationTotal));
        // 화면의 직접 입력 기간 칸이 실제 조회 구간을 그대로 보여 주도록 계산된 경계일을 채운다.
        stats.setPeriodFrom(range.startDate().toString());
        stats.setPeriodTo(range.endDate().toString());
        stats.setRequestedPeriod(range.requestedPeriod());
        stats.setWeekdayDetails(weekdayDetails);
        stats.setHourDetails(hourDetails);
        stats.setWeekdayHourDetails(weekdayHourDetails);
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

    /** WEEKDAY_LABELS(월~일) 순서로 집계한 요일별 확정 참가 인원. 범위 밖 값은 버린다. */
    private static long[] participantsByWeekday(List<WeekdayParticipantCount> counts) {
        long[] participantsByDay = new long[WEEKDAY_LABELS.size()];
        for (WeekdayParticipantCount row : nullSafe(counts)) {
            if (row == null) {
                continue;
            }
            int index = weekdayIndex(row.getWeekday());
            if (index >= 0) {
                participantsByDay[index] = Math.max(0, row.getParticipantCount());
            }
        }
        return participantsByDay;
    }

    /** 00~23시 순서로 집계한 시간대별 확정 참가 인원. 범위 밖 값은 버린다. */
    private static long[] participantsByHour(List<HourParticipantCount> counts) {
        long[] participantsByHour = new long[24];
        for (HourParticipantCount row : nullSafe(counts)) {
            if (row == null) {
                continue;
            }
            int hour = row.getHour();
            if (hour < 0 || hour >= participantsByHour.length) {
                continue;
            }
            participantsByHour[hour] = Math.max(0, row.getParticipantCount());
        }
        return participantsByHour;
    }

    /** 요일 7행과 최대 참가 인원 요일 라벨. 집계 대상이 없으면 라벨은 null이다. */
    private static WeekdayChart buildWeekdayChart(long[] participantsByDay) {
        long max = 0;
        int popularIndex = -1;
        for (int index = 0; index < participantsByDay.length; index++) {
            if (participantsByDay[index] > max) {
                max = participantsByDay[index];
                popularIndex = index;
            }
        }

        List<FacilityStats.WeekdayShare> rows = new ArrayList<>(WEEKDAY_LABELS.size());
        for (int index = 0; index < participantsByDay.length; index++) {
            String tone = index == popularIndex && max > 0 ? TONE_GOOD : "";
            rows.add(new FacilityStats.WeekdayShare(WEEKDAY_LABELS.get(index), tone,
                    percent(participantsByDay[index], max)));
        }
        return new WeekdayChart(rows, popularIndex < 0 ? null : WEEKDAY_LABELS.get(popularIndex));
    }

    /** MySQL DAYOFWEEK(1=일, 2=월, … 7=토)를 월~일 인덱스로 바꾼다. 범위 밖은 -1이다. */
    private static int weekdayIndex(long dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            return -1;
        }
        return (int) (dayOfWeek == 1 ? 6 : dayOfWeek - 2);
    }

    /**
     * 집계된 시간대만 오름차순으로 담고, 최대 참가 인원 시간대 라벨을 함께 만든다.
     *
     * <p>집계 대상이 없으면 빈 목록과 null 라벨이다.</p>
     */
    private static HourChart buildHourChart(long[] participantsByHour) {
        long max = 0;
        int peakHour = -1;
        for (int hour = 0; hour < participantsByHour.length; hour++) {
            if (participantsByHour[hour] > max) {
                max = participantsByHour[hour];
                peakHour = hour;
            }
        }

        List<FacilityStats.PopularHour> rows = new ArrayList<>();
        for (int hour = 0; hour < participantsByHour.length; hour++) {
            if (participantsByHour[hour] <= 0) {
                continue;
            }
            rows.add(new FacilityStats.PopularHour(hourLabel(hour), participantsByHour[hour],
                    percent(participantsByHour[hour], max)));
        }
        return new HourChart(rows, peakHour < 0 ? null : hourLabel(peakHour));
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

    /**
     * 요일×시간대 상세 7행(월~일)을 만들고 각 행의 시간대 24칸(00~23시)을 집계값으로 채운다.
     *
     * <p>집계되지 않은 칸은 0이며, 알 수 없는 요일·시간대 값은 버린다.</p>
     */
    private static List<FacilityStats.WeekdayHourDetail> buildWeekdayHourDetails(
            List<WeekdayHourParticipantCount> counts) {
        long[][] participantsByDayAndHour = new long[WEEKDAY_LABELS.size()][24];
        for (WeekdayHourParticipantCount row : nullSafe(counts)) {
            if (row == null) {
                continue;
            }
            int dayIndex = weekdayIndex(row.getWeekday());
            int hour = row.getHour();
            if (dayIndex < 0 || hour < 0 || hour >= participantsByDayAndHour[dayIndex].length) {
                continue;
            }
            participantsByDayAndHour[dayIndex][hour] = Math.max(0, row.getParticipantCount());
        }

        List<FacilityStats.WeekdayHourDetail> rows = new ArrayList<>(WEEKDAY_LABELS.size());
        for (int dayIndex = 0; dayIndex < WEEKDAY_LABELS.size(); dayIndex++) {
            List<Long> hourlyCounts = new ArrayList<>(participantsByDayAndHour[dayIndex].length);
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

    /** 최대값 대비 상대 백분율(0~100). 최대값이 0이면 0이다. */
    private static int percent(long count, long max) {
        if (max <= 0) {
            return 0;
        }
        return (int) Math.round(count * 100.0 / max);
    }

    /** 승인율(%). 신청 총계가 0이면 0이고, 그 밖에는 반올림한 정수 백분율이다. */
    private static double approvalRate(long approvedCount, long applicationTotal) {
        if (applicationTotal <= 0) {
            return 0;
        }
        return Math.round(Math.max(0, approvedCount) * 100.0 / applicationTotal);
    }

    private record WeekdayChart(List<FacilityStats.WeekdayShare> rows, String popularDay) {
    }

    private record HourChart(List<FacilityStats.PopularHour> rows, String peakTime) {
    }

    /* ---------------- 공통 ---------------- */

    private static boolean hasManager(Long managerUserId) {
        return managerUserId != null && managerUserId > 0;
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
