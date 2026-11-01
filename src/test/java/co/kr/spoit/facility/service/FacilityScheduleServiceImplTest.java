package co.kr.spoit.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.kr.spoit.facility.domain.FacilityClosure;
import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.domain.OperatingRule;
import co.kr.spoit.facility.dto.ClosureForm;
import co.kr.spoit.facility.dto.ConditionsForm;
import co.kr.spoit.facility.dto.DateScheduleForm;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.dto.WeeklyHourRow;
import co.kr.spoit.facility.dto.WeeklyScheduleForm;
import co.kr.spoit.facility.mapper.FacilityMapper;
import co.kr.spoit.facility.mapper.FacilityScheduleMapper;

/** 예약 운영 일정 저장의 소유권·검증·충돌 차단을 검증한다. */
@ExtendWith(MockitoExtension.class)
class FacilityScheduleServiceImplTest {

    private static final long FACILITY_ID = 10L;
    private static final long USER_ID = 7L;
    private static final LocalDate DATE = LocalDate.of(2026, 11, 2);

    @Mock
    private FacilityScheduleMapper scheduleMapper;

    @Mock
    private FacilityScheduleLoader scheduleLoader;

    @Mock
    private FacilityMapper facilityMapper;

    @Mock
    private FacilityService facilityService;

    @InjectMocks
    private FacilityScheduleServiceImpl service;

