package co.kr.spoit.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.kr.spoit.facility.dto.ApplicationCountSummary;
import co.kr.spoit.facility.dto.FacilityStats;
import co.kr.spoit.facility.dto.HourRoomCount;
import co.kr.spoit.facility.dto.WeekdayRoomCount;
import co.kr.spoit.facility.mapper.FacilityMapper;
import co.kr.spoit.facility.mapper.FacilityStatsMapper;

/** 예약 통계 서비스의 기간 정규화·권한 범위·백분율 계산을 검증한다. */
@ExtendWith(MockitoExtension.class)
class FacilityStatsServiceTest {

    private static final long USER_ID = 7L;
    private static final long FACILITY_ID = 10L;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 2026-11-06 12:00 KST. 오늘 날짜 경계 계산의 기준이다. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T03:00:00Z"), ZONE);

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    @Mock
    private FacilityStatsMapper facilityStatsMapper;

    @Mock
    private FacilityMapper facilityMapper;

    private FacilityStatsService service;

    @BeforeEach
    void setUp() {
        service = new FacilityStatsService(facilityStatsMapper, facilityMapper, CLOCK);
    }

    /* ---------------- 기간 정규화 ---------------- */

    @Test
    @DisplayName("period가 7/30/90이 아니면 기본 30일(오늘 포함)로 되돌린다")
    void fallsBackToThirtyDaysForUnknownPeriod() {
        stubOwnedFacilityWithNoRooms();

        for (String period : List.of("0", "-5", "999", "abc", "", "15")) {
            FacilityStats stats = service.load(USER_ID, FACILITY_ID, period, null, null);
            assertThat(stats.getPeriod()).as("period=%s", period).isEqualTo(30);
            assertThat(stats.getPeriodLabel()).isEqualTo("최근 30일");
        }

        verify(facilityStatsMapper, org.mockito.Mockito.atLeastOnce())
                .countMatches(eq(USER_ID), eq(FACILITY_ID), eq(TODAY.minusDays(29).atStartOfDay()),
                        eq(TODAY.atTime(23, 59, 59)));
    }

    @Test
    @DisplayName("period=7/90이면 오늘을 포함한 과거 N일 구간으로 조회한다")
    void resolvesDayPeriodsIncludingToday() {
        stubOwnedFacilityWithNoRooms();

        service.load(USER_ID, FACILITY_ID, "7", null, null);
        verify(facilityStatsMapper).countMatches(eq(USER_ID), eq(FACILITY_ID),
                eq(TODAY.minusDays(6).atStartOfDay()), eq(TODAY.atTime(23, 59, 59)));

        service.load(USER_ID, FACILITY_ID, "90", null, null);
        verify(facilityStatsMapper).countMatches(eq(USER_ID), eq(FACILITY_ID),
                eq(TODAY.minusDays(89).atStartOfDay()), eq(TODAY.atTime(23, 59, 59)));
    }

    @Test
    @DisplayName("custom 기간이 유효하면 입력한 from~to 구간을 그대로 쓴다")
    void usesCustomRangeWhenValid() {
        stubOwnedFacilityWithNoRooms();

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "custom", "2026-10-01", "2026-10-31");

