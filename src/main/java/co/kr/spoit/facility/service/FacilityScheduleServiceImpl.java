package co.kr.spoit.facility.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import co.kr.spoit.facility.domain.DateOverride;
import co.kr.spoit.facility.domain.FacilityClosure;
import co.kr.spoit.facility.domain.FacilitySchedule;
import co.kr.spoit.facility.domain.FacilitySportCondition;
import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.domain.OperatingRule;
import co.kr.spoit.facility.domain.ScheduleTimes;
import co.kr.spoit.facility.dto.CalendarView;
import co.kr.spoit.facility.dto.ClosureForm;
import co.kr.spoit.facility.dto.ClosureRow;
import co.kr.spoit.facility.dto.ConditionsForm;
import co.kr.spoit.facility.dto.ConditionsView;
import co.kr.spoit.facility.dto.DateScheduleForm;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.dto.SchedulePage;
import co.kr.spoit.facility.dto.SportOption;
import co.kr.spoit.facility.dto.WeeklyHourRow;
import co.kr.spoit.facility.dto.WeeklyScheduleForm;
import co.kr.spoit.facility.mapper.FacilityMapper;
import co.kr.spoit.facility.mapper.FacilityScheduleMapper;
import co.kr.spoit.facility.service.FacilityScheduleLoader.ScheduleSnapshot;

/**
 * 예약 운영 일정 화면과 저장을 구현한다.
 *
 * <p>변경마다 담당자 확인 → 시설 행 잠금 → {@code facility_sport} → {@code social_match} 순서로
 * 최신 값을 다시 읽는다. 시설 잠금은 같은 시설의 일정 변경과 매칭방 점유 검증을 직렬화한다.</p>
 */
