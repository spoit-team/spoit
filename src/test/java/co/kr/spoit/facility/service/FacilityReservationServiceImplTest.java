package co.kr.spoit.facility.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.kr.spoit.facility.domain.FacilityClosure;
import co.kr.spoit.facility.domain.OperatingRule;
import co.kr.spoit.facility.mapper.FacilityScheduleMapper;

/** 매칭 도메인에 공개하는 시설 일정 검사 계약을 검증한다. */
@ExtendWith(MockitoExtension.class)
class FacilityReservationServiceImplTest {

    private static final long FACILITY_ID = 10L;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 3);

    @Mock
    private FacilityScheduleMapper scheduleMapper;

    @Mock
    private FacilityScheduleLoader scheduleLoader;

    @InjectMocks
    private FacilityReservationServiceImpl service;

    @Test
    @DisplayName("시설이 없거나 삭제됐으면 잠금 단계에서 안내 예외를 던진다")
    void lockFailsWhenFacilityMissing() {
        when(scheduleMapper.selectFacilityNameForUpdate(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.lockActiveFacility(999L))
                .isInstanceOf(FacilityScheduleUnavailableException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    @DisplayName("운영 시간이 설정되지 않은 시설은 예약할 수 없다")
    void requireAvailableRejectsUnconfiguredFacility() {
        when(scheduleMapper.selectFacilityNameForUpdate(FACILITY_ID)).thenReturn("테스트 체육관");
        when(scheduleLoader.snapshotForUpdate(FACILITY_ID))
                .thenReturn(new FacilityScheduleLoader.ScheduleSnapshot(List.of(), List.of(), List.of()));

        assertThatThrownBy(() -> service.requireAvailable(FACILITY_ID,
                DATE.atTime(10, 0), DATE.atTime(11, 0)))
                .isInstanceOf(FacilityScheduleUnavailableException.class)
                .hasMessageContaining("운영 시간이 아직 설정되지 않아");
    }

    @Test
    @DisplayName("운영 시간 안의 요청 구간은 통과한다")
    void requireAvailableAllowsInsideOperatingHours() {
        givenWeekly(LocalTime.of(6, 0), LocalTime.of(22, 0), false);

        service.requireAvailable(FACILITY_ID, DATE.atTime(10, 0), DATE.atTime(11, 0));
    }

    @Test
    @DisplayName("휴관과 겹치는 구간은 예약할 수 없다")
    void requireAvailableRejectsClosure() {
        FacilityClosure closure = new FacilityClosure();
        closure.setStartAt(DATE.atTime(10, 0));
        closure.setEndAt(DATE.atTime(12, 0));
        closure.setStatus(FacilityClosure.STATUS_ACTIVE);
        givenSnapshot(List.of(OperatingRule.open(
                DATE.getDayOfWeek().getValue(), LocalTime.of(6, 0), LocalTime.of(22, 0), false)),
                List.of(closure));

        assertThatThrownBy(() -> service.requireAvailable(FACILITY_ID,
                DATE.atTime(10, 0), DATE.atTime(11, 0)))
                .isInstanceOf(FacilityScheduleUnavailableException.class)
                .hasMessageContaining("휴관");
    }

    @Test
    @DisplayName("날짜별 0슬롯 휴무는 하루 전체 예약을 막는다")
    void requireAvailableRejectsClosedOverrideDay() {
        givenSnapshot(
                List.of(OperatingRule.open(DATE.getDayOfWeek().getValue(),
                        LocalTime.of(6, 0), LocalTime.of(22, 0), false)),
                List.of());
        when(scheduleLoader.snapshotForUpdate(FACILITY_ID)).thenReturn(
                new FacilityScheduleLoader.ScheduleSnapshot(
                        List.of(OperatingRule.open(DATE.getDayOfWeek().getValue(),
                                LocalTime.of(6, 0), LocalTime.of(22, 0), false)),
                        List.of(co.kr.spoit.facility.domain.DateOverride.of(DATE, List.of(), "휴무")),
                        List.of()));

        assertThatThrownBy(() -> service.requireAvailable(FACILITY_ID,
                DATE.atTime(10, 0), DATE.atTime(11, 0)))
                .isInstanceOf(FacilityScheduleUnavailableException.class);
    }

    @Test
    @DisplayName("요청 구간이 뒤집혔으면 거부한다")
    void requireAvailableRejectsReversedRange() {
        when(scheduleMapper.selectFacilityNameForUpdate(FACILITY_ID)).thenReturn("테스트 체육관");

        assertThatThrownBy(() -> service.requireAvailable(FACILITY_ID,
                DATE.atTime(11, 0), DATE.atTime(10, 0)))
                .isInstanceOf(FacilityScheduleUnavailableException.class);
    }

    @Test
    @DisplayName("3일 연속 이용 가능한 구간은 기간 상한 없이 통과한다")
    void requireAvailableAllowsThreeConsecutiveDays() {
        givenDailyAllDayFor(DATE, DATE.plusDays(3));

        service.requireAvailable(FACILITY_ID, DATE.atTime(0, 0), DATE.plusDays(3).atTime(0, 0));
    }

    @Test
    @DisplayName("3일 구간 중간에 닫힌 날이 있으면 저장 후보와 마찬가지로 거부한다")
    void requireAvailableRejectsGapInsideMultiDayRange() {
        givenDailyAllDayFor(DATE, DATE.plusDays(3), DATE.plusDays(1).getDayOfWeek().getValue());

        assertThatThrownBy(() -> service.requireAvailable(FACILITY_ID,
                DATE.atTime(0, 0), DATE.plusDays(3).atTime(0, 0)))
                .isInstanceOf(FacilityScheduleUnavailableException.class);
    }

    private void givenWeekly(LocalTime open, LocalTime close, boolean nextDay) {
        givenSnapshot(List.of(OperatingRule.open(DATE.getDayOfWeek().getValue(), open, close, nextDay)),
                List.of());
    }

    /** 모든 요일을 24시간 운영으로 두되 {@code closedWeekdays} 요일은 휴무로 만든다. */
    private void givenDailyAllDayFor(LocalDate from, LocalDate to, int... closedWeekdays) {
        List<Integer> closed = Arrays.stream(closedWeekdays).boxed().toList();
        List<OperatingRule> rules = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            int weekday = date.getDayOfWeek().getValue();
            rules.add(closed.contains(weekday)
                    ? OperatingRule.closed(weekday)
                    : OperatingRule.open(weekday, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, true));
        }
        givenSnapshot(rules, List.of());
    }

    private void givenSnapshot(List<OperatingRule> rules, List<FacilityClosure> closures) {
        when(scheduleMapper.selectFacilityNameForUpdate(FACILITY_ID)).thenReturn("테스트 체육관");
        when(scheduleLoader.snapshotForUpdate(FACILITY_ID))
                .thenReturn(new FacilityScheduleLoader.ScheduleSnapshot(rules, List.of(), closures));
    }
}
