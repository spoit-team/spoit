package co.kr.spoit.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.mapper.FacilityScheduleMapper;

/** 대관 가능 구역 수(동시 예약 한도) 검증 API를 검증한다. */
@ExtendWith(MockitoExtension.class)
class FacilityCapacityServiceImplTest {

    private static final long FACILITY_ID = 10L;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 2);

    @Mock
    private FacilityScheduleMapper scheduleMapper;

    @InjectMocks
    private FacilityCapacityServiceImpl service;

    @Test
    @DisplayName("연속된 두 방을 가로지르는 새 방은 동시 점유 최대 2라 한도 2에서 허용된다")
    void allowsRoomCrossingTwoAdjacentRoomsUnderLimitTwo() {
        when(scheduleMapper.selectConcurrentLimitForUpdate(FACILITY_ID)).thenReturn(2);
        when(scheduleMapper.selectMatchPeriodsForUpdate(eq(FACILITY_ID), anyList())).thenReturn(List.of(
                period(1L, 10, 11),
                period(2L, 11, 12)));

        service.requireCapacity(FACILITY_ID, DATE.atTime(10, 0), DATE.atTime(12, 0));
    }

    @Test
    @DisplayName("한도가 가득 찬 시간대에는 새 방을 허용하지 않는다")
    void rejectsWhenLimitReached() {
        when(scheduleMapper.selectConcurrentLimitForUpdate(FACILITY_ID)).thenReturn(2);
        when(scheduleMapper.selectMatchPeriodsForUpdate(eq(FACILITY_ID), anyList())).thenReturn(List.of(
                period(1L, 10, 12),
                period(2L, 11, 13)));

        // 11~12시에는 기존 두 방(10~12, 11~13)이 동시에 있어 한도 2가 이미 찼다.
        assertThatThrownBy(() -> service.requireCapacity(FACILITY_ID,
                DATE.atTime(11, 0), DATE.atTime(12, 0)))
                .isInstanceOf(FacilityScheduleUnavailableException.class)
                .hasMessageContaining("동시 대관 한도");
    }

    @Test
    @DisplayName("시설이 없으면 대관 한도를 확인할 수 없다")
    void rejectsUnknownFacility() {
        when(scheduleMapper.selectConcurrentLimitForUpdate(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.requireCapacity(999L,
                DATE.atTime(10, 0), DATE.atTime(11, 0)))
                .isInstanceOf(FacilityScheduleUnavailableException.class)
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    @DisplayName("요청 구간이 겹치지 않으면 기존 점유와 무관하게 허용한다")
    void allowsNonOverlappingRange() {
        when(scheduleMapper.selectConcurrentLimitForUpdate(FACILITY_ID)).thenReturn(1);
        when(scheduleMapper.selectMatchPeriodsForUpdate(eq(FACILITY_ID), anyList())).thenReturn(List.of(
                period(1L, 10, 11)));

        service.requireCapacity(FACILITY_ID, DATE.atTime(11, 0), DATE.atTime(12, 0));
    }

    private static MatchPeriod period(Long matchId, int startHour, int endHour) {
        MatchPeriod period = new MatchPeriod();
        period.setMatchId(matchId);
        period.setStartAt(DATE.atTime(LocalTime.of(startHour, 0)));
        period.setEndAt(DATE.atTime(LocalTime.of(endHour, 0)));
        return period;
    }
}