@Service
public class FacilityScheduleServiceImpl implements FacilityScheduleService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_LABEL = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)");
    private static final DateTimeFormatter DATE_VALUE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int MAX_NOTE_LENGTH = 500;
    private static final int MIN_CAPACITY = 2;
    private static final int MAX_CAPACITY = 200;

    /** 정렬된 상태 목록으로 점유·충돌 대상을 고정한다. */
    private static final List<String> OCCUPYING_STATUSES = FacilityCapacityServiceImpl.OCCUPYING_STATUSES;

    private final FacilityScheduleMapper scheduleMapper;
    private final FacilityScheduleLoader scheduleLoader;
    private final FacilityMapper facilityMapper;
    private final FacilityService facilityService;

    public FacilityScheduleServiceImpl(FacilityScheduleMapper scheduleMapper,
            FacilityScheduleLoader scheduleLoader, FacilityMapper facilityMapper,
            FacilityService facilityService) {
        this.scheduleMapper = scheduleMapper;
        this.scheduleLoader = scheduleLoader;
        this.facilityMapper = facilityMapper;
        this.facilityService = facilityService;
    }

    @Override
    @Transactional(readOnly = true)
    public SchedulePage findSchedulePage(Long userId, Long facilityId, LocalDate date, Long sportId,
            String tab, YearMonth month) {
        FacilityListItem selected = findManagedFacility(userId, facilityId);
        if (selected == null) {
            return SchedulePage.empty();
        }
        long id = selected.getFacilityId();
        ScheduleSnapshot snapshot = scheduleLoader.snapshot(id);
        FacilitySchedule schedule = snapshot.toSchedule();
        YearMonth visibleMonth = month != null ? month : (date != null ? YearMonth.from(date) : YearMonth.now(ZONE));

        List<WeeklyHourRow> weeklyRows = weeklyRows(snapshot.rules());
        List<String> hourOptions = hourOptions();
        CalendarView calendar = calendar(selected, snapshot, schedule, date, visibleMonth, sportId, tab);
        List<SportOption> sports = sportOptions(id);
        ConditionsView conditions = conditions(id, sportId);
        List<ClosureRow> closures = closureRows(id);
        return new SchedulePage(selected, weeklyRows, hourOptions, calendar, sports, conditions,
                closures, normalizeTab(tab));
    }

    @Override
    @Transactional
    public void saveWeeklyHours(Long userId, WeeklyScheduleForm form) {
        long facilityId = requireManagedFacility(userId, form == null ? null : form.getFacilityId());
        List<OperatingRule> rows = parseWeeklyRows(form);
        requireFacilityForUpdate(facilityId);
        ScheduleSnapshot snapshot = scheduleLoader.snapshotForUpdate(facilityId);

        // 변경 후 주간 규칙 + 기존 날짜별 예외 + 적용 중 휴관으로 활성 방 전체를 다시 검사한다.
        FacilitySchedule candidate = FacilitySchedule.of(rows, snapshot.overrides(), snapshot.closures());
        List<MatchPeriod> conflicts = activeConflicts(facilityId, candidate);
        if (!conflicts.isEmpty()) {
            // 충돌 시 아무것도 쓰지 않는다. 기존 신청·매칭방을 자동 취소하지 않는다.
            throw new ScheduleConflictException(
                    "변경한 운영 시간에 포함되지 않는 모집 중·매칭 완료 매칭방이 있어 저장하지 않았습니다.",
                    conflicts, "주간 운영 시간");
        }

        scheduleMapper.deleteWeeklyRules(facilityId);
        scheduleMapper.insertWeeklyRules(facilityId, rows);
    }

    @Override
    @Transactional
    public void saveDateHours(Long userId, DateScheduleForm form) {
        long facilityId = requireManagedFacility(userId, form == null ? null : form.getFacilityId());
        LocalDate date = parseDate(form == null ? null : form.getDate());
        requireFacilityForUpdate(facilityId);
        ScheduleSnapshot snapshot = scheduleLoader.snapshotForUpdate(facilityId);
        FacilitySchedule current = snapshot.toSchedule();
        String note = validNote(form.getNote());

        List<Integer> requested = parseHours(form.getHours());
        // 휴관으로 비활성화되어 전송되지 않은 슬롯은 기존 기본 선택을 그대로 보존한다.
        List<Integer> hours = preserveBlockedHours(date, current, requested);

        FacilitySchedule candidate = FacilitySchedule.of(snapshot.rules(),
                replaceOverride(snapshot.overrides(), date, hours, note), snapshot.closures());
        List<MatchPeriod> conflicts = activeConflicts(facilityId, candidate);
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(
                    "변경한 날짜별 운영 시간에 포함되지 않는 모집 중·매칭 완료 매칭방이 있어 저장하지 않았습니다.",
                    conflicts, date.format(DATE_LABEL));
        }

        DateOverride existing = snapshot.findOverride(date);
        if (existing != null) {
            scheduleMapper.deleteDateOverrideSlots(existing.getOverrideId());
            scheduleMapper.deleteDateOverride(existing.getOverrideId());
        }
        DateOverride saved = DateOverride.of(date, hours, note);
        saved.setFacilityId(facilityId);
        scheduleMapper.insertDateOverride(saved);
        if (!hours.isEmpty()) {
            scheduleMapper.insertDateOverrideHours(saved.getOverrideId(), hours);
        }
    }

    @Override
    @Transactional
    public void resetDateHours(Long userId, Long facilityId, LocalDate date) {
        long id = requireManagedFacility(userId, facilityId);
        if (date == null) {
            throw new IllegalArgumentException("주간 기준으로 되돌릴 날짜를 선택해 주세요.");
        }
        requireFacilityForUpdate(id);
        ScheduleSnapshot snapshot = scheduleLoader.snapshotForUpdate(id);
        DateOverride existing = snapshot.findOverride(date);
        if (existing == null) {
            throw new IllegalArgumentException("선택한 날짜에는 날짜별 설정이 없습니다.");
        }
        FacilitySchedule candidate = FacilitySchedule.of(snapshot.rules(),
                replaceOverride(snapshot.overrides(), date, null, null), snapshot.closures());
        List<MatchPeriod> conflicts = activeConflicts(id, candidate);
        if (!conflicts.isEmpty()) {
            throw new ScheduleConflictException(
                    "주간 운영 시간으로 되돌리면 포함되지 않는 모집 중·매칭 완료 매칭방이 있어 저장하지 않았습니다.",
                    conflicts, date.format(DATE_LABEL));
        }

        scheduleMapper.deleteDateOverrideSlots(existing.getOverrideId());
        scheduleMapper.deleteDateOverride(existing.getOverrideId());
    }

    @Override
    @Transactional
    public void saveConditions(Long userId, ConditionsForm form) {
        long facilityId = requireManagedFacility(userId, form == null ? null : form.getFacilityId());
        Long sportId = form.getSportId();
        if (sportId == null) {
            throw new IllegalArgumentException("모집 조건을 설정할 종목을 선택해 주세요.");
        }
        if (form.getCapacity() == null || form.getCapacity() < MIN_CAPACITY
                || form.getCapacity() > MAX_CAPACITY) {
            throw new IllegalArgumentException("최대 참가 인원은 2~200명 사이로 입력해 주세요.");
        }
        String note = validNote(form.getNote());

        requireFacilityForUpdate(facilityId);
        Integer existingCapacity = scheduleMapper.selectSportCapacityForUpdate(facilityId, sportId);
        if (existingCapacity == null) {
            throw new IllegalArgumentException("선택한 시설에서 운영하지 않는 종목입니다.");
        }
        scheduleMapper.updateSportConditions(facilityId, sportId, form.getCapacity(), note);
    }

    @Override
    @Transactional
    public void registerClosure(Long userId, ClosureForm form) {
        long facilityId = requireManagedFacility(userId, form == null ? null : form.getFacilityId());
        LocalDate date = parseDate(form.getDate());
        LocalDateTime startAt;
        LocalDateTime endAt;
        try {
            LocalTime start = ScheduleTimes.parseStart(form.getStart(), "휴관 시작 시간");
            ScheduleTimes.EndTime end = ScheduleTimes.parseEnd(form.getEnd(), "휴관 종료 시간");
            startAt = date.atTime(start);
            boolean nextDay = end.nextDay() || end.time().isBefore(start);
            if (!end.nextDay() && end.time().equals(start)) {
                throw new IllegalArgumentException("휴관 종료 시간은 시작 시간과 달라야 합니다.");
            }
            endAt = nextDay ? date.plusDays(1).atTime(end.time()) : date.atTime(end.time());
        } catch (IllegalArgumentException exception) {
            throw exception;
        }
        if (form.getReason() == null || form.getReason().isBlank()) {
            throw new IllegalArgumentException("휴관 사유를 입력해 주세요.");
        }
        String reason = form.getReason().strip();
        if (reason.length() > 255) {
            throw new IllegalArgumentException("휴관 사유는 255자 이내로 입력해 주세요.");
        }

        requireFacilityForUpdate(facilityId);
        // 겹침 조건: 매칭 시작 < 휴관 종료 && 매칭 종료 > 휴관 시작. 겹치는 방을 전부 모아 경고한다.
        List<MatchPeriod> conflicts = scheduleMapper.selectOverlappingMatchPeriodsForUpdate(
                facilityId, OCCUPYING_STATUSES, startAt, endAt);
        if (conflicts != null && !conflicts.isEmpty()) {
            throw new ScheduleConflictException(
                    "입력한 휴관 기간과 겹치는 모집 중·매칭 완료 매칭방이 있어 휴관을 등록하지 않았습니다.",
                    conflicts, "휴관 " + date.format(DATE_LABEL) + " "
                            + ScheduleTimes.format(startAt.toLocalTime()) + " ~ "
                            + ScheduleTimes.format(endAt.toLocalTime())
                            + (endAt.toLocalDate().isAfter(date) ? " (다음 날)" : ""));
        }

        FacilityClosure closure = new FacilityClosure();
        closure.setFacilityId(facilityId);
        closure.setCreatedBy(userId);
        closure.setStartAt(startAt);
        closure.setEndAt(endAt);
        closure.setReason(reason);
        closure.setStatus(FacilityClosure.STATUS_ACTIVE);
        scheduleMapper.insertClosure(closure);
    }

    @Override
    @Transactional
    public void cancelClosure(Long userId, long facilityId, long scheduleChangeId) {
        // 템플릿은 취소 폼에 facilityId를 싣지 않는다. 서버가 대상 휴관에서 시설을 찾아
        // 그 시설의 담당자인지 다시 확인하므로 시설 ID를 신뢰하지 않는다.
        FacilityClosure target = scheduleMapper.selectClosureById(scheduleChangeId);
        if (target == null) {
            throw new IllegalArgumentException("취소할 수 있는 휴관 일정을 찾을 수 없습니다.");
        }
        long id = requireManagedFacility(userId,
                facilityId > 0 ? facilityId : target.getFacilityId());
        if (id != target.getFacilityId()) {
            throw new IllegalArgumentException("취소할 수 있는 휴관 일정을 찾을 수 없습니다.");
        }
        requireFacilityForUpdate(id);
        FacilityClosure closure = scheduleMapper.selectClosureForUpdate(id, scheduleChangeId);
        if (closure == null) {
            throw new IllegalArgumentException("취소할 수 있는 휴관 일정을 찾을 수 없습니다.");
        }
        int updated = scheduleMapper.cancelClosure(id, scheduleChangeId, FacilityClosure.STATUS_CANCELLED);
        if (updated == 0) {
            throw new IllegalArgumentException("취소할 수 있는 휴관 일정을 찾을 수 없습니다.");
        }
    }

    // ------------------------------------------------------------------
    // 조회 모델 구성
    // ------------------------------------------------------------------

    private CalendarView calendar(FacilityListItem facility, ScheduleSnapshot snapshot, FacilitySchedule schedule,
            LocalDate date, YearMonth month, Long sportId, String tab) {
        List<CalendarView.Day> days = calendarDays(facility, snapshot, schedule, date, month, sportId, tab);
        String monthLabel = month.getYear() + "년 " + month.getMonthValue() + "월";
        String prevUrl = calendarUrl(facility, month.minusMonths(1).atDay(1), sportId, tab);
        String nextUrl = calendarUrl(facility, month.plusMonths(1).atDay(1), sportId, tab);
        if (date == null) {
            return CalendarView.empty(monthLabel, prevUrl, nextUrl, days);
        }

        DateOverride override = snapshot.findOverride(date);
        List<Integer> baseHours = baseHours(schedule, date);
        // 최종 이용 가능 시간은 휴관까지 차감한 값이다. 기저 선택(baseHours)은 화면 체크 상태와
        // 휴관으로 비활성화된 슬롯 보존에 그대로 쓰고, 안내 문구만 실제 가능 시간으로 계산한다.
        List<Integer> openHours = schedule.availableHours(date);
        List<CalendarView.HourSlot> slots = new ArrayList<>();
        Set<Integer> selected = new LinkedHashSet<>(baseHours);
        for (int hour = 0; hour < 24; hour++) {
            String time = String.format("%02d:00", hour);
            boolean blocked = schedule.isClosureBlockedHour(date, hour);
            boolean isSelected = selected.contains(hour);
            slots.add(new CalendarView.HourSlot(time, isSelected, blocked, blocked,
                    blocked ? "휴관" : null,
                    blocked ? time + " ~ " + String.format("%02d:00", hour + 1) + " 휴관으로 선택 불가" : time));
        }
        boolean configured = schedule.isConfigured();
        String source = !configured
                ? "주간 운영 시간이 설정되지 않아 신규 신청이 차단됩니다."
                : (override == null ? "주간 운영 시간을 기준으로 표시합니다."
                        : (override.isClosed() ? "이 날짜는 휴무로 설정되어 있습니다."
                                : "이 날짜에만 적용되는 시간입니다."));
        String ranges = rangesText(baseHours);
        String availability = !configured
                ? "주간 운영 시간이 설정되지 않아 신규 신청이 차단됩니다."
                : (override != null && override.isClosed()
                        ? "이 날짜는 종일 예약이 불가능합니다."
                        : "예약 가능 " + openHours.size() + "시간");
        return new CalendarView(monthLabel, prevUrl, nextUrl, days, date.format(DATE_VALUE),
                date.format(DATE_LABEL), source, baseHours.size(), slots, ranges,
                override == null ? null : override.getNote(), availability);
    }

    private List<CalendarView.Day> calendarDays(FacilityListItem facility, ScheduleSnapshot snapshot,
            FacilitySchedule schedule, LocalDate selected, YearMonth month, Long sportId, String tab) {
        LocalDate first = month.atDay(1);
        LocalDate gridStart = first.minusDays(first.getDayOfWeek().getValue() % 7);
        List<CalendarView.Day> days = new ArrayList<>(42);
        for (int index = 0; index < 42; index++) {
            LocalDate day = gridStart.plusDays(index);
            DateOverride override = snapshot.findOverride(day);
            String customLabel = override != null && !override.isClosed() ? "시간 지정" : null;
            String closedLabel = override != null && override.isClosed() ? "휴관" : null;
            boolean blocked = closureOverlapsDate(snapshot.closures(), day);
            String aria = day.format(DATE_LABEL)
                    + (customLabel != null ? ", 날짜별 시간 지정" : "")
                    + (closedLabel != null ? ", 휴관" : "");
            days.add(new CalendarView.Day(day.getDayOfMonth(), calendarUrl(facility, day, sportId, tab), aria,
                    day.equals(selected), day.getMonthValue() != month.getMonthValue(), blocked,
                    customLabel, closedLabel));
        }
        return days;
    }

    private String calendarUrl(FacilityListItem facility, LocalDate date, Long sportId, String tab) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/facility")
                .queryParam("view", "schedule")
                .queryParam("facilityId", facility.getFacilityId())
                .queryParam("date", date.format(DATE_VALUE));
        if (sportId != null) {
            builder.queryParam("sportId", sportId);
        }
        String normalized = normalizeTab(tab);
        if (normalized != null) {
            builder.queryParam("tab", normalized);
        }
        return builder.build().encode().toUriString();
    }

    private List<WeeklyHourRow> weeklyRows(List<OperatingRule> rules) {
        Map<Integer, OperatingRule> byDay = new LinkedHashMap<>();
        for (OperatingRule rule : rules) {
            byDay.put(rule.getWeekday(), rule);
        }
        List<WeeklyHourRow> rows = new ArrayList<>(7);
        for (int day = 1; day <= 7; day++) {
            OperatingRule rule = byDay.get(day);
            if (rule == null) {
                // 미설정 시설은 편집을 시작할 수 있도록 기본 시간대를 보여 주되 휴무로 표시한다.
                rows.add(WeeklyHourRow.of(day, false, "06:00", "22:00"));
                continue;
            }
            String start = ScheduleTimes.format(rule.getOpenTime());
            // "24:00"은 다음 날 자정 종료일 때만 쓴다. 22:00~다음 날 06:00 같은 야간 운영은
            // 실제 종료 시각을 보여 줘야 변경 없이 다시 저장해도 같은 구간이 유지된다.
            String end = isMidnightNextDay(rule) ? "24:00" : ScheduleTimes.format(rule.getCloseTime());
            rows.add(WeeklyHourRow.of(day, rule.isOpen(), start, end));
        }
        return rows;
    }

    /**
     * 종료 시각이 다음 날 자정({@code 24:00})인지 확인한다.
     *
     * <p>폼의 {@code 24:00} 표기는 다음 날 00:00 종료만 뜻하므로, 06:00처럼 자정이 아닌
     * 익일 종료는 실제 시각으로 표시해야 저장 왕복에서 값이 바뀌지 않는다.</p>
     */
    private static boolean isMidnightNextDay(OperatingRule rule) {
        return rule.isEndNextDay() && LocalTime.MIDNIGHT.equals(rule.getCloseTime());
    }

    private List<String> hourOptions() {
        List<String> options = new ArrayList<>(25);
        for (int hour = 0; hour <= 23; hour++) {
            options.add(String.format("%02d:00", hour));
        }
        options.add("24:00");
        return options;
    }

    private List<SportOption> sportOptions(long facilityId) {
        List<FacilitySportCondition> conditions = scheduleMapper.selectSportConditions(facilityId);
        List<SportOption> options = new ArrayList<>();
        if (conditions == null) {
            return options;
        }
        for (FacilitySportCondition condition : conditions) {
            options.add(new SportOption(condition.getSportId(), condition.getSportName()));
        }
        return options;
    }

    private ConditionsView conditions(long facilityId, Long sportId) {
        List<FacilitySportCondition> conditions = scheduleMapper.selectSportConditions(facilityId);
        if (conditions == null || conditions.isEmpty()) {
            return ConditionsView.empty();
        }
        FacilitySportCondition selected = conditions.get(0);
        if (sportId != null) {
            for (FacilitySportCondition condition : conditions) {
                if (sportId.equals(condition.getSportId())) {
                    selected = condition;
                    break;
                }
            }
        }
        return new ConditionsView(selected.getSportId(), selected.getCapacity(), selected.getNote());
    }

    private List<ClosureRow> closureRows(long facilityId) {
        String facilityName = scheduleMapper.selectFacilityName(facilityId);
        List<FacilityClosure> closures = scheduleMapper.selectClosures(facilityId);
        List<ClosureRow> rows = new ArrayList<>();
        if (closures == null) {
            return rows;
        }
        for (FacilityClosure closure : closures) {
            rows.add(ClosureRow.from(closure, facilityName == null ? "" : facilityName));
        }
        return rows;
    }

    // ------------------------------------------------------------------
    // 검증 보조
    // ------------------------------------------------------------------

    /** 요일별 폼을 7행 규칙으로 해석한다. 휴무 행은 시간 값을 00:00으로 보관한다. */
    private List<OperatingRule> parseWeeklyRows(WeeklyScheduleForm form) {
        List<OperatingRule> rows = new ArrayList<>(7);
        for (int day = 1; day <= 7; day++) {
            if (!form.isOpen(day)) {
                rows.add(OperatingRule.closed(day));
                continue;
            }
            String startValue = form.getStartTimes() == null ? null : form.getStartTimes().get(day);
            String endValue = form.getEndTimes() == null ? null : form.getEndTimes().get(day);
            LocalTime start = ScheduleTimes.parseStart(startValue, dayLabel(day) + " 시작 시간");
            ScheduleTimes.EndTime end = ScheduleTimes.parseEnd(endValue, dayLabel(day) + " 종료 시간");
            if (!end.nextDay() && end.time().equals(start)) {
                throw new IllegalArgumentException(dayLabel(day) + " 시작 시간과 종료 시간이 같습니다. "
                        + "24시간 운영은 00:00부터 24:00까지로 설정해 주세요.");
            }
            boolean nextDay = end.nextDay() || end.time().isBefore(start);
            rows.add(OperatingRule.open(day, start, end.time(), nextDay));
        }
        return rows;
    }

    private static String dayLabel(int day) {
        return switch (day) {
            case 1 -> "월요일";
            case 2 -> "화요일";
            case 3 -> "수요일";
            case 4 -> "목요일";
            case 5 -> "금요일";
            case 6 -> "토요일";
            case 7 -> "일요일";
            default -> day + "요일";
        };
    }

    private List<Integer> parseHours(List<String> values) {
        Set<Integer> hours = new LinkedHashSet<>();
        if (values == null) {
            return List.of();
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            hours.add(ScheduleTimes.toHour(value, "이용 시작 시간"));
        }
        List<Integer> sorted = new ArrayList<>(hours);
        sorted.sort(Integer::compareTo);
        return List.copyOf(sorted);
    }

    /**
     * 휴관으로 차단되어 폼에서 전송되지 않은 슬롯의 기존 기본 선택을 보존한다.
     * 차단되지 않은 슬롯은 제출 값을 그대로 쓴다.
     */
    private List<Integer> preserveBlockedHours(LocalDate date, FacilitySchedule current,
            List<Integer> requested) {
        Set<Integer> resolved = new LinkedHashSet<>(requested);
        List<Integer> base = baseHours(current, date);
        for (int hour = 0; hour < 24; hour++) {
            if (!current.isClosureBlockedHour(date, hour)) {
                continue;
            }
            if (base.contains(hour)) {
                resolved.add(hour);
            } else {
                resolved.remove(hour);
            }
        }
        List<Integer> sorted = new ArrayList<>(resolved);
        sorted.sort(Integer::compareTo);
        return List.copyOf(sorted);
    }

    /** 주간 규칙 또는 기존 예외만 반영한 슬롯(휴관 차감 전)이다. */
    private List<Integer> baseHours(FacilitySchedule schedule, LocalDate date) {
        List<Integer> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            if (schedule.isBaseHourOpen(date, hour)) {
                hours.add(hour);
            }
        }
        return hours;
    }

    /** 활성 매칭방 전체가 후보 일정에 온전히 포함되는지 확인하고, 실패한 방을 모두 모은다. */
    private List<MatchPeriod> activeConflicts(long facilityId, FacilitySchedule candidate) {
        List<MatchPeriod> periods = scheduleMapper.selectMatchPeriodsForUpdate(facilityId, OCCUPYING_STATUSES);
        List<MatchPeriod> conflicts = new ArrayList<>();
        if (periods == null) {
            return conflicts;
        }
        for (MatchPeriod period : periods) {
            if (period == null || period.getStartAt() == null || period.getEndAt() == null) {
                continue;
            }
            if (!candidate.covers(period.getStartAt(), period.getEndAt())) {
                conflicts.add(period);
            }
        }
        return conflicts;
    }

    private List<DateOverride> replaceOverride(List<DateOverride> current, LocalDate date, List<Integer> hours,
            String note) {
        List<DateOverride> result = new ArrayList<>();
        for (DateOverride override : current) {
            if (!date.equals(override.getDate())) {
                result.add(override);
            }
        }
        if (hours != null) {
            result.add(DateOverride.of(date, hours, note));
        }
        return result;
    }

    private boolean closureOverlapsDate(List<FacilityClosure> closures, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        for (FacilityClosure closure : closures) {
            if (closure.overlaps(dayStart, dayEnd)) {
                return true;
            }
        }
        return false;
    }

    private String rangesText(List<Integer> hours) {
        if (hours == null || hours.isEmpty()) {
            return "선택된 시간이 없습니다.";
        }
        List<String> ranges = new ArrayList<>();
        int start = hours.get(0);
        int previous = start;
        for (int index = 1; index < hours.size(); index++) {
            int hour = hours.get(index);
            if (hour == previous + 1) {
                previous = hour;
                continue;
            }
            ranges.add(range(start, previous));
            start = hour;
            previous = hour;
        }
        ranges.add(range(start, previous));
        return String.join(", ", ranges);
    }

    private static String range(int startHour, int endHour) {
        return String.format("%02d:00–%02d:00", startHour, endHour + 1);
    }

    // ------------------------------------------------------------------
    // 소유권·입력 검증
    // ------------------------------------------------------------------

    private FacilityListItem findManagedFacility(Long userId, Long facilityId) {
        if (userId == null || facilityId == null) {
            return null;
        }
        for (FacilityListItem item : facilityService.findManagedFacilities(userId)) {
            if (facilityId.equals(item.getFacilityId())) {
                return item;
            }
        }
        return null;
    }

    private long requireManagedFacility(Long userId, Long facilityId) {
        if (facilityId == null) {
            throw new IllegalArgumentException("운영 일정을 설정할 시설을 선택해 주세요.");
        }
        if (userId == null || !facilityMapper.existsFacilityManager(facilityId, userId)) {
            throw new FacilityAccessDeniedException("담당 시설만 관리할 수 있습니다.");
        }
        return facilityId;
    }

    /** 시설 행을 잠근다. 없거나 삭제됐으면 안내 예외를 던진다. */
    private void requireFacilityForUpdate(long facilityId) {
        if (scheduleMapper.selectFacilityNameForUpdate(facilityId) == null) {
            throw new FacilityNotFoundException("운영 일정을 설정할 시설을 찾을 수 없습니다.");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("날짜를 선택해 주세요.");
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("날짜 형식이 올바르지 않습니다. 예: 2026-11-02", exception);
        }
    }

    private String validNote(String note) {
        if (note == null) {
            return null;
        }
        String trimmed = note.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("안내 문구는 " + MAX_NOTE_LENGTH + "자 이내로 입력해 주세요.");
        }
        return trimmed;
    }

    private static String normalizeTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return null;
        }
        return switch (tab.strip()) {
            case "schedule-hours", "schedule-conditions", "schedule-changes" -> tab.strip();
            default -> null;
        };
    }
}