    @Test
    @DisplayName("담당하지 않는 시설의 일정은 저장하지 않고 조회도 하지 않는다")
    void rejectsNonManagedFacility() {
        when(facilityMapper.existsFacilityManager(FACILITY_ID, USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.saveWeeklyHours(USER_ID, weeklyForm()))
                .isInstanceOf(FacilityAccessDeniedException.class);

        verifyNoInteractions(scheduleMapper);
        verify(scheduleMapper, never()).insertWeeklyRules(anyLong(), anyList());
    }

    @Test
    @DisplayName("운영 시간 미설정 시설에는 7일 휴무 설정을 저장할 수 있고 7행을 남긴다")
    void savesAllWeekClosedRows() {
        givenManagedFacility();
        givenFacilityLocked();
        when(scheduleLoader.snapshotForUpdate(FACILITY_ID))
                .thenReturn(new FacilityScheduleLoader.ScheduleSnapshot(List.of(), List.of(), List.of()));
        when(scheduleMapper.selectMatchPeriodsForUpdate(eq(FACILITY_ID), anyList())).thenReturn(List.of());

        WeeklyScheduleForm form = weeklyForm();
        form.setOpenDays(List.of());
        service.saveWeeklyHours(USER_ID, form);

        verify(scheduleMapper).insertWeeklyRules(eq(FACILITY_ID), org.mockito.ArgumentMatchers.argThat(rows ->
                rows.size() == 7 && rows.stream().noneMatch(OperatingRule::isOpen)));
    }

    @Test
    @DisplayName("시작과 종료가 같은 요일 설정은 저장하지 않는다")
    void rejectsIdenticalStartAndEnd() {
        givenManagedFacility();

        WeeklyScheduleForm form = weeklyForm();
        form.setOpenDays(List.of(1));
        form.setStartTimes(Map.of(1, "10:00"));
        form.setEndTimes(Map.of(1, "10:00"));

        assertThatThrownBy(() -> service.saveWeeklyHours(USER_ID, form))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("같습니다");

        verify(scheduleMapper, never()).insertWeeklyRules(anyLong(), anyList());
    }

    @Test
    @DisplayName("종료가 시작보다 이르면 다음 날 운영으로 저장한다")
    void savesOvernightAsNextDay() {
        givenManagedFacility();
        givenFacilityLocked();
        when(scheduleLoader.snapshotForUpdate(FACILITY_ID))
                .thenReturn(new FacilityScheduleLoader.ScheduleSnapshot(List.of(), List.of(), List.of()));
        when(scheduleMapper.selectMatchPeriodsForUpdate(eq(FACILITY_ID), anyList())).thenReturn(List.of());

        WeeklyScheduleForm form = weeklyForm();
        form.setOpenDays(List.of(1));
        form.setStartTimes(Map.of(1, "22:00"));
        form.setEndTimes(Map.of(1, "06:00"));

        service.saveWeeklyHours(USER_ID, form);

        verify(scheduleMapper).insertWeeklyRules(eq(FACILITY_ID), org.mockito.ArgumentMatchers.argThat(rows ->
                rows.size() == 7
                        && rows.get(0).isOpen()
                        && rows.get(0).isEndNextDay()
                        && rows.get(0).getCloseTime().equals(LocalTime.of(6, 0))));
    }

    @Test
    @DisplayName("변경 후 포함되지 않는 활성 매칭방이 있으면 주간 설정 전체를 저장하지 않는다")
    void rejectsWeeklyChangeConflictingWithActiveMatch() {
        givenManagedFacility();
        givenFacilityLocked();
        when(scheduleLoader.snapshotForUpdate(FACILITY_ID))
                .thenReturn(new FacilityScheduleLoader.ScheduleSnapshot(List.of(), List.of(), List.of()));
        MatchPeriod match = match(500L, "야간 경기", 22, 23);
        // 기존 방은 22~23시인데 새 설정은 06~22시라 22시 슬롯이 포함되지 않는다.
        when(scheduleMapper.selectMatchPeriodsForUpdate(eq(FACILITY_ID), anyList())).thenReturn(List.of(match));

        WeeklyScheduleForm form = weeklyForm();
        form.setOpenDays(List.of(1));
        form.setStartTimes(Map.of(1, "06:00"));
        form.setEndTimes(Map.of(1, "22:00"));

        assertThatThrownBy(() -> service.saveWeeklyHours(USER_ID, form))
                .isInstanceOf(ScheduleConflictException.class)
                .satisfies(exception -> assertThat(((ScheduleConflictException) exception).getConflicts())
                        .extracting(MatchPeriod::getMatchId).containsExactly(500L));

        verify(scheduleMapper, never()).deleteWeeklyRules(anyLong());
        verify(scheduleMapper, never()).insertWeeklyRules(anyLong(), anyList());
    }

    @Test
    @DisplayName("주간 운영 미설정 시설의 날짜별 예외 저장은 활성 방 전체를 충돌로 보여 주고 쓰지 않는다")
    void rejectsDateOverrideOnUnconfiguredFacilityWithActiveMatches() {
        givenManagedFacility();
        givenFacilityLocked();
        // 주간 규칙이 하나도 없는 시설이라 기존 방은 실제로 이용 가능하지 않다.
        when(scheduleLoader.snapshotForUpdate(FACILITY_ID))
                .thenReturn(new FacilityScheduleLoader.ScheduleSnapshot(List.of(), List.of(), List.of()));
        List<MatchPeriod> matches = List.of(
                match(500L, "첫 방", 10, 11),
                match(501L, "둘째 방", 11, 12));
        when(scheduleMapper.selectMatchPeriodsForUpdate(eq(FACILITY_ID), anyList())).thenReturn(matches);

        DateScheduleForm form = new DateScheduleForm();
        form.setFacilityId(FACILITY_ID);
        form.setDate("2026-11-02");
        form.setHours(List.of("10:00", "11:00"));

        assertThatThrownBy(() -> service.saveDateHours(USER_ID, form))
                .isInstanceOf(ScheduleConflictException.class)
                .satisfies(exception -> assertThat(((ScheduleConflictException) exception).getConflicts())
                        .extracting(MatchPeriod::getMatchId).containsExactly(500L, 501L));

        // 예외가 있어도 미설정 시설은 예약할 수 없으므로 아무것도 쓰지 않는다.
        verify(scheduleMapper, never()).insertDateOverride(org.mockito.ArgumentMatchers.any());
        verify(scheduleMapper, never()).insertDateOverrideHours(anyLong(), anyList());
    }

    @Test
    @DisplayName("날짜 안내는 휴관을 차감한 실제 이용 가능 시간으로 계산한다")
    void availabilityCountsClosureSubtractedHours() {
        List<OperatingRule> rules = List.of(
                OperatingRule.open(1, LocalTime.of(6, 0), LocalTime.of(12, 0), false));
        FacilityClosure closure = new FacilityClosure();
        closure.setStartAt(DATE.atTime(9, 0));
        closure.setEndAt(DATE.atTime(10, 0));
        closure.setStatus(FacilityClosure.STATUS_ACTIVE);
        givenPageQueries(new FacilityScheduleLoader.ScheduleSnapshot(rules, List.of(), List.of(closure)));

        co.kr.spoit.facility.dto.CalendarView calendar = service
                .findSchedulePage(USER_ID, FACILITY_ID, DATE, null, null, null)
                .getCalendar();

        // 기저 선택(06~12시 6칸)은 그대로 두고, 안내 문구만 휴관 1칸을 뺀 5시간으로 표시한다.
        assertThat(calendar.getSelectedCount()).isEqualTo(6);
        assertThat(calendar.getAvailabilityText()).isEqualTo("예약 가능 5시간");
    }

    @Test
    @DisplayName("주간 운영 미설정 시설은 날짜 예외가 있어도 신규 신청 차단을 안내한다")
    void availabilityWarnsUnconfiguredEvenWithOverride() {
        co.kr.spoit.facility.domain.DateOverride override =
                co.kr.spoit.facility.domain.DateOverride.of(DATE, List.of(0, 1), "새벽 개방");
        givenPageQueries(new FacilityScheduleLoader.ScheduleSnapshot(List.of(), List.of(override), List.of()));

        co.kr.spoit.facility.dto.CalendarView calendar = service
                .findSchedulePage(USER_ID, FACILITY_ID, DATE, null, null, null)
                .getCalendar();

        assertThat(calendar.getSourceLabel()).contains("주간 운영 시간이 설정되지 않아 신규 신청이 차단");
        assertThat(calendar.getAvailabilityText()).contains("주간 운영 시간이 설정되지 않아 신규 신청이 차단");
        // 예외 슬롯의 기저 선택과 안내 문구는 보존한다.
        assertThat(calendar.getSelectedCount()).isEqualTo(2);
        assertThat(calendar.getNote()).isEqualTo("새벽 개방");
    }

    @Test
    @DisplayName("날짜별 0슬롯 저장은 예외 헤더를 남겨 그날 휴무로 구분한다")
    void savesEmptyOverrideAsClosedDay() {
        givenManagedFacility();
        givenFacilityLocked();
        when(scheduleLoader.snapshotForUpdate(FACILITY_ID))
                .thenReturn(new FacilityScheduleLoader.ScheduleSnapshot(List.of(), List.of(), List.of()));
        when(scheduleMapper.selectMatchPeriodsForUpdate(eq(FACILITY_ID), anyList())).thenReturn(List.of());
        when(scheduleMapper.insertDateOverride(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            co.kr.spoit.facility.domain.DateOverride override = invocation.getArgument(0);
            override.setOverrideId(900L);
            return 1;
        });

        DateScheduleForm form = new DateScheduleForm();
        form.setFacilityId(FACILITY_ID);
        form.setDate("2026-11-02");
        form.setHours(List.of());
        form.setNote("정기 휴무");
        service.saveDateHours(USER_ID, form);

        verify(scheduleMapper).insertDateOverride(org.mockito.ArgumentMatchers.argThat(override ->
                override.getDate().equals(DATE) && override.getHours().isEmpty()));
        verify(scheduleMapper, never()).insertDateOverrideHours(anyLong(), anyList());
    }

    @Test
    @DisplayName("휴관으로 비활성화된 슬롯의 기존 선택은 저장 시 보존한다")
    void preservesBlockedHourSelection() {
        givenManagedFacility();
        givenFacilityLocked();
        // 주간 06~12시, 09~10시에 적용 중 휴관이 있다.
        List<OperatingRule> rules = List.of(
                OperatingRule.open(1, LocalTime.of(6, 0), LocalTime.of(12, 0), false));
        FacilityClosure closure = new FacilityClosure();
        closure.setStartAt(DATE.atTime(9, 0));
        closure.setEndAt(DATE.atTime(10, 0));
        closure.setStatus(FacilityClosure.STATUS_ACTIVE);
        when(scheduleLoader.snapshotForUpdate(FACILITY_ID))
                .thenReturn(new FacilityScheduleLoader.ScheduleSnapshot(rules, List.of(), List.of(closure)));
        when(scheduleMapper.selectMatchPeriodsForUpdate(eq(FACILITY_ID), anyList())).thenReturn(List.of());
        when(scheduleMapper.insertDateOverride(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            co.kr.spoit.facility.domain.DateOverride override = invocation.getArgument(0);
            override.setOverrideId(901L);
            return 1;
        });

        DateScheduleForm form = new DateScheduleForm();
        form.setFacilityId(FACILITY_ID);
        form.setDate("2026-11-02");
        // 09시는 휴관으로 disabled되어 전송되지 않았다. 06·07만 사용자가 남겼다.
        form.setHours(List.of("06:00", "07:00"));
        service.saveDateHours(USER_ID, form);

        verify(scheduleMapper).insertDateOverrideHours(901L, List.of(6, 7, 9));
    }

    @Test
    @DisplayName("모집 조건은 2~200명만 저장한다")
    void validatesConditionRanges() {
        givenManagedFacility();

        ConditionsForm tooSmall = conditions(1);
        assertThatThrownBy(() -> service.saveConditions(USER_ID, tooSmall))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2~200");

        verifyNoInteractions(scheduleMapper);
    }

    @Test
    @DisplayName("시설이 운영하지 않는 종목의 모집 조건은 저장하지 않는다")
    void rejectsConditionForUnknownSport() {
        givenManagedFacility();
        givenFacilityLocked();
        when(scheduleMapper.selectSportCapacityForUpdate(FACILITY_ID, 3L)).thenReturn(null);

        assertThatThrownBy(() -> service.saveConditions(USER_ID, conditions(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("운영하지 않는 종목");

        verify(scheduleMapper, never()).updateSportConditions(anyLong(), anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("휴관이 활성 매칭방과 겹치면 저장하지 않고 겹치는 방 전체를 알린다")
    void rejectsClosureOverlappingActiveMatches() {
        givenManagedFacility();
        givenFacilityLocked();
        List<MatchPeriod> conflicts = List.of(
                match(500L, "첫 방", 10, 11),
                match(501L, "둘째 방", 11, 12));
        when(scheduleMapper.selectOverlappingMatchPeriodsForUpdate(eq(FACILITY_ID), anyList(),
                eq(DATE.atTime(10, 0)), eq(DATE.atTime(12, 0)))).thenReturn(conflicts);

        ClosureForm form = new ClosureForm();
        form.setFacilityId(FACILITY_ID);
        form.setDate("2026-11-02");
        form.setStart("10:00");
        form.setEnd("12:00");
        form.setReason("시설 점검");

        assertThatThrownBy(() -> service.registerClosure(USER_ID, form))
                .isInstanceOf(ScheduleConflictException.class)
                .satisfies(exception -> assertThat(((ScheduleConflictException) exception).getConflicts())
                        .extracting(MatchPeriod::getMatchId).containsExactly(500L, 501L));

        verify(scheduleMapper, never()).insertClosure(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("겹치는 방이 없으면 휴관을 적용 상태로 등록한다")
    void registersClosureWithoutConflict() {
        givenManagedFacility();
        givenFacilityLocked();
        when(scheduleMapper.selectOverlappingMatchPeriodsForUpdate(eq(FACILITY_ID), anyList(),
                eq(DATE.atTime(10, 0)), eq(DATE.atTime(12, 0)))).thenReturn(List.of());

        ClosureForm form = new ClosureForm();
        form.setFacilityId(FACILITY_ID);
        form.setDate("2026-11-02");
        form.setStart("10:00");
        form.setEnd("12:00");
        form.setReason("시설 점검");
        service.registerClosure(USER_ID, form);

        verify(scheduleMapper).insertClosure(org.mockito.ArgumentMatchers.argThat(closure ->
                closure.getStatus().equals(FacilityClosure.STATUS_ACTIVE)
                        && closure.getStartAt().equals(DATE.atTime(10, 0))
                        && closure.getEndAt().equals(DATE.atTime(12, 0))
                        && closure.getCreatedBy() == USER_ID));
    }

    @Test
    @DisplayName("취소 대상은 담당 시설의 적용 중 휴관만 허용한다")
    void rejectsCancellingClosureOfOtherFacility() {
        // 요청이 담당 시설(10)을 주장하지만 대상 휴관은 다른 시설(11) 소속이다.
        when(facilityMapper.existsFacilityManager(FACILITY_ID, USER_ID)).thenReturn(true);
        FacilityClosure target = new FacilityClosure();
        target.setFacilityId(11L);
        target.setScheduleChangeId(300L);
        when(scheduleMapper.selectClosureById(300L)).thenReturn(target);

        assertThatThrownBy(() -> service.cancelClosure(USER_ID, FACILITY_ID, 300L))
                .isInstanceOf(IllegalArgumentException.class);

        verify(scheduleMapper, never()).cancelClosure(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("야간·자정 종료 규칙은 GET 표시값 그대로 재저장해도 같은 구간을 유지한다")
    void weeklyRowsRoundTripKeepsOvernightAndMidnightEnds() {
        // 22:00~다음 날 06:00 과 00:00~다음 날 00:00(24:00) 두 형태를 저장된 상태로 둔다.
        List<OperatingRule> stored = List.of(
                OperatingRule.open(1, LocalTime.of(22, 0), LocalTime.of(6, 0), true),
                OperatingRule.open(2, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, true));
        FacilityListItem facility = managedFacility();
        when(facilityService.findManagedFacilities(USER_ID)).thenReturn(List.of(facility));
        when(scheduleLoader.snapshot(FACILITY_ID))
                .thenReturn(new FacilityScheduleLoader.ScheduleSnapshot(stored, List.of(), List.of()));
        when(scheduleMapper.selectSportConditions(FACILITY_ID)).thenReturn(List.of());
        when(scheduleMapper.selectClosures(FACILITY_ID)).thenReturn(List.of());
        when(scheduleMapper.selectFacilityName(FACILITY_ID)).thenReturn("테스트 체육관");

        List<WeeklyHourRow> rows = service.findSchedulePage(USER_ID, FACILITY_ID, null, null, null, null)
                .getWeeklyHours();

        // 24:00은 다음 날 자정 종료에만 쓰고, 06:00 종료는 실제 시각으로 보여 준다.
        assertThat(rows.get(0).getStart()).isEqualTo("22:00");
        assertThat(rows.get(0).getEnd()).isEqualTo("06:00");
        assertThat(rows.get(1).getStart()).isEqualTo("00:00");
        assertThat(rows.get(1).getEnd()).isEqualTo("24:00");

        // 화면에 보인 값을 그대로 다시 제출한다.
        WeeklyScheduleForm form = new WeeklyScheduleForm();
        form.setFacilityId(FACILITY_ID);
        form.setOpenDays(List.of(1, 2));
        form.setStartTimes(Map.of(1, rows.get(0).getStart(), 2, rows.get(1).getStart()));
        form.setEndTimes(Map.of(1, rows.get(0).getEnd(), 2, rows.get(1).getEnd()));
        givenManagedFacility();
        givenFacilityLocked();
        when(scheduleLoader.snapshotForUpdate(FACILITY_ID))
                .thenReturn(new FacilityScheduleLoader.ScheduleSnapshot(List.of(), List.of(), List.of()));
        when(scheduleMapper.selectMatchPeriodsForUpdate(eq(FACILITY_ID), anyList())).thenReturn(List.of());

        service.saveWeeklyHours(USER_ID, form);

        // 재저장 결과가 원래 저장값(22:00~06:00, 00:00~다음 날 00:00)과 같아야 한다.
        verify(scheduleMapper).insertWeeklyRules(eq(FACILITY_ID), org.mockito.ArgumentMatchers.argThat(saved ->
                saved.size() == 7
                        && saved.get(0).isOpen()
                        && saved.get(0).getOpenTime().equals(LocalTime.of(22, 0))
                        && saved.get(0).getCloseTime().equals(LocalTime.of(6, 0))
                        && saved.get(0).isEndNextDay()
                        && saved.get(1).isOpen()
                        && saved.get(1).getOpenTime().equals(LocalTime.MIDNIGHT)
                        && saved.get(1).getCloseTime().equals(LocalTime.MIDNIGHT)
                        && saved.get(1).isEndNextDay()));
    }

    private void givenManagedFacility() {
        when(facilityMapper.existsFacilityManager(FACILITY_ID, USER_ID)).thenReturn(true);
    }

    private static FacilityListItem managedFacility() {
        co.kr.spoit.facility.domain.FacilitySummary summary =
                new co.kr.spoit.facility.domain.FacilitySummary();
        summary.setFacilityId(FACILITY_ID);
        summary.setName("테스트 체육관");
        summary.setAddress("서울특별시 테스트구");
        summary.setConcurrentMatchLimit(3);
        summary.setStatus("운영 중");
        return FacilityListItem.from(summary);
    }

    /** 변경 경로가 시설 행을 잠그는 계약을 만족시킨다. */
    private void givenFacilityLocked() {
        when(scheduleMapper.selectFacilityNameForUpdate(FACILITY_ID)).thenReturn("테스트 체육관");
    }

    /** 일정 화면 조회가 필요로 하는 읽기 경로를 한 번에 준비한다. */
    private void givenPageQueries(FacilityScheduleLoader.ScheduleSnapshot snapshot) {
        when(facilityService.findManagedFacilities(USER_ID))
                .thenReturn(List.of(managedFacility()));
        when(scheduleLoader.snapshot(FACILITY_ID)).thenReturn(snapshot);
        when(scheduleMapper.selectSportConditions(FACILITY_ID)).thenReturn(List.of());
        when(scheduleMapper.selectClosures(FACILITY_ID)).thenReturn(List.of());
        when(scheduleMapper.selectFacilityName(FACILITY_ID)).thenReturn("테스트 체육관");
    }

    private static WeeklyScheduleForm weeklyForm() {
        WeeklyScheduleForm form = new WeeklyScheduleForm();
        form.setFacilityId(FACILITY_ID);
        Map<Integer, String> starts = new LinkedHashMap<>();
        Map<Integer, String> ends = new LinkedHashMap<>();
        for (int day = 1; day <= 7; day++) {
            starts.put(day, "06:00");
            ends.put(day, "22:00");
        }
        form.setStartTimes(starts);
        form.setEndTimes(ends);
        return form;
    }

    private static ConditionsForm conditions(int capacity) {
        ConditionsForm form = new ConditionsForm();
        form.setFacilityId(FACILITY_ID);
        form.setSportId(3L);
        form.setCapacity(capacity);
        return form;
    }

    private static MatchPeriod match(long matchId, String title, int startHour, int endHour) {
        MatchPeriod period = new MatchPeriod();
        period.setMatchId(matchId);
        period.setTitle(title);
        period.setStartAt(DATE.atTime(startHour, 0));
        period.setEndAt(DATE.atTime(endHour, 0));
        return period;
    }
}
