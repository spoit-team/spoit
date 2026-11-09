package co.kr.spoit.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
import co.kr.spoit.facility.dto.HourParticipantCount;
import co.kr.spoit.facility.dto.WeekdayHourParticipantCount;
import co.kr.spoit.facility.dto.WeekdayParticipantCount;
import co.kr.spoit.facility.mapper.FacilityMapper;
import co.kr.spoit.facility.mapper.FacilityStatsMapper;

/**
 * 예약 통계 서비스의 기간 정규화·권한 범위·참가 인원 기준 백분율 계산을 검증한다.
 *
 * <p>{@code bookingCount}는 종료 매칭방 수, 요일·시간대 {@code count}는 확정 참가 인원,
 * 상세 {@code share}는 전체 확정 참가 인원({@code participantCount}) 대비 반올림 백분율이다.</p>
 */
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
        stubOwnedFacilityWithNoMatches();

        for (String period : List.of("0", "-5", "999", "abc", "", "15")) {
            FacilityStats stats = service.load(USER_ID, FACILITY_ID, period, null, null);
            assertThat(stats.getPeriod()).as("period=%s", period).isEqualTo(30);
            assertThat(stats.getPeriodLabel()).isEqualTo("최근 30일");
        }

        verify(facilityStatsMapper, org.mockito.Mockito.atLeastOnce())
                .countFinishedMatches(eq(USER_ID), eq(FACILITY_ID),
                        eq(TODAY.minusDays(29).atStartOfDay()), eq(TODAY.atTime(23, 59, 59)));
    }

    @Test
    @DisplayName("period=7/90이면 오늘을 포함한 과거 N일 구간으로 조회한다")
    void resolvesDayPeriodsIncludingToday() {
        stubOwnedFacilityWithNoMatches();

        service.load(USER_ID, FACILITY_ID, "7", null, null);
        verify(facilityStatsMapper).countFinishedMatches(eq(USER_ID), eq(FACILITY_ID),
                eq(TODAY.minusDays(6).atStartOfDay()), eq(TODAY.atTime(23, 59, 59)));

        service.load(USER_ID, FACILITY_ID, "90", null, null);
        verify(facilityStatsMapper).countFinishedMatches(eq(USER_ID), eq(FACILITY_ID),
                eq(TODAY.minusDays(89).atStartOfDay()), eq(TODAY.atTime(23, 59, 59)));
    }

    @Test
    @DisplayName("custom 기간이 유효하면 입력한 from~to 구간을 그대로 쓴다")
    void usesCustomRangeWhenValid() {
        stubOwnedFacilityWithNoMatches();

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "custom", "2026-10-01", "2026-10-31");

        assertThat(stats.getPeriod()).isEqualTo(31);
        verify(facilityStatsMapper).countFinishedMatches(eq(USER_ID), eq(FACILITY_ID),
                eq(LocalDate.of(2026, 10, 1).atStartOfDay()),
                eq(LocalDate.of(2026, 10, 31).atTime(23, 59, 59)));
    }

    @Test
    @DisplayName("custom 기간이 누락·파싱 실패·역전이면 기본 30일로 되돌린다")
    void fallsBackToThirtyDaysForInvalidCustomRange() {
        stubOwnedFacilityWithNoMatches();

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

        verify(facilityStatsMapper, never()).countFinishedMatches(anyLong(), anyLong(),
                eq(LocalDateTime.of(2026, 10, 31, 0, 0)), any());
        verify(facilityStatsMapper, org.mockito.Mockito.atLeastOnce())
                .countFinishedMatches(eq(USER_ID), eq(FACILITY_ID),
                        eq(TODAY.minusDays(29).atStartOfDay()), eq(TODAY.atTime(23, 59, 59)));
    }

    @Test
    @DisplayName("period=7 정규화 시 periodFrom/periodTo가 오늘-6일/오늘로 채워진다")
    void fillsPeriodRangeFieldsForDayPeriod() {
        stubOwnedFacilityWithNoMatches();

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "7", null, null);

        assertThat(stats.getPeriodFrom()).isEqualTo("2026-10-31");
        assertThat(stats.getPeriodTo()).isEqualTo("2026-11-06");
    }

    @Test
    @DisplayName("custom 기간이 유효하면 periodFrom/periodTo가 입력값 그대로 채워진다")
    void fillsPeriodRangeFieldsForCustomPeriod() {
        stubOwnedFacilityWithNoMatches();

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "custom", "2026-10-01", "2026-10-31");

        assertThat(stats.getPeriodFrom()).isEqualTo("2026-10-01");
        assertThat(stats.getPeriodTo()).isEqualTo("2026-10-31");
    }

    @Test
    @DisplayName("requestedPeriod는 기간 select 복원을 위해 요청값(custom 포함)을 그대로 담는다")
    void keepsRequestedPeriodForSelectRestoration() {
        stubOwnedFacilityWithNoMatches();

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
    @DisplayName("요일·시간대 지표는 매칭방 수가 아니라 확정 참가 인원으로 계산한다")
    void calculatesPercentAndHeightFromParticipantCounts() {
        stubOwnedFacility();
        when(facilityStatsMapper.countFinishedMatches(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(2L);
        when(facilityStatsMapper.countConfirmedParticipants(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(12L);
        when(facilityStatsMapper.countApplications(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(summary(4, 3, 1, 3));
        when(facilityStatsMapper.countParticipantsByWeekday(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(
                        participantWeekday(2, 6),   // 월
                        participantWeekday(4, 2),   // 수
                        participantWeekday(7, 0)));  // 토
        when(facilityStatsMapper.countParticipantsByHour(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(
                        participantHour(18, 4),
                        participantHour(20, 8),
                        participantHour(9, 2)));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        // 방은 2건뿐이지만 참가 인원은 12명이다.
        assertThat(stats.getBookingCount()).isEqualTo(2L);
        assertThat(stats.getParticipantCount()).isEqualTo(12L);

        assertThat(stats.getWeekdays()).extracting(FacilityStats.WeekdayShare::getLabel)
                .containsExactly("월", "화", "수", "목", "금", "토", "일");
        assertThat(stats.getWeekdays()).extracting(FacilityStats.WeekdayShare::getPercent)
                .containsExactly(100, 0, 33, 0, 0, 0, 0);
        assertThat(stats.getWeekdays().get(0).getTone()).isEqualTo("good");

        assertThat(stats.getPopularHours()).extracting(FacilityStats.PopularHour::getLabel)
                .containsExactly("09시", "18시", "20시");
        assertThat(stats.getPopularHours()).extracting(FacilityStats.PopularHour::getHeight)
                .containsExactly(25, 50, 100);
        assertThat(stats.getPopularHours()).extracting(FacilityStats.PopularHour::getCount)
                .containsExactly(2L, 4L, 8L);

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
        stubCounts(1, 0, summary(0, 0, 0, 0), List.of(participantWeekday(1, 0)), List.of());

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getPopularDay()).isNull();
        assertThat(stats.getPeakTime()).isNull();
        assertThat(stats.getPopularHours()).isEmpty();
        assertThat(stats.getWeekdays()).extracting(FacilityStats.WeekdayShare::getPercent)
                .containsExactly(0, 0, 0, 0, 0, 0, 0);
    }

    /* ---------------- 상세 집계 ---------------- */

    @Test
    @DisplayName("요일·시간대 상세는 집계되지 않은 구간도 0으로 채워 7행·24행을 유지한다")
    void fillsDetailRowsWithZerosWhenNothingCollected() {
        stubOwnedFacility();
        stubCounts(0, 0, summary(0, 0, 0, 0), List.of(), List.of());

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getWeekdayDetails()).hasSize(7);
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getLabel)
                .containsExactly("월", "화", "수", "목", "금", "토", "일");
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getCount)
                .containsOnly(0L);
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getShare)
                .containsOnly(0);

        assertThat(stats.getHourDetails()).hasSize(24);
        assertThat(stats.getHourDetails()).extracting(FacilityStats.HourDetail::getLabel)
                .first().isEqualTo("00시");
        assertThat(stats.getHourDetails()).extracting(FacilityStats.HourDetail::getLabel)
                .last().isEqualTo("23시");
        assertThat(stats.getHourDetails()).extracting(FacilityStats.HourDetail::getCount)
                .containsOnly(0L);
        assertThat(stats.getHourDetails()).extracting(FacilityStats.HourDetail::getShare)
                .containsOnly(0);

        assertThat(stats.getWeekdayHourDetails()).hasSize(7);
        assertThat(stats.getWeekdayHourDetails()).extracting(FacilityStats.WeekdayHourDetail::getLabel)
                .containsExactly("월", "화", "수", "목", "금", "토", "일");
        assertThat(stats.getWeekdayHourDetails())
                .allSatisfy(row -> assertThat(row.getHourlyCounts()).hasSize(24).containsOnly(0L));
    }

    @Test
    @DisplayName("요일·시간대 상세의 비율은 전체 확정 참가 인원 대비 반올림 백분율이다")
    void calculatesDetailShareAgainstParticipantTotal() {
        stubOwnedFacility();
        stubCounts(2, 10, summary(0, 0, 0, 0),
                List.of(participantWeekday(2, 6), participantWeekday(3, 4)),
                List.of(participantHour(9, 3), participantHour(20, 7)));
        when(facilityStatsMapper.countParticipantsByWeekdayAndHour(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(
                        participantWeekdayHour(2, 9, 4),
                        participantWeekdayHour(2, 20, 2),
                        participantWeekdayHour(3, 10, 4)));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        // 방 수(2)가 아니라 확정 참가 인원(10)이 분모다.
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getCount)
                .containsExactly(6L, 4L, 0L, 0L, 0L, 0L, 0L);
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getShare)
                .containsExactly(60, 40, 0, 0, 0, 0, 0);

        assertThat(stats.getHourDetails()).filteredOn(row -> row.getCount() > 0)
                .extracting(FacilityStats.HourDetail::getLabel, FacilityStats.HourDetail::getCount,
                        FacilityStats.HourDetail::getShare)
                .containsExactly(
                        tuple("09시", 3L, 30),
                        tuple("20시", 7L, 70));

        assertThat(stats.getWeekdayHourDetails().get(0).getHourlyCounts().get(9)).isEqualTo(4L);
        assertThat(stats.getWeekdayHourDetails().get(0).getHourlyCounts().get(20)).isEqualTo(2L);
        assertThat(stats.getWeekdayHourDetails().get(1).getHourlyCounts().get(10)).isEqualTo(4L);
        assertThat(stats.getWeekdayHourDetails().get(2).getHourlyCounts()).containsOnly(0L);
    }

    @Test
    @DisplayName("상세 비율은 전체 대비 반올림하며 전체가 0이면 0이다")
    void roundsDetailShareAndKeepsZeroWhenTotalIsZero() {
        stubOwnedFacility();
        stubCounts(3, 0, summary(0, 0, 0, 0), List.of(participantWeekday(2, 1), participantWeekday(3, 2)),
                List.of(participantHour(9, 1), participantHour(10, 2)));

        // 참가 인원 합계가 0이면 요일 값이 있어도 share는 0이다.
        FacilityStats zero = service.load(USER_ID, FACILITY_ID, "30", null, null);
        assertThat(zero.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getShare)
                .containsOnly(0);
        assertThat(zero.getHourDetails()).extracting(FacilityStats.HourDetail::getShare)
                .containsOnly(0);

        // 1/3 = 33.33 → 33, 2/3 = 66.67 → 67
        stubCounts(3, 3, summary(0, 0, 0, 0), List.of(participantWeekday(2, 1), participantWeekday(3, 2)),
                List.of(participantHour(9, 1), participantHour(10, 2)));
        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getShare)
                .containsExactly(33, 67, 0, 0, 0, 0, 0);
        assertThat(stats.getHourDetails()).filteredOn(row -> row.getCount() > 0)
                .extracting(FacilityStats.HourDetail::getShare)
                .containsExactly(33, 67);
    }

    @Test
    @DisplayName("요일×시간대 집계는 DAYOFWEEK를 월~일로 옮기고 시간대 24칸에 채운다")
    void mapsWeekdayHourCountsToWeekdayRows() {
        stubOwnedFacility();
        stubCounts(4, 0, summary(0, 0, 0, 0), List.of(), List.of());
        when(facilityStatsMapper.countParticipantsByWeekdayAndHour(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(
                        participantWeekdayHour(1, 23, 1),   // 일
                        participantWeekdayHour(7, 0, 2),    // 토
                        participantWeekdayHour(2, 6, 1),    // 월
                        participantWeekdayHour(9, 5, 99)));  // 범위 밖 요일은 버린다

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getWeekdayHourDetails().get(6).getHourlyCounts().get(23)).isEqualTo(1L);
        assertThat(stats.getWeekdayHourDetails().get(5).getHourlyCounts().get(0)).isEqualTo(2L);
        assertThat(stats.getWeekdayHourDetails().get(0).getHourlyCounts().get(6)).isEqualTo(1L);
        assertThat(stats.getWeekdayHourDetails()).allSatisfy(
                row -> assertThat(row.getHourlyCounts()).hasSize(24));
    }

    @Test
    @DisplayName("시설 미선택·미담당 빈 통계도 상세 집계 리스트를 0으로 채워 제공한다")
    void emptyStatsStillProvideZeroFilledDetailLists() {
        FacilityStats stats = service.load(USER_ID, null, "30", null, null);

        assertThat(stats.getWeekdayDetails()).hasSize(7);
        assertThat(stats.getHourDetails()).hasSize(24);
        assertThat(stats.getWeekdayHourDetails()).hasSize(7);
        assertThat(stats.getWeekdayHourDetails())
                .allSatisfy(row -> assertThat(row.getHourlyCounts()).hasSize(24).containsOnly(0L));
        verifyNoInteractions(facilityStatsMapper, facilityMapper);

        FacilityStats notManaged = FacilityStats.empty(30, "최근 30일");
        assertThat(notManaged.getWeekdayDetails()).hasSize(7);
        assertThat(notManaged.getHourDetails()).hasSize(24);
        assertThat(notManaged.getWeekdayHourDetails()).hasSize(7);
    }

    /* ---------------- 도우미 ---------------- */

    private void stubOwnedFacility() {
        when(facilityMapper.existsFacilityManager(FACILITY_ID, USER_ID)).thenReturn(true);
    }

    private void stubOwnedFacilityWithNoMatches() {
        stubOwnedFacility();
        stubCounts(0, 0, summary(0, 0, 0, 0), List.of(), List.of());
    }

    private void stubCounts(long matches, long participants, ApplicationCountSummary applications,
            List<WeekdayParticipantCount> weekdays, List<HourParticipantCount> hours) {
        // 기간 경계 자체는 별도 테스트에서 verify로 검증하므로 여기서는 구간과 무관하게 응답만 고정한다.
        when(facilityStatsMapper.countFinishedMatches(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(matches);
        when(facilityStatsMapper.countConfirmedParticipants(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(participants);
        when(facilityStatsMapper.countApplications(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(applications);
        when(facilityStatsMapper.countParticipantsByWeekday(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(weekdays);
        when(facilityStatsMapper.countParticipantsByHour(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(hours);
        // 요일×시간대 집계는 기본 응답(빈 목록)을 그대로 쓰고, 값이 필요한 테스트만 따로 stub한다.
    }

    private static ApplicationCountSummary summary(long total, long individual, long club, long approved) {
        ApplicationCountSummary summary = new ApplicationCountSummary();
        summary.setTotal(total);
        summary.setIndividualCount(individual);
        summary.setClubCount(club);
        summary.setApprovedCount(approved);
        return summary;
    }

    private static WeekdayParticipantCount participantWeekday(int dayOfWeek, long participantCount) {
        WeekdayParticipantCount row = new WeekdayParticipantCount();
        row.setWeekday(dayOfWeek);
        row.setParticipantCount(participantCount);
        return row;
    }

    private static HourParticipantCount participantHour(int hour, long participantCount) {
        HourParticipantCount row = new HourParticipantCount();
        row.setHour(hour);
        row.setParticipantCount(participantCount);
        return row;
    }

    private static WeekdayHourParticipantCount participantWeekdayHour(int dayOfWeek, int hour,
            long participantCount) {
        WeekdayHourParticipantCount row = new WeekdayHourParticipantCount();
        row.setWeekday(dayOfWeek);
        row.setHour(hour);
        row.setParticipantCount(participantCount);
        return row;
    }
}