        assertThat(stats.getPeriod()).isEqualTo(31);
        verify(facilityStatsMapper).countMatches(eq(USER_ID), eq(FACILITY_ID),
                eq(LocalDate.of(2026, 10, 1).atStartOfDay()),
                eq(LocalDate.of(2026, 10, 31).atTime(23, 59, 59)));
    }

    @Test
    @DisplayName("custom 기간이 누락·파싱 실패·역전이면 기본 30일로 되돌린다")
    void fallsBackToThirtyDaysForInvalidCustomRange() {
        stubOwnedFacilityWithNoRooms();

        String[][] invalidRanges = {
                {null, null},
                {"2026-10-01", null},
                {"2026-10-01", "not-a-date"},
                {"2026-10-31", "2026-10-01"}};

        for (String[] range : invalidRanges) {
            FacilityStats stats = service.load(USER_ID, FACILITY_ID, "custom", range[0], range[1]);
            assertThat(stats.getPeriod()).as("from=%s to=%s", range[0], range[1]).isEqualTo(30);
            assertThat(stats.getPeriodLabel()).isEqualTo("최근 30일");
        }

        verify(facilityStatsMapper, never()).countMatches(anyLong(), anyLong(),
                eq(LocalDateTime.of(2026, 10, 31, 0, 0)), any());
        verify(facilityStatsMapper, org.mockito.Mockito.atLeastOnce())
                .countMatches(eq(USER_ID), eq(FACILITY_ID), eq(TODAY.minusDays(29).atStartOfDay()),
                        eq(TODAY.atTime(23, 59, 59)));
    }

    @Test
    @DisplayName("period=7 정규화 시 periodFrom/periodTo가 오늘-6일/오늘로 채워진다")
    void fillsPeriodRangeFieldsForDayPeriod() {
        stubOwnedFacilityWithNoRooms();

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "7", null, null);

        assertThat(stats.getPeriodFrom()).isEqualTo("2026-10-31");
        assertThat(stats.getPeriodTo()).isEqualTo("2026-11-06");
    }

    @Test
    @DisplayName("custom 기간이 유효하면 periodFrom/periodTo가 입력값 그대로 채워진다")
    void fillsPeriodRangeFieldsForCustomPeriod() {
        stubOwnedFacilityWithNoRooms();

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "custom", "2026-10-01", "2026-10-31");

        assertThat(stats.getPeriodFrom()).isEqualTo("2026-10-01");
        assertThat(stats.getPeriodTo()).isEqualTo("2026-10-31");
    }

    @Test
    @DisplayName("requestedPeriod는 기간 select 복원을 위해 요청값(custom 포함)을 그대로 담는다")
    void keepsRequestedPeriodForSelectRestoration() {
        stubOwnedFacilityWithNoRooms();

        // 일수가 7일과 같아도 custom이면 custom으로 남아야 select가 "직접 입력"을 유지한다.
        FacilityStats custom = service.load(USER_ID, FACILITY_ID, "custom", "2026-10-30", "2026-11-05");
        assertThat(custom.getPeriod()).isEqualTo(7);
        assertThat(custom.getRequestedPeriod()).isEqualTo("custom");

        assertThat(service.load(USER_ID, FACILITY_ID, "90", null, null).getRequestedPeriod())
                .isEqualTo("90");
        // 알 수 없는 값은 기본 30일로 정규화되므로 표시값도 30이다.
        assertThat(service.load(USER_ID, FACILITY_ID, "abc", null, null).getRequestedPeriod())
                .isEqualTo("30");
        // 시설 미선택으로 매퍼를 부르지 않는 빈 통계도 같은 값을 유지한다.
        assertThat(service.load(USER_ID, null, "custom", "2026-10-30", "2026-11-05")
                .getRequestedPeriod()).isEqualTo("custom");
    }

    /* ---------------- 권한 범위 ---------------- */

    @Test
    @DisplayName("시설 ID가 없으면 매퍼를 호출하지 않고 빈 통계를 반환한다")
    void returnsEmptyWithoutFacilityId() {
        FacilityStats stats = service.load(USER_ID, null, "30", null, null);

        assertThat(stats.getBookingCount()).isZero();
        assertThat(stats.getWeekdays()).hasSize(7);
        verifyNoInteractions(facilityStatsMapper, facilityMapper);
    }

    @Test
    @DisplayName("담당하지 않은 시설이면 집계 매퍼를 호출하지 않고 빈 통계를 반환한다")
    void returnsEmptyWhenFacilityNotManaged() {
        when(facilityMapper.existsFacilityManager(FACILITY_ID, USER_ID)).thenReturn(false);

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getBookingCount()).isZero();
        assertThat(stats.getApprovalRate()).isZero();
        verifyNoInteractions(facilityStatsMapper);
    }

    @Test
    @DisplayName("관리자 ID가 없으면 시설 담당 조회도 하지 않고 빈 통계를 반환한다")
    void returnsEmptyWithoutManagerId() {
        FacilityStats stats = service.load(null, FACILITY_ID, "30", null, null);

        assertThat(stats.getBookingCount()).isZero();
        verifyNoInteractions(facilityStatsMapper, facilityMapper);
    }

    /* ---------------- 계산 ---------------- */

    @Test
    @DisplayName("요일·시간대 백분율과 height를 최대값 대비로 계산한다")
    void calculatesPercentAndHeightAgainstMaximum() {
        stubOwnedFacility();
        when(facilityStatsMapper.countMatches(eq(USER_ID), eq(FACILITY_ID), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(10L);
        when(facilityStatsMapper.sumParticipants(eq(USER_ID), eq(FACILITY_ID), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(42L);
        when(facilityStatsMapper.countApplications(eq(USER_ID), eq(FACILITY_ID), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(summary(4, 3, 1, 3));
        when(facilityStatsMapper.countByWeekday(eq(USER_ID), eq(FACILITY_ID), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(List.of(
                        weekday(2, 3),   // 월
                        weekday(4, 1),   // 수
                        weekday(7, 0)));  // 토
        when(facilityStatsMapper.countByHour(eq(USER_ID), eq(FACILITY_ID), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(List.of(
                        hour(18, 2),
                        hour(20, 5),
                        hour(9, 1)));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getBookingCount()).isEqualTo(10L);
        assertThat(stats.getParticipantCount()).isEqualTo(42L);

        assertThat(stats.getWeekdays()).extracting(FacilityStats.WeekdayShare::getLabel)
                .containsExactly("월", "화", "수", "목", "금", "토", "일");
        assertThat(stats.getWeekdays()).extracting(FacilityStats.WeekdayShare::getPercent)
                .containsExactly(100, 0, 33, 0, 0, 0, 0);
        assertThat(stats.getWeekdays().get(0).getTone()).isEqualTo("good");

        assertThat(stats.getPopularHours()).extracting(FacilityStats.PopularHour::getLabel)
                .containsExactly("09시", "18시", "20시");
        assertThat(stats.getPopularHours()).extracting(FacilityStats.PopularHour::getHeight)
                .containsExactly(20, 40, 100);
        assertThat(stats.getPopularHours()).extracting(FacilityStats.PopularHour::getCount)
                .containsExactly(1L, 2L, 5L);

        assertThat(stats.getPopularDay()).isEqualTo("월");
        assertThat(stats.getPeakTime()).isEqualTo("20시");
    }

    @Test
    @DisplayName("신청 총계가 0이면 승인율은 0이고 신청 지표도 0이다")
    void approvalRateIsZeroWithoutApplications() {
        stubOwnedFacility();
        stubCounts(0, 0, summary(0, 0, 0, 0), List.of(), List.of());

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getApplicationTotal()).isZero();
        assertThat(stats.getApprovalRate()).isZero();
        assertThat(stats.getPopularDay()).isNull();
        assertThat(stats.getPeakTime()).isNull();
        assertThat(stats.getPopularHours()).isEmpty();
    }

    @Test
    @DisplayName("승인율은 확정/총계 백분율을 반올림하고 신청 구분을 그대로 담는다")
    void calculatesApprovalRateWithRounding() {
        stubOwnedFacility();
        stubCounts(0, 0, summary(3, 2, 1, 2), List.of(), List.of());

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getApplicationTotal()).isEqualTo(3L);
        assertThat(stats.getIndividualCount()).isEqualTo(2L);
        assertThat(stats.getClubCount()).isEqualTo(1L);
        assertThat(stats.getApprovedCount()).isEqualTo(2L);
        assertThat(stats.getApprovalRate()).isEqualTo(67.0);
    }

    @Test
    @DisplayName("시간대 집계가 비어 있으면 인기 시간대 라벨은 null이고 목록은 비어 있다")
    void popularHourLabelsAreNullWhenNothingCollected() {
        stubOwnedFacility();
        stubCounts(5, 8, summary(0, 0, 0, 0), List.of(weekday(1, 0)), List.of());

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getPopularDay()).isNull();
        assertThat(stats.getPeakTime()).isNull();
        assertThat(stats.getPopularHours()).isEmpty();
        assertThat(stats.getWeekdays()).extracting(FacilityStats.WeekdayShare::getPercent)
                .containsExactly(0, 0, 0, 0, 0, 0, 0);
    }

    /* ---------------- 도우미 ---------------- */

    private void stubOwnedFacility() {
        when(facilityMapper.existsFacilityManager(FACILITY_ID, USER_ID)).thenReturn(true);
    }

    private void stubOwnedFacilityWithNoRooms() {
        stubOwnedFacility();
        stubCounts(0, 0, summary(0, 0, 0, 0), List.of(), List.of());
    }

    private void stubCounts(long matches, long participants, ApplicationCountSummary applications,
            List<WeekdayRoomCount> weekdays, List<HourRoomCount> hours) {
        // 기간 경계 자체는 별도 테스트에서 verify로 검증하므로 여기서는 구간과 무관하게 응답만 고정한다.
        when(facilityStatsMapper.countMatches(eq(USER_ID), eq(FACILITY_ID), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(matches);
        when(facilityStatsMapper.sumParticipants(eq(USER_ID), eq(FACILITY_ID), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(participants);
        when(facilityStatsMapper.countApplications(eq(USER_ID), eq(FACILITY_ID), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(applications);
        when(facilityStatsMapper.countByWeekday(eq(USER_ID), eq(FACILITY_ID), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(weekdays);
        when(facilityStatsMapper.countByHour(eq(USER_ID), eq(FACILITY_ID), any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(hours);
    }

    private static ApplicationCountSummary summary(long total, long individual, long club, long approved) {
        ApplicationCountSummary summary = new ApplicationCountSummary();
        summary.setTotal(total);
        summary.setIndividualCount(individual);
        summary.setClubCount(club);
        summary.setApprovedCount(approved);
        return summary;
    }

    private static WeekdayRoomCount weekday(int dayOfWeek, long roomCount) {
        WeekdayRoomCount row = new WeekdayRoomCount();
        row.setWeekday(dayOfWeek);
        row.setRoomCount(roomCount);
        return row;
    }

    private static HourRoomCount hour(int hour, long roomCount) {
        HourRoomCount row = new HourRoomCount();
        row.setHour(hour);
        row.setRoomCount(roomCount);
        return row;
    }
}
