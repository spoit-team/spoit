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
import co.kr.spoit.facility.dto.WeekdayHourApplicationStatusCount;
import co.kr.spoit.facility.dto.WeekdayHourParticipantCount;
import co.kr.spoit.facility.mapper.FacilityMapper;
import co.kr.spoit.facility.mapper.FacilityStatsMapper;

/**
 * 예약 통계 서비스의 기간 정규화·권한 범위·참가 인원 기준 백분율 계산을 검증한다.
 *
 * <p>{@code bookingCount}는 종료 매칭방 수, 요일·시간대 {@code count}는 확정 참가 인원,
 * 상세 {@code share}는 전체 확정 참가 인원({@code participantCount}) 대비 반올림 백분율이다.
 * 요일별·시간대별 집계는 요일×시간대 집계 한 번에서 파생한다.</p>
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
        assertThat(stats.getWeekdayDetails()).hasSize(7);
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

    @Test
    @DisplayName("관리자·시설 ID가 0이나 음수면 매퍼를 호출하지 않고 기간만 정규화한 빈 통계를 반환한다")
    void returnsNormalizedEmptyStatsForNonPositiveIds() {
        for (Long managerUserId : List.of(0L, -1L)) {
            for (Long facilityId : List.of(0L, -1L)) {
                FacilityStats stats = service.load(managerUserId, facilityId, "90", null, null);

                // ID가 양수가 아니면 실제 조회 구간(periodFrom/periodTo)은 채우지 않는다.
                assertThat(stats.getPeriod()).as("manager=%s facility=%s", managerUserId, facilityId)
                        .isEqualTo(90);
                assertThat(stats.getPeriodLabel()).isEqualTo("최근 90일");
                assertThat(stats.getRequestedPeriod()).isEqualTo("90");
                assertThat(stats.getPeriodFrom()).isNull();
                assertThat(stats.getPeriodTo()).isNull();
                assertThat(stats.getBookingCount()).isZero();
                assertThat(stats.getParticipantCount()).isZero();
                assertThat(stats.getApplicationTotal()).isZero();
                assertThat(stats.getPopularDay()).isNull();
                assertThat(stats.getPeakTime()).isNull();
                // 조회하지 않아도 화면 모양은 0으로 채운 목록과 상한 1로 유지된다.
                assertThat(stats.getWeekdayDetails()).hasSize(7);
                assertThat(stats.getHourDetails()).hasSize(24);
                assertThat(stats.getWeekdayHourDetails()).hasSize(7);
                assertThat(stats.getApplicationWeekdayDetails()).hasSize(7);
                assertThat(stats.getApplicationHourDetails()).hasSize(24);
                assertThat(stats.getApplicationWeekdayHourDetails()).hasSize(7);
                assertThat(stats.getWeekdayChartMax()).isEqualTo(1L);
                assertThat(stats.getHourChartMax()).isEqualTo(1L);
                assertThat(stats.getWeekdayHourChartMax()).isEqualTo(1L);
            }
        }

        verifyNoInteractions(facilityStatsMapper, facilityMapper);
    }

    @Test
    @DisplayName("매퍼가 null·음수 집계를 돌려주면 0으로 정규화하고 차트 목록은 0으로 채운다")
    void normalizesNullAndNegativeAggregates() {
        stubOwnedFacility();
        // 스칼라 집계는 음수, 신청 요약과 요일×시간대 목록은 null로 응답한다.
        stubCounts(-3, -7, null);
        when(facilityStatsMapper.countParticipantsByWeekdayAndHour(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(null);
        when(facilityStatsMapper.countApplicationStatusesByWeekdayAndHour(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(null);

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        // 음수·null 응답을 0으로 올려 화면에 음수 지표가 나가지 않게 한다.
        assertThat(stats.getBookingCount()).isZero();
        assertThat(stats.getParticipantCount()).isZero();
        assertThat(stats.getApplicationTotal()).isZero();
        assertThat(stats.getIndividualCount()).isZero();
        assertThat(stats.getClubCount()).isZero();
        assertThat(stats.getApprovedCount()).isZero();
        assertThat(stats.getCancelledCount()).isZero();
        assertThat(stats.getResolvedCount()).isZero();
        assertThat(stats.getApprovalRate()).isZero();
        assertThat(stats.getCancelRate()).isZero();
        assertThat(stats.getPopularDay()).isNull();
        assertThat(stats.getPeakTime()).isNull();

        // 참가 인원 상세는 7/24/7×24를 0으로 채운다.
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getCount)
                .containsOnly(0L);
        assertThat(stats.getHourDetails()).extracting(FacilityStats.HourDetail::getCount)
                .containsOnly(0L);
        assertThat(stats.getWeekdayHourDetails())
                .allSatisfy(row -> assertThat(row.getHourlyCounts()).hasSize(24).containsOnly(0L));

        // 신청 분포도 7/24/7×24를 0으로 채우고 차트 상한은 최소 1이다.
        assertThat(stats.getApplicationWeekdayDetails())
                .extracting(FacilityStats.ApplicationDistributionDetail::getTotalCount)
                .containsOnly(0L);
        assertThat(stats.getApplicationHourDetails())
                .extracting(FacilityStats.ApplicationDistributionDetail::getTotalCount)
                .containsOnly(0L);
        assertThat(stats.getApplicationWeekdayHourDetails())
                .allSatisfy(day -> assertThat(day.getHours()).hasSize(24)
                        .allSatisfy(hour -> {
                            assertThat(hour.getTotalCount()).isZero();
                            assertThat(hour.getApprovedCount()).isZero();
                            assertThat(hour.getCancelledCount()).isZero();
                        }));
        assertThat(stats.getWeekdayChartMax()).isEqualTo(1L);
        assertThat(stats.getHourChartMax()).isEqualTo(1L);
        assertThat(stats.getWeekdayHourChartMax()).isEqualTo(1L);
        assertAllTotalCountsEqualResolved(stats);

        // 요약 필드와 집계 행 자체가 음수여도 0으로 올리고, 알 수 없는 요일·시간대 행은 버린다.
        stubCounts(0, 0, summary(-5, -2, -1, -4, -3));
        stubWeekdayHourCounts(
                participantWeekdayHour(2, 9, -4),
                participantWeekdayHour(9, 5, 7));
        stubApplicationStatuses(
                applicationStatus(2, 9, -2, -3, -5),
                applicationStatus(3, 40, 6, 6, 12));

        FacilityStats negatives = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(negatives.getApplicationTotal()).isZero();
        assertThat(negatives.getIndividualCount()).isZero();
        assertThat(negatives.getClubCount()).isZero();
        assertThat(negatives.getApprovedCount()).isZero();
        assertThat(negatives.getCancelledCount()).isZero();
        assertThat(negatives.getResolvedCount()).isZero();
        assertThat(negatives.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getCount)
                .containsOnly(0L);
        assertThat(negatives.getHourDetails()).extracting(FacilityStats.HourDetail::getCount)
                .containsOnly(0L);
        assertThat(negatives.getWeekdayHourDetails())
                .allSatisfy(row -> assertThat(row.getHourlyCounts()).hasSize(24).containsOnly(0L));
        // 음수·범위 밖 신청 행이 모두 걸러져 분포도 0이고 상한은 최소값을 지킨다.
        assertThat(negatives.getApplicationWeekdayDetails())
                .extracting(FacilityStats.ApplicationDistributionDetail::getTotalCount)
                .containsOnly(0L);
        assertThat(negatives.getApplicationHourDetails())
                .extracting(FacilityStats.ApplicationDistributionDetail::getTotalCount)
                .containsOnly(0L);
        assertThat(negatives.getApplicationWeekdayHourDetails())
                .allSatisfy(day -> assertThat(day.getHours()).hasSize(24)
                        .allSatisfy(hour -> assertThat(hour.getTotalCount()).isZero()));
        assertThat(negatives.getWeekdayChartMax()).isEqualTo(1L);
        assertThat(negatives.getHourChartMax()).isEqualTo(1L);
        assertThat(negatives.getWeekdayHourChartMax()).isEqualTo(1L);
        assertAllTotalCountsEqualResolved(negatives);
    }

    /* ---------------- 계산 ---------------- */

    @Test
    @DisplayName("요일·시간대 지표는 매칭방 수가 아니라 확정 참가 인원으로 계산한다")
    void calculatesCountsFromDerivedParticipantTotals() {
        stubOwnedFacility();
        when(facilityStatsMapper.countFinishedMatches(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(2L);
        when(facilityStatsMapper.countConfirmedParticipants(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(12L);
        when(facilityStatsMapper.countApplications(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(summary(4, 3, 1, 3));
        // 요일×시간대 집계 하나에서 요일 합계(월 6·수 2·토 0)와 시간대 합계(09시 2·18시 4·20시 8)를 파생한다.
        stubWeekdayHourCounts(
                participantWeekdayHour(2, 9, 2),
                participantWeekdayHour(2, 18, 1),
                participantWeekdayHour(2, 20, 3),
                participantWeekdayHour(4, 18, 1),
                participantWeekdayHour(4, 20, 1),
                participantWeekdayHour(7, 20, 4),
                participantWeekdayHour(7, 9, 0));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        // 방은 2건뿐이지만 참가 인원은 12명이다.
        assertThat(stats.getBookingCount()).isEqualTo(2L);
        assertThat(stats.getParticipantCount()).isEqualTo(12L);

        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getLabel)
                .containsExactly("월", "화", "수", "목", "금", "토", "일");
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getCount)
                .containsExactly(6L, 0L, 2L, 0L, 0L, 4L, 0L);
        assertThat(stats.getHourDetails()).filteredOn(row -> row.getCount() > 0)
                .extracting(FacilityStats.HourDetail::getLabel, FacilityStats.HourDetail::getCount)
                .containsExactly(tuple("09시", 2L), tuple("18시", 2L), tuple("20시", 8L));

        assertThat(stats.getPopularDay()).isEqualTo("월");
        assertThat(stats.getPeakTime()).isEqualTo("20시");
    }

    @Test
    @DisplayName("신청 총계가 0이면 승인율은 0이고 신청 지표도 0이다")
    void approvalRateIsZeroWithoutApplications() {
        stubOwnedFacility();
        stubCounts(0, 0, summary(0, 0, 0, 0));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getApplicationTotal()).isZero();
        assertThat(stats.getApprovalRate()).isZero();
        assertThat(stats.getPopularDay()).isNull();
        assertThat(stats.getPeakTime()).isNull();
        assertThat(stats.getHourDetails()).extracting(FacilityStats.HourDetail::getCount)
                .containsOnly(0L);
    }

    @Test
    @DisplayName("승인율은 확정/처리 수(확정+반려+취소) 백분율을 반올림하고 신청 구분을 그대로 담는다")
    void calculatesApprovalRateWithRounding() {
        stubOwnedFacility();
        stubCounts(0, 0, summary(3, 2, 1, 2, 1));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getApplicationTotal()).isEqualTo(3L);
        assertThat(stats.getIndividualCount()).isEqualTo(2L);
        assertThat(stats.getClubCount()).isEqualTo(1L);
        assertThat(stats.getApprovedCount()).isEqualTo(2L);
        assertThat(stats.getCancelledCount()).isEqualTo(1L);
        assertThat(stats.getResolvedCount()).isEqualTo(3L);
        // 확정 2 / 처리 3 = 66.67 → 67, (반려+취소) 1 / 처리 3 = 33.33 → 33
        assertThat(stats.getApprovalRate()).isEqualTo(67.0);
        assertThat(stats.getCancelRate()).isEqualTo(33.0);
    }

    @Test
    @DisplayName("대기는 신청 총계에는 들어가지만 승인율·취소율 분모(처리 수)에서는 빠진다")
    void waitingCountIsInTotalButNotInResolvedDenominator() {
        stubOwnedFacility();
        // 최신 신청 5건: 확정 2, 반려+취소 1, 대기 2
        stubCounts(0, 0, summary(5, 3, 2, 2, 1));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getApplicationTotal()).isEqualTo(5L);
        assertThat(stats.getResolvedCount()).isEqualTo(3L);
        assertThat(stats.getApprovalRate()).isEqualTo(67.0);
        assertThat(stats.getCancelRate()).isEqualTo(33.0);
    }

    @Test
    @DisplayName("처리 수가 0이면(대기만 있으면) 승인율·취소율은 0이다")
    void ratesAreZeroWhenOnlyWaiting() {
        stubOwnedFacility();
        stubCounts(0, 0, summary(4, 4, 0, 0, 0));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getApplicationTotal()).isEqualTo(4L);
        assertThat(stats.getResolvedCount()).isZero();
        assertThat(stats.getApprovalRate()).isZero();
        assertThat(stats.getCancelRate()).isZero();
    }

    @Test
    @DisplayName("요일·시간대 집계가 0건뿐이면 인기 요일·집중 시간 라벨은 null이고 상세도 0이다")
    void popularLabelsAreNullWhenNothingCollected() {
        stubOwnedFacility();
        stubCounts(1, 0, summary(0, 0, 0, 0));
        stubWeekdayHourCounts(participantWeekdayHour(1, 0, 0));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getPopularDay()).isNull();
        assertThat(stats.getPeakTime()).isNull();
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getCount)
                .containsOnly(0L);
        assertThat(stats.getHourDetails()).extracting(FacilityStats.HourDetail::getCount)
                .containsOnly(0L);
    }

    @Test
    @DisplayName("요일·시간대 동률이면 월~일·00~23시 순서상 먼저인 라벨을 고른다")
    void picksFirstLabelOnTie() {
        stubOwnedFacility();
        stubCounts(2, 4, summary(0, 0, 0, 0));
        // 월·화가 2명으로 동률이고 09시·20시도 2명으로 동률이라, 먼저 나오는 월·09시를 고른다.
        stubWeekdayHourCounts(
                participantWeekdayHour(2, 20, 1),
                participantWeekdayHour(3, 9, 1),
                participantWeekdayHour(2, 9, 1),
                participantWeekdayHour(3, 20, 1));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getPopularDay()).isEqualTo("월");
        assertThat(stats.getPeakTime()).isEqualTo("09시");
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getCount)
                .containsExactly(2L, 2L, 0L, 0L, 0L, 0L, 0L);
        assertThat(stats.getHourDetails()).filteredOn(row -> row.getCount() > 0)
                .extracting(FacilityStats.HourDetail::getLabel, FacilityStats.HourDetail::getCount)
                .containsExactly(tuple("09시", 2L), tuple("20시", 2L));
    }

    @Test
    @DisplayName("한 시간대에 여러 요일이 몰려도 요일 합계와 시간대 합계를 각각 파생한다")
    void derivesBothTotalsFromSameHourAcrossWeekdays() {
        stubOwnedFacility();
        stubCounts(3, 5, summary(0, 0, 0, 0));
        // 같은 19시에 월 2명·수 3명이 시작한다.
        stubWeekdayHourCounts(
                participantWeekdayHour(2, 19, 2),
                participantWeekdayHour(4, 19, 3));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getCount)
                .containsExactly(2L, 0L, 3L, 0L, 0L, 0L, 0L);
        assertThat(stats.getHourDetails()).filteredOn(row -> row.getCount() > 0)
                .extracting(FacilityStats.HourDetail::getLabel, FacilityStats.HourDetail::getCount)
                .containsExactly(tuple("19시", 5L));
        assertThat(stats.getPeakTime()).isEqualTo("19시");
    }

    /* ---------------- 상세 집계 ---------------- */

    @Test
    @DisplayName("요일·시간대 상세는 집계되지 않은 구간도 0으로 채워 7행·24행을 유지한다")
    void fillsDetailRowsWithZerosWhenNothingCollected() {
        stubOwnedFacility();
        stubCounts(0, 0, summary(0, 0, 0, 0));

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
        stubCounts(2, 10, summary(0, 0, 0, 0));
        stubWeekdayHourCounts(
                participantWeekdayHour(2, 9, 4),
                participantWeekdayHour(2, 20, 2),
                participantWeekdayHour(3, 10, 4));

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
                        tuple("09시", 4L, 40),
                        tuple("10시", 4L, 40),
                        tuple("20시", 2L, 20));

        assertThat(stats.getWeekdayHourDetails().get(0).getHourlyCounts().get(9)).isEqualTo(4L);
        assertThat(stats.getWeekdayHourDetails().get(0).getHourlyCounts().get(20)).isEqualTo(2L);
        assertThat(stats.getWeekdayHourDetails().get(1).getHourlyCounts().get(10)).isEqualTo(4L);
        assertThat(stats.getWeekdayHourDetails().get(2).getHourlyCounts()).containsOnly(0L);
    }

    @Test
    @DisplayName("상세 비율은 전체 대비 반올림하며 전체가 0이면 0이고, 집계 행 합과 달라도 전체를 분모로 쓴다")
    void roundsDetailShareAndKeepsZeroWhenTotalIsZero() {
        stubOwnedFacility();
        stubCounts(3, 0, summary(0, 0, 0, 0));
        stubWeekdayHourCounts(
                participantWeekdayHour(2, 9, 1),
                participantWeekdayHour(3, 10, 2));

        // 참가 인원 합계(countConfirmedParticipants)가 0이면 행 합계가 있어도 share는 0이다.
        FacilityStats zero = service.load(USER_ID, FACILITY_ID, "30", null, null);
        assertThat(zero.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getShare)
                .containsOnly(0);
        assertThat(zero.getHourDetails()).extracting(FacilityStats.HourDetail::getShare)
                .containsOnly(0);

        // 1/3 = 33.33 → 33, 2/3 = 66.67 → 67
        stubCounts(3, 3, summary(0, 0, 0, 0));
        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getShare)
                .containsExactly(33, 67, 0, 0, 0, 0, 0);
        assertThat(stats.getHourDetails()).filteredOn(row -> row.getCount() > 0)
                .extracting(FacilityStats.HourDetail::getShare)
                .containsExactly(33, 67);

        // 집계 행 합(3)과 참가 인원 쿼리 값(10)이 달라도 비율 분모는 쿼리 값이다.
        stubCounts(3, 10, summary(0, 0, 0, 0));
        FacilityStats mismatched = service.load(USER_ID, FACILITY_ID, "30", null, null);
        assertThat(mismatched.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getShare)
                .containsExactly(10, 20, 0, 0, 0, 0, 0);
        assertThat(mismatched.getHourDetails()).filteredOn(row -> row.getCount() > 0)
                .extracting(FacilityStats.HourDetail::getCount, FacilityStats.HourDetail::getShare)
                .containsExactly(tuple(1L, 10), tuple(2L, 20));
    }

    @Test
    @DisplayName("요일×시간대 집계는 DAYOFWEEK를 월~일로 옮기고 시간대 24칸에 채운다")
    void mapsWeekdayHourCountsToWeekdayRows() {
        stubOwnedFacility();
        stubCounts(4, 0, summary(0, 0, 0, 0));
        stubWeekdayHourCounts(
                participantWeekdayHour(1, 23, 1),   // 일
                participantWeekdayHour(7, 0, 2),    // 토
                participantWeekdayHour(2, 6, 1),    // 월
                participantWeekdayHour(9, 5, 99));  // 범위 밖 요일은 버린다

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getWeekdayHourDetails().get(6).getHourlyCounts().get(23)).isEqualTo(1L);
        assertThat(stats.getWeekdayHourDetails().get(5).getHourlyCounts().get(0)).isEqualTo(2L);
        assertThat(stats.getWeekdayHourDetails().get(0).getHourlyCounts().get(6)).isEqualTo(1L);
        assertThat(stats.getWeekdayHourDetails()).allSatisfy(
                row -> assertThat(row.getHourlyCounts()).hasSize(24));
        // 버려진 요일 행은 파생 합계에도 들어가지 않는다.
        assertThat(stats.getWeekdayDetails()).extracting(FacilityStats.WeekdayDetail::getCount)
                .containsExactly(1L, 0L, 0L, 0L, 0L, 2L, 1L);
        assertThat(stats.getHourDetails()).filteredOn(row -> row.getCount() > 0)
                .extracting(FacilityStats.HourDetail::getLabel, FacilityStats.HourDetail::getCount)
                .containsExactly(tuple("00시", 2L), tuple("06시", 1L), tuple("23시", 1L));
    }

    @Test
    @DisplayName("시설 미선택·미담당 빈 통계도 상세 집계 리스트를 0으로 채워 제공한다")
    void emptyStatsStillProvideZeroFilledDetailLists() {
        FacilityStats stats = service.load(USER_ID, null, "30", null, null);

        // 기간 표시값은 요청값 그대로이고 지표·인기 라벨·조회 날짜는 0·null이다.
        assertThat(stats.getPeriod()).isEqualTo(30);
        assertThat(stats.getPeriodLabel()).isEqualTo("최근 30일");
        assertThat(stats.getRequestedPeriod()).isEqualTo("30");
        assertThat(stats.getPeriodFrom()).isNull();
        assertThat(stats.getPeriodTo()).isNull();
        assertThat(stats.getBookingCount()).isZero();
        assertThat(stats.getParticipantCount()).isZero();
        assertThat(stats.getPopularDay()).isNull();
        assertThat(stats.getPeakTime()).isNull();
        assertThat(stats.getApplicationTotal()).isZero();
        assertThat(stats.getApprovalRate()).isZero();

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
        assertThat(stats.getWeekdayHourDetails())
                .extracting(FacilityStats.WeekdayHourDetail::getLabel)
                .containsExactly("월", "화", "수", "목", "금", "토", "일");
        assertThat(stats.getWeekdayHourDetails())
                .allSatisfy(row -> assertThat(row.getHourlyCounts()).hasSize(24).containsOnly(0L));

        // 신청 분포도 7/24/7×24 목록을 0으로 채우고 차트 상한은 최소 1이다.
        assertThat(stats.getCancelledCount()).isZero();
        assertThat(stats.getResolvedCount()).isZero();
        assertThat(stats.getCancelRate()).isZero();
        assertThat(stats.getApplicationWeekdayDetails()).hasSize(7);
        assertThat(stats.getApplicationWeekdayDetails())
                .extracting(FacilityStats.ApplicationDistributionDetail::getLabel)
                .containsExactly("월", "화", "수", "목", "금", "토", "일");
        assertThat(stats.getApplicationWeekdayDetails())
                .allSatisfy(row -> {
                    assertThat(row.getApprovedCount()).isZero();
                    assertThat(row.getCancelledCount()).isZero();
                    assertThat(row.getTotalCount()).isZero();
                });
        assertThat(stats.getApplicationHourDetails()).hasSize(24);
        assertThat(stats.getApplicationHourDetails())
                .extracting(FacilityStats.ApplicationDistributionDetail::getLabel)
                .first().isEqualTo("00시");
        assertThat(stats.getApplicationHourDetails())
                .extracting(FacilityStats.ApplicationDistributionDetail::getLabel)
                .last().isEqualTo("23시");
        assertThat(stats.getApplicationWeekdayHourDetails()).hasSize(7);
        assertThat(stats.getApplicationWeekdayHourDetails())
                .allSatisfy(row -> assertThat(row.getHours()).hasSize(24)
                        .allSatisfy(hour -> {
                            assertThat(hour.getApprovedCount()).isZero();
                            assertThat(hour.getCancelledCount()).isZero();
                            assertThat(hour.getTotalCount()).isZero();
                        }));
        assertThat(stats.getWeekdayChartMax()).isEqualTo(1L);
        assertThat(stats.getHourChartMax()).isEqualTo(1L);
        assertThat(stats.getWeekdayHourChartMax()).isEqualTo(1L);
        assertAllTotalCountsEqualResolved(stats);
        verifyNoInteractions(facilityStatsMapper, facilityMapper);
    }

    /* ---------------- 최신 신청 분포 차트 ---------------- */

    @Test
    @DisplayName("최신 신청 분포는 요일·시간대·요일×시간대 목록을 7/24/7×24로 채운다")
    void buildsApplicationDistributionLists() {
        stubOwnedFacility();
        // 최신 신청 5건: 확정 2, 반려+취소 1, 대기 2. 신청 총계는 대기를 포함한다.
        stubCounts(0, 0, summary(5, 3, 2, 2, 1));
        stubApplicationStatuses(
                applicationStatus(2, 9, 1, 0, 1),   // 월 09시: 확정 1
                applicationStatus(2, 9, 1, 1, 2),   // 같은 월 09시 행: 확정 1 + 반려 1 (합산)
                applicationStatus(3, 20, 0, 0, 1),  // 화 20시: 대기 1뿐 → 차트에서는 0
                applicationStatus(4, 18, 0, 0, 1)); // 수 18시: 대기 1뿐 → 차트에서는 0

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        // 신청 총계는 대기를 포함한 5건이다.
        assertThat(stats.getApplicationTotal()).isEqualTo(5L);
        assertThat(stats.getResolvedCount()).isEqualTo(3L);

        assertThat(stats.getApplicationWeekdayDetails()).hasSize(7);
        assertThat(stats.getApplicationWeekdayDetails())
                .extracting(FacilityStats.ApplicationDistributionDetail::getLabel)
                .containsExactly("월", "화", "수", "목", "금", "토", "일");
        // 수요일은 대기만 있어 차트 막대(처리 수)가 0이다.
        assertThat(stats.getApplicationWeekdayDetails())
                .extracting(FacilityStats.ApplicationDistributionDetail::getApprovedCount,
                        FacilityStats.ApplicationDistributionDetail::getCancelledCount,
                        FacilityStats.ApplicationDistributionDetail::getTotalCount)
                .containsExactly(
                        tuple(2L, 1L, 3L),
                        tuple(0L, 0L, 0L),
                        tuple(0L, 0L, 0L),
                        tuple(0L, 0L, 0L),
                        tuple(0L, 0L, 0L),
                        tuple(0L, 0L, 0L),
                        tuple(0L, 0L, 0L));

        assertThat(stats.getApplicationHourDetails()).hasSize(24);
        // 대기만 있는 18시는 차트에서 빠져 0으로 남는다.
        assertThat(stats.getApplicationHourDetails()).filteredOn(row -> row.getTotalCount() > 0)
                .extracting(FacilityStats.ApplicationDistributionDetail::getLabel,
                        FacilityStats.ApplicationDistributionDetail::getApprovedCount,
                        FacilityStats.ApplicationDistributionDetail::getCancelledCount,
                        FacilityStats.ApplicationDistributionDetail::getTotalCount)
                .containsExactly(tuple("09시", 2L, 1L, 3L));
        assertThat(stats.getApplicationHourDetails().get(18).getTotalCount()).isZero();

        assertThat(stats.getApplicationWeekdayHourDetails()).hasSize(7);
        List<FacilityStats.ApplicationDistributionDetail> mondayHours =
                stats.getApplicationWeekdayHourDetails().get(0).getHours();
        assertThat(mondayHours).hasSize(24);
        assertThat(mondayHours.get(0).getLabel()).isEqualTo("00시");
        assertThat(mondayHours.get(23).getLabel()).isEqualTo("23시");
        assertThat(mondayHours.get(9).getApprovedCount()).isEqualTo(2L);
        assertThat(mondayHours.get(9).getCancelledCount()).isEqualTo(1L);
        assertThat(mondayHours.get(9).getTotalCount()).isEqualTo(3L);
        // 대기만 있는 수 18시 칸은 처리 수 0이다.
        assertThat(stats.getApplicationWeekdayHourDetails().get(2).getHours().get(18).getTotalCount())
                .isZero();
        // 모든 막대는 처리 수(확정+반려+취소)이며 대기를 포함하지 않는다.
        assertAllTotalCountsEqualResolved(stats);
        // 한 번의 집계에서 요일별·시간대별·요일×시간대별을 모두 파생한다.
        verify(facilityStatsMapper, org.mockito.Mockito.times(1))
                .countApplicationStatusesByWeekdayAndHour(eq(USER_ID), eq(FACILITY_ID),
                        any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("차트 상한은 처리 건수(확정+반려+취소) 최대값이고 대기는 제외하며 최소 1이다")
    void chartMaxUsesResolvedCountsAndMinimumOne() {
        stubOwnedFacility();
        // 최신 신청 7건: 확정 3, 반려+취소 1, 대기 3. 개인 4·동호회 3.
        stubCounts(0, 0, summary(7, 4, 3, 3, 1));
        // 월 09시: 확정 1 + 반려 1 = 처리 2, 대기 1은 제외된다.
        // 화 20시: 대기 2뿐이므로 상한에도 막대에도 들어가지 않는다.
        stubApplicationStatuses(
                applicationStatus(2, 9, 1, 1, 3),
                applicationStatus(3, 20, 0, 0, 2),
                applicationStatus(4, 10, 2, 0, 2));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        // 요일별 처리 건수 최대: 월 2 (화 0, 수 2 → 동률이지만 상한 값은 2)
        assertThat(stats.getWeekdayChartMax()).isEqualTo(2L);
        // 시간대별 처리 건수 최대: 09시 2, 10시 2 → 2
        assertThat(stats.getHourChartMax()).isEqualTo(2L);
        // 한 칸 최대: 월 09시 2, 수 10시 2 → 2
        assertThat(stats.getWeekdayHourChartMax()).isEqualTo(2L);
        // 대기가 섞인 칸(월 09시 전체 3, 화 20시 전체 2)도 차트 total은 처리 수만 센다.
        assertThat(stats.getApplicationWeekdayDetails().get(0).getTotalCount()).isEqualTo(2L);
        assertThat(stats.getApplicationHourDetails().get(9).getTotalCount()).isEqualTo(2L);
        assertThat(stats.getApplicationHourDetails().get(20).getTotalCount()).isZero();
        assertThat(stats.getApplicationWeekdayHourDetails().get(0).getHours().get(9).getTotalCount())
                .isEqualTo(2L);
        assertAllTotalCountsEqualResolved(stats);
    }

    @Test
    @DisplayName("처리된 신청이 없으면 차트 상한은 0이 아니라 1이고 막대 처리 수도 0이다")
    void chartMaxIsAtLeastOneWhenNothingResolved() {
        stubOwnedFacility();
        // 최신 신청 3건이 모두 대기다. 신청 총계에는 들어가지만 차트에는 들어가지 않는다.
        stubCounts(0, 0, summary(3, 3, 0, 0, 0));
        stubApplicationStatuses(applicationStatus(2, 9, 0, 0, 3));

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getApplicationTotal()).isEqualTo(3L);
        assertThat(stats.getResolvedCount()).isZero();
        assertThat(stats.getWeekdayChartMax()).isEqualTo(1L);
        assertThat(stats.getHourChartMax()).isEqualTo(1L);
        assertThat(stats.getWeekdayHourChartMax()).isEqualTo(1L);
        // 대기만 있는 칸은 차트 막대 처리 수가 0이다.
        assertThat(stats.getApplicationWeekdayDetails().get(0).getTotalCount()).isZero();
        assertThat(stats.getApplicationHourDetails().get(9).getTotalCount()).isZero();
        assertThat(stats.getApplicationWeekdayHourDetails().get(0).getHours().get(9).getTotalCount())
                .isZero();
        assertAllTotalCountsEqualResolved(stats);
    }

    @Test
    @DisplayName("신청 분포 집계의 알 수 없는 요일·시간대 행은 버리고 같은 칸은 합산한다")
    void dropsUnknownApplicationStatusRowsAndSumsDuplicates() {
        stubOwnedFacility();
        // 범위 밖 행(확정 3·반려 3)은 버려지므로 확정 1·반려+취소 1만 남고, 대기 5는 차트에서 빠진다.
        stubCounts(0, 0, summary(7, 4, 3, 1, 1));
        stubApplicationStatuses(
                applicationStatus(9, 5, 3, 3, 6),   // 범위 밖 요일은 버린다
                applicationStatus(2, 30, 3, 3, 6),  // 범위 밖 시간대는 버린다
                applicationStatus(1, 23, 1, 0, 1),  // 일 23시
                applicationStatus(1, 23, 0, 1, 2),  // 일 23시 반려 추가 (같은 칸 합산)
                applicationStatus(1, 23, 0, 0, 4)); // 일 23시 대기 4건은 합산돼도 차트에서 빠진다

        FacilityStats stats = service.load(USER_ID, FACILITY_ID, "30", null, null);

        assertThat(stats.getApplicationWeekdayDetails().get(6).getApprovedCount()).isEqualTo(1L);
        assertThat(stats.getApplicationWeekdayDetails().get(6).getCancelledCount()).isEqualTo(1L);
        assertThat(stats.getApplicationWeekdayDetails().get(6).getTotalCount()).isEqualTo(2L);
        assertThat(stats.getApplicationHourDetails().get(23).getTotalCount()).isEqualTo(2L);
        assertThat(stats.getApplicationWeekdayHourDetails().get(6).getHours().get(23)
                .getTotalCount()).isEqualTo(2L);
        assertThat(stats.getWeekdayChartMax()).isEqualTo(2L);
        assertAllTotalCountsEqualResolved(stats);
    }

    /* ---------------- 도우미 ---------------- */

    /**
     * 차트 계약 불변식: 요일·시간대·요일×시간대의 모든 막대는
     * {@code totalCount == approvedCount + cancelledCount}(대기 제외)다.
     */
    private static void assertAllTotalCountsEqualResolved(FacilityStats stats) {
        assertThat(stats.getApplicationWeekdayDetails()).allSatisfy(row -> {
            assertThat(row.getTotalCount())
                    .isEqualTo(row.getApprovedCount() + row.getCancelledCount());
            assertThat(row.getTotalCount()).isNotNegative();
        });
        assertThat(stats.getApplicationHourDetails()).allSatisfy(row -> {
            assertThat(row.getTotalCount())
                    .isEqualTo(row.getApprovedCount() + row.getCancelledCount());
            assertThat(row.getTotalCount()).isNotNegative();
        });
        assertThat(stats.getApplicationWeekdayHourDetails()).allSatisfy(day ->
                assertThat(day.getHours()).allSatisfy(hour -> {
                    assertThat(hour.getTotalCount())
                            .isEqualTo(hour.getApprovedCount() + hour.getCancelledCount());
                    assertThat(hour.getTotalCount()).isNotNegative();
                }));
    }

    private void stubOwnedFacility() {
        when(facilityMapper.existsFacilityManager(FACILITY_ID, USER_ID)).thenReturn(true);
    }

    private void stubOwnedFacilityWithNoMatches() {
        stubOwnedFacility();
        stubCounts(0, 0, summary(0, 0, 0, 0));
    }

    private void stubCounts(long matches, long participants, ApplicationCountSummary applications) {
        // 기간 경계 자체는 별도 테스트에서 verify로 검증하므로 여기서는 구간과 무관하게 응답만 고정한다.
        when(facilityStatsMapper.countFinishedMatches(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(matches);
        when(facilityStatsMapper.countConfirmedParticipants(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(participants);
        when(facilityStatsMapper.countApplications(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(applications);
        // 요일×시간대 집계는 기본 응답(빈 목록)을 그대로 쓰고, 값이 필요한 테스트만 따로 stub한다.
    }

    private void stubWeekdayHourCounts(WeekdayHourParticipantCount... rows) {
        when(facilityStatsMapper.countParticipantsByWeekdayAndHour(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(rows));
    }

    private void stubApplicationStatuses(WeekdayHourApplicationStatusCount... rows) {
        when(facilityStatsMapper.countApplicationStatusesByWeekdayAndHour(eq(USER_ID), eq(FACILITY_ID),
                any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of(rows));
    }

    private static WeekdayHourApplicationStatusCount applicationStatus(int dayOfWeek, int hour,
            long approvedCount, long cancelledCount, long totalCount) {
        WeekdayHourApplicationStatusCount row = new WeekdayHourApplicationStatusCount();
        row.setWeekday(dayOfWeek);
        row.setHour(hour);
        row.setApprovedCount(approvedCount);
        row.setCancelledCount(cancelledCount);
        row.setTotalCount(totalCount);
        return row;
    }

    private static ApplicationCountSummary summary(long total, long individual, long club, long approved) {
        return summary(total, individual, club, approved, 0);
    }

    private static ApplicationCountSummary summary(long total, long individual, long club, long approved,
            long cancelled) {
        ApplicationCountSummary summary = new ApplicationCountSummary();
        summary.setTotal(total);
        summary.setIndividualCount(individual);
        summary.setClubCount(club);
        summary.setApprovedCount(approved);
        summary.setCancelledCount(cancelled);
        return summary;
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
