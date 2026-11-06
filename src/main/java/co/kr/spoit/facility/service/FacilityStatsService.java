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
import co.kr.spoit.facility.dto.HourRoomCount;
import co.kr.spoit.facility.dto.WeekdayRoomCount;
import co.kr.spoit.facility.mapper.FacilityMapper;
import co.kr.spoit.facility.mapper.FacilityStatsMapper;

/**
 * 시설 관리자 예약 통계 화면의 집계를 담당한다.
 *
 * <p>집계 범위는 인증 주체가 담당하는 활성 시설 중 화면에서 선택한 시설 하나이며, 기간은 매칭방
 * {@code start_at} 기준이다. 관리자 식별자는 항상 인증 주체에서 받고 클라이언트 값을 신뢰하지
 * 않는다. 시설 미선택·미담당 시설이면 매퍼를 호출하지 않고 0건 통계를 돌려준다.</p>
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

        long bookingCount = facilityStatsMapper.countMatches(managerUserId, facilityId, startAt, endAt);
        long participantCount = facilityStatsMapper.sumParticipants(managerUserId, facilityId, startAt, endAt);
        ApplicationCountSummary applications = facilityStatsMapper.countApplications(managerUserId, facilityId,
                startAt, endAt);
        List<WeekdayRoomCount> weekdayCounts = facilityStatsMapper.countByWeekday(managerUserId, facilityId,
                startAt, endAt);
        List<HourRoomCount> hourCounts = facilityStatsMapper.countByHour(managerUserId, facilityId, startAt,
                endAt);

        WeekdayChart weekdayChart = buildWeekdayChart(weekdayCounts);
        HourChart hourChart = buildHourChart(hourCounts);

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

    /** 요일 7행과 최대 요일 라벨. 집계 대상이 없으면 라벨은 null이다. */
    private static WeekdayChart buildWeekdayChart(List<WeekdayRoomCount> counts) {
        long[] roomsByDay = new long[WEEKDAY_LABELS.size()];
        for (WeekdayRoomCount row : nullSafe(counts)) {
            if (row == null) {
                continue;
            }
            int index = weekdayIndex(row.getWeekday());
            if (index >= 0) {
                roomsByDay[index] = Math.max(0, row.getRoomCount());
            }
        }

        long max = 0;
        int popularIndex = -1;
        for (int index = 0; index < roomsByDay.length; index++) {
            if (roomsByDay[index] > max) {
                max = roomsByDay[index];
                popularIndex = index;
            }
        }

        List<FacilityStats.WeekdayShare> rows = new ArrayList<>(WEEKDAY_LABELS.size());
        for (int index = 0; index < roomsByDay.length; index++) {
            String tone = index == popularIndex && max > 0 ? TONE_GOOD : "";
            rows.add(new FacilityStats.WeekdayShare(WEEKDAY_LABELS.get(index), tone,
                    percent(roomsByDay[index], max)));
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
     * 집계된 시간대만 오름차순으로 담고, 최대 시간대 라벨을 함께 만든다.
     *
     * <p>집계 대상이 없으면 빈 목록과 null 라벨이다.</p>
     */
    private static HourChart buildHourChart(List<HourRoomCount> counts) {
        long[] roomsByHour = new long[24];
        for (HourRoomCount row : nullSafe(counts)) {
            if (row == null) {
                continue;
            }
            int hour = row.getHour();
            if (hour < 0 || hour >= roomsByHour.length) {
                continue;
            }
            roomsByHour[hour] = Math.max(0, row.getRoomCount());
        }

        long max = 0;
        int peakHour = -1;
        for (int hour = 0; hour < roomsByHour.length; hour++) {
            if (roomsByHour[hour] > max) {
                max = roomsByHour[hour];
                peakHour = hour;
            }
        }

        List<FacilityStats.PopularHour> rows = new ArrayList<>();
        for (int hour = 0; hour < roomsByHour.length; hour++) {
            if (roomsByHour[hour] <= 0) {
                continue;
            }
            rows.add(new FacilityStats.PopularHour(hourLabel(hour), roomsByHour[hour],
                    percent(roomsByHour[hour], max)));
        }
        return new HourChart(rows, peakHour < 0 ? null : hourLabel(peakHour));
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
