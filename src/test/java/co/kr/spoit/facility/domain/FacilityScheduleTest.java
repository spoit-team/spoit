package co.kr.spoit.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 주간·날짜별·휴관을 합친 이용 가능 시간 계산을 검증한다. */
class FacilityScheduleTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 2);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 8);

    @Test
    @DisplayName("주간 규칙이 없으면 미설정으로 보고 예약을 막는다")
    void unconfiguredWhenNoWeeklyRule() {
        FacilitySchedule schedule = FacilitySchedule.of(List.of(), List.of(), List.of());

        assertThat(schedule.isConfigured()).isFalse();
        assertThat(schedule.covers(MONDAY.atTime(10, 0), MONDAY.atTime(11, 0))).isFalse();
    }

    @Test
    @DisplayName("주간 규칙이 없으면 날짜 예외가 있어도 예약할 수 없고 최종 이용 가능 시간도 비어 있다")
    void overrideDoesNotMakeUnconfiguredFacilityBookable() {
        List<DateOverride> overrides = List.of(DateOverride.of(MONDAY, List.of(0, 1, 2), "새벽 개방"));
        FacilitySchedule schedule = FacilitySchedule.of(List.of(), overrides, List.of());

        assertThat(schedule.isConfigured()).isFalse();
        // 신규 신청 차단(requireAvailable)과 저장 후보 검사가 같은 판정을 써야 한다.
        assertThat(schedule.covers(MONDAY.atTime(0, 0), MONDAY.atTime(1, 0))).isFalse();
        assertThat(schedule.availableHours(MONDAY)).isEmpty();
        assertThat(schedule.availableIntervals(MONDAY)).isEmpty();
        // 기저 선택은 예외 저장·휴관 잠금 슬롯 보존을 위해 그대로 남는다.
        assertThat(schedule.isBaseHourOpen(MONDAY, 0)).isTrue();
        assertThat(schedule.isBaseHourOpen(MONDAY, 3)).isFalse();
    }

    @Test
    @DisplayName("연속 3일 운영은 하나의 구간으로 인정하고 중간에 닫힌 날이 있으면 거부한다")
    void coversThreeConsecutiveDaysOnlyWhenUnbroken() {
        // 월·화·수요일 00:00~다음 날 00:00(24시간) 운영이다.
        List<OperatingRule> rules = List.of(
                OperatingRule.open(1, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, true),
                OperatingRule.open(2, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, true),
                OperatingRule.open(3, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, true));
        FacilitySchedule schedule = FacilitySchedule.of(rules, List.of(), List.of());

        // 월요일 00:00 ~ 목요일 00:00 (3일 연속, 자정 경계로 이어짐)
        assertThat(schedule.covers(MONDAY.atTime(0, 0), MONDAY.plusDays(3).atTime(0, 0))).isTrue();
        // 같은 요청 구간에 휴관이 끼면 중간이 끊긴다.
        FacilitySchedule withClosure = FacilitySchedule.of(rules, List.of(),
                List.of(closure(TUESDAY.atTime(10, 0), TUESDAY.atTime(11, 0),
                        FacilityClosure.STATUS_ACTIVE)));
        assertThat(withClosure.covers(MONDAY.atTime(0, 0), MONDAY.plusDays(3).atTime(0, 0))).isFalse();
        assertThat(withClosure.covers(MONDAY.atTime(0, 0), TUESDAY.atTime(10, 0))).isTrue();
        assertThat(withClosure.covers(TUESDAY.atTime(11, 0), MONDAY.plusDays(3).atTime(0, 0))).isTrue();
    }

    @Test
    @DisplayName("7일 전체 휴무는 설정된 상태지만 이용 가능 시간이 없다")
    void allWeekClosedIsConfiguredButUnavailable() {
        List<OperatingRule> rules = List.of(
                OperatingRule.closed(1), OperatingRule.closed(2), OperatingRule.closed(3),
                OperatingRule.closed(4), OperatingRule.closed(5), OperatingRule.closed(6),
                OperatingRule.closed(7));

        FacilitySchedule schedule = FacilitySchedule.of(rules, List.of(), List.of());

        assertThat(schedule.isConfigured()).isTrue();
        assertThat(schedule.availableHours(MONDAY)).isEmpty();
        assertThat(schedule.covers(MONDAY.atTime(0, 0), MONDAY.atTime(1, 0))).isFalse();
    }

    @Test
    @DisplayName("월요일 야간 운영은 자연일로 쪼개 화요일 00시부터 적용된다")
    void weeklyOvernightSpansIntoNextDay() {
        FacilitySchedule schedule = FacilitySchedule.of(
                List.of(OperatingRule.open(1, LocalTime.of(22, 0), LocalTime.of(6, 0), true)),
                List.of(), List.of());

        assertThat(schedule.availableHours(MONDAY)).containsExactly(22, 23);
        assertThat(schedule.availableHours(TUESDAY)).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(schedule.covers(MONDAY.atTime(22, 0), TUESDAY.atTime(1, 0))).isTrue();
        assertThat(schedule.covers(TUESDAY.atTime(5, 0), TUESDAY.atTime(7, 0))).isFalse();
    }

    @Test
    @DisplayName("일요일에서 월요일로 넘어가는 야간 운영과 연말 경계도 이어진다")
    void sundayNightCrossesIntoMondayAndNewYear() {
        LocalDate yearEndSunday = LocalDate.of(2026, 12, 27);
        List<OperatingRule> rules = List.of(
                OperatingRule.open(7, LocalTime.of(22, 0), LocalTime.of(2, 0), true));
        FacilitySchedule schedule = FacilitySchedule.of(rules, List.of(), List.of());

        assertThat(schedule.availableHours(yearEndSunday)).containsExactly(22, 23);
        assertThat(schedule.availableHours(yearEndSunday.plusDays(1))).containsExactly(0, 1);
        assertThat(schedule.covers(yearEndSunday.atTime(23, 0), yearEndSunday.plusDays(1).atTime(1, 0)))
                .isTrue();
    }

    @Test
    @DisplayName("날짜별 예외는 그 날짜 전체를 대체하고 주간 시간 밖 개방도 허용한다")
    void dateOverrideReplacesWholeDay() {
        List<OperatingRule> rules = List.of(
                OperatingRule.open(1, LocalTime.of(6, 0), LocalTime.of(22, 0), false));
        List<DateOverride> overrides = List.of(DateOverride.of(MONDAY, List.of(0, 1, 2), "새벽 개방"));
        FacilitySchedule schedule = FacilitySchedule.of(rules, overrides, List.of());

        assertThat(schedule.availableHours(MONDAY)).containsExactly(0, 1, 2);
        assertThat(schedule.availableHours(TUESDAY)).isEmpty();
    }

    @Test
    @DisplayName("슬롯이 0개인 날짜 예외는 그 날짜 전체 휴무이며 전날 야간까지 막는다")
    void emptyOverrideClosesWholeDayIncludingPreviousNight() {
        List<OperatingRule> rules = List.of(
                OperatingRule.open(1, LocalTime.of(22, 0), LocalTime.of(6, 0), true));
        // 화요일 예외 헤더만 있고 슬롯이 없다.
        List<DateOverride> overrides = List.of(DateOverride.of(TUESDAY, List.of(), "정기 휴무"));
        FacilitySchedule schedule = FacilitySchedule.of(rules, overrides, List.of());

        assertThat(schedule.hasOverride(TUESDAY)).isTrue();
        assertThat(schedule.availableHours(TUESDAY)).isEmpty();
        assertThat(schedule.covers(MONDAY.atTime(23, 0), TUESDAY.atTime(1, 0))).isFalse();
        // 월요일 자체는 그대로 열려 있다.
        assertThat(schedule.availableHours(MONDAY)).containsExactly(22, 23);
    }

    @Test
    @DisplayName("적용 중 휴관만 차감하고 취소된 휴관은 무시한다")
    void activeClosuresSubtractAndCancelledAreIgnored() {
        List<OperatingRule> rules = List.of(
                OperatingRule.open(1, LocalTime.of(6, 0), LocalTime.of(22, 0), false));
        FacilitySchedule schedule = FacilitySchedule.of(rules, List.of(), List.of(
                closure(MONDAY.atTime(10, 0), MONDAY.atTime(12, 0), FacilityClosure.STATUS_ACTIVE),
                closure(MONDAY.atTime(14, 0), MONDAY.atTime(16, 0), FacilityClosure.STATUS_CANCELLED)));

        assertThat(schedule.availableHours(MONDAY))
                .doesNotContain(10, 11)
                .contains(14, 15);
        assertThat(schedule.covers(MONDAY.atTime(9, 0), MONDAY.atTime(10, 0))).isTrue();
        assertThat(schedule.covers(MONDAY.atTime(10, 0), MONDAY.atTime(11, 0))).isFalse();
    }

    @Test
    @DisplayName("휴관 종료와 매칭 시작이 맞닿으면 겹치지 않는다")
    void adjacentClosureBoundaryIsAllowed() {
        List<OperatingRule> rules = List.of(
                OperatingRule.open(1, LocalTime.of(6, 0), LocalTime.of(22, 0), false));
        FacilitySchedule schedule = FacilitySchedule.of(rules, List.of(), List.of(
                closure(MONDAY.atTime(8, 0), MONDAY.atTime(10, 0), FacilityClosure.STATUS_ACTIVE)));

        assertThat(schedule.covers(MONDAY.atTime(10, 0), MONDAY.atTime(11, 0))).isTrue();
        assertThat(schedule.isClosureBlockedHour(MONDAY, 9)).isTrue();
        assertThat(schedule.isClosureBlockedHour(MONDAY, 10)).isFalse();
    }

    @Test
    @DisplayName("야간 운영 중 자정을 넘는 휴관도 자연일 경계에서 정확히 차단한다")
    void overnightClosureBlocksAcrossMidnight() {
        List<OperatingRule> rules = List.of(
                OperatingRule.open(1, LocalTime.of(22, 0), LocalTime.of(6, 0), true));
        FacilitySchedule schedule = FacilitySchedule.of(rules, List.of(), List.of(
                closure(MONDAY.atTime(23, 0), TUESDAY.atTime(1, 0), FacilityClosure.STATUS_ACTIVE)));

        assertThat(schedule.availableHours(MONDAY)).containsExactly(22);
        assertThat(schedule.availableHours(TUESDAY)).containsExactly(1, 2, 3, 4, 5);
    }

    private static FacilityClosure closure(LocalDateTime start, LocalDateTime end, String status) {
        FacilityClosure closure = new FacilityClosure();
        closure.setStartAt(start);
        closure.setEndAt(end);
        closure.setStatus(status);
        return closure;
    }
}
