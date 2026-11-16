package co.kr.spoit.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.kr.spoit.facility.domain.DateOverride;
import co.kr.spoit.facility.domain.DateOverrideSlot;
import co.kr.spoit.facility.mapper.FacilityScheduleMapper;

class FacilityScheduleLoaderTest {
    @Test
    void readOnlySnapshotGroupsSlotsAndPreservesClosedDates() {
        FacilityScheduleMapper mapper = mock(FacilityScheduleMapper.class);
        FacilityScheduleLoader loader = new FacilityScheduleLoader(mapper);
        DateOverride open = DateOverride.of(LocalDate.of(2026, 11, 10), List.of(), null);
        open.setOverrideId(1L);
        DateOverride closed = DateOverride.of(LocalDate.of(2026, 11, 11), List.of(), null);
        closed.setOverrideId(2L);
        DateOverrideSlot first = new DateOverrideSlot();
        first.setOverrideId(1L);
        first.setSlotHour(8);
        DateOverrideSlot second = new DateOverrideSlot();
        second.setOverrideId(1L);
        second.setSlotHour(20);
        when(mapper.selectDateOverrides(10L)).thenReturn(List.of(open, closed));
        when(mapper.selectDateOverrideSlots(10L)).thenReturn(List.of(first, second));

        var snapshot = loader.snapshot(10L);

        assertThat(snapshot.overrides()).containsExactly(open, closed);
        assertThat(open.getHours()).containsExactly(8, 20);
        assertThat(closed.getHours()).isEmpty();
        verify(mapper).selectDateOverrideSlots(10L);
        verify(mapper, never()).selectDateOverrideHoursForUpdate(1L);
    }

    @Test
    void lockedSnapshotKeepsPerHeaderCurrentReads() {
        FacilityScheduleMapper mapper = mock(FacilityScheduleMapper.class);
        FacilityScheduleLoader loader = new FacilityScheduleLoader(mapper);
        DateOverride open = DateOverride.of(LocalDate.of(2026, 11, 10), List.of(), null);
        open.setOverrideId(1L);
        when(mapper.selectDateOverridesForUpdate(10L)).thenReturn(List.of(open));
        when(mapper.selectDateOverrideHoursForUpdate(1L)).thenReturn(List.of(9, 10));

        assertThat(loader.snapshotForUpdate(10L).overrides().get(0).getHours()).containsExactly(9, 10);
        verify(mapper, never()).selectDateOverrideSlots(10L);
    }

    @Test
    void readOnlySnapshotWithFiveHundredHeadersUsesSingleBulkSlotRead() {
        FacilityScheduleMapper mapper = mock(FacilityScheduleMapper.class);
        FacilityScheduleLoader loader = new FacilityScheduleLoader(mapper);
        List<DateOverride> headers = new ArrayList<>();
        List<DateOverrideSlot> slots = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            DateOverride header = DateOverride.of(LocalDate.of(2026, 1, 1).plusDays(index), List.of(), null);
            header.setOverrideId((long) index + 1);
            headers.add(header);
            if (index == 0) {
                continue;
            }
            DateOverrideSlot slot = new DateOverrideSlot();
            slot.setOverrideId((long) index + 1);
            slot.setSlotHour(index % 24);
            slots.add(slot);
        }
        when(mapper.selectDateOverrides(10L)).thenReturn(headers);
        when(mapper.selectDateOverrideSlots(10L)).thenReturn(slots);

        var snapshot = loader.snapshot(10L);

        assertThat(snapshot.overrides()).hasSize(500);
        assertThat(snapshot.overrides().get(0).getHours()).isEmpty();
        for (int index = 1; index < 500; index++) {
            assertThat(snapshot.overrides().get(index).getHours()).containsExactly(index % 24);
        }
        verify(mapper, times(1)).selectDateOverrideSlots(10L);
        verify(mapper, never()).selectDateOverrideHoursForUpdate(anyLong());
    }
}
