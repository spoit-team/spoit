package co.kr.spoit.facility.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주간 운영 규칙·날짜별 예외·적용 중인 휴관을 합쳐 실제 이용 가능 시간을 계산한다.
 *
 * <p>계산 규칙은 확정 정책을 그대로 따른다.</p>
 * <ul>
 *   <li>모든 구간은 시작을 포함하고 종료를 포함하지 않는 {@code [시작, 종료)}이며 1시간 단위다.</li>
 *   <li>주간 야간 운영을 자연일 기준으로 먼저 쪼갠 뒤, 그 날짜의 예외가 있으면 그 날짜 전체를
 *       예외 슬롯으로 대체한다. 이후 적용 중인 휴관 구간을 차감한다.</li>
 *   <li>예외 헤더는 있는데 슬롯이 0개면 그 날짜 전체가 휴무다. 예외 자체가 없으면 주간 규칙을
 *       상속한다.</li>
 *   <li>주간 규칙이 하나도 없으면 미설정이며 신규 예약을 막는다. 7행 전체 휴무는 설정된 상태로
 *       보되 이용 가능 시간이 없다.</li>
 * </ul>
 */
public class FacilitySchedule {

    private final Map<Integer, OperatingRule> weekly;
    private final Map<LocalDate, DateOverride> overrides;
    private final List<FacilityClosure> closures;

    private FacilitySchedule(Map<Integer, OperatingRule> weekly, Map<LocalDate, DateOverride> overrides,
            List<FacilityClosure> closures) {
        this.weekly = weekly;
        this.overrides = overrides;
        this.closures = closures;
    }

    public static FacilitySchedule of(List<OperatingRule> rules, List<DateOverride> overrides,
            List<FacilityClosure> closures) {
        Map<Integer, OperatingRule> weekly = new HashMap<>();
        if (rules != null) {
            for (OperatingRule rule : rules) {
                if (rule != null) {
                    weekly.put(rule.getWeekday(), rule);
                }
            }
        }
        Map<LocalDate, DateOverride> byDate = new HashMap<>();
        if (overrides != null) {
            for (DateOverride override : overrides) {
                if (override != null && override.getDate() != null) {
                    byDate.put(override.getDate(), override);
                }
            }
        }
        List<FacilityClosure> active = new ArrayList<>();
        if (closures != null) {
            for (FacilityClosure closure : closures) {
                if (closure != null && closure.isActive()) {
                    active.add(closure);
                }
            }
        }
        return new FacilitySchedule(Map.copyOf(weekly), Map.copyOf(byDate), List.copyOf(active));
    }

    /** 주간 규칙이 한 행이라도 있으면 설정된 상태다. 미설정은 신규 예약을 막는다. */
    public boolean isConfigured() {
        return !weekly.isEmpty();
    }

    public boolean hasOverride(LocalDate date) {
        return overrides.containsKey(date);
    }

    public DateOverride findOverride(LocalDate date) {
        return overrides.get(date);
    }

    /**
     * 주간 규칙 또는 날짜별 예외만 반영한 슬롯이다(휴관 차감 전). 화면에서 휴관으로 비활성화된
     * 슬롯의 기존 선택을 보존할 때 쓴다.
     *
     * <p>설정 여부와 무관하게 기저 슬롯을 그대로 돌려준다. 미설정 시설의 예외 슬롯도 화면에서
     * 선택 상태로 남겨 두고, 실제 예약 가능 여부만 {@link #covers}·{@link #availableHours}가
     * 판단하게 하기 위함이다.</p>
     */
    public boolean isBaseHourOpen(LocalDate date, int hour) {
        return baseMask(date)[hour];
    }

    /** 해당 자연일에서 휴관이 겹쳐 차단된 1시간 슬롯인지 확인한다. */
    public boolean isClosureBlockedHour(LocalDate date, int hour) {
        LocalDateTime slotStart = date.atTime(hour, 0);
        LocalDateTime slotEnd = slotStart.plusHours(1);
        for (FacilityClosure closure : closures) {
            if (closure.overlaps(slotStart, slotEnd)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 해당 자연일의 최종 이용 가능 1시간 슬롯(휴관 차감 후)이다.
     *
     * <p>주간 규칙이 미설정이면 날짜별 예외가 있어도 예약할 수 없으므로 빈 목록이다.</p>
     */
    public List<Integer> availableHours(LocalDate date) {
        if (!isConfigured()) {
            return List.of();
        }
        boolean[] mask = baseMask(date);
        List<Integer> hours = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            if (mask[hour] && !isClosureBlockedHour(date, hour)) {
                hours.add(hour);
            }
        }
        return List.copyOf(hours);
    }

    /**
     * 해당 자연일의 최종 이용 가능 구간을 병합해 돌려준다.
     *
     * <p>주간 규칙이 미설정이면 날짜별 예외가 있어도 예약할 수 없으므로 빈 목록이다.</p>
     */
    public List<Interval> availableIntervals(LocalDate date) {
        if (!isConfigured()) {
            return List.of();
        }
        return intervalsOf(date);
    }

    /**
     * 요청 구간 전체가 최종 이용 가능 시간에 포함되는지 확인한다.
     *
     * <p>자정을 넘는 구간도 날짜별로 나누어 계산하고, 연속된 구간이 이어지는지 하루씩 전진하며
     * 확인한다. 요청 구간이 여러 날이어도 하루치(24슬롯)만 계산하므로 메모리가 늘지 않는다.</p>
     *
     * <p>주간 규칙이 미설정이면 날짜별 예외가 있어도 예약할 수 없으므로 항상 {@code false}다.
     * 신규 신청 차단({@code requireAvailable})과 저장 후보 검사가 같은 판정을 쓰게 하기 위함이다.</p>
     */
    public boolean covers(LocalDateTime start, LocalDateTime end) {
        if (!isConfigured() || start == null || end == null || !start.isBefore(end)) {
            return false;
        }
        LocalDateTime cursor = start;
        LocalDate lastDate = end.toLocalDate();
        for (LocalDate date = start.toLocalDate(); !date.isAfter(lastDate); date = date.plusDays(1)) {
            boolean progressed = false;
            for (Interval interval : intervalsOf(date)) {
                if (!interval.end().isAfter(cursor)) {
                    // 이미 지나간 구간이다.
                    continue;
                }
                if (interval.start().isAfter(cursor)) {
                    // 현재 위치와 이어지지 않는 빈 시간이 있다.
                    return false;
                }
                cursor = interval.end();
                progressed = true;
                if (!cursor.isBefore(end)) {
                    return true;
                }
            }
            if (!progressed) {
                // 이 날짜에 남은 이용 가능 시간이 없어 구간이 끊긴다.
                return false;
            }
        }
        return false;
    }

    private boolean[] baseMask(LocalDate date) {
        boolean[] mask = new boolean[24];
        DateOverride override = overrides.get(date);
        if (override != null) {
            for (int hour : override.getHours()) {
                if (hour >= 0 && hour < 24) {
                    mask[hour] = true;
                }
            }
            return mask;
        }
        applyWeekly(mask, date);
        return mask;
    }

    private void applyWeekly(boolean[] mask, LocalDate date) {
        OperatingRule today = weekly.get(date.getDayOfWeek().getValue());
        if (today != null && today.isOpen()) {
            int openMinutes = minutes(today.getOpenTime());
            int closeMinutes = minutes(today.getCloseTime());
            // 종료가 시작보다 이르거나 자정 넘김으로 표시된 경우는 다음 날까지 이어진다.
            boolean nextDay = today.isEndNextDay() || closeMinutes < openMinutes;
            int endMinutes = nextDay ? 24 * 60 : closeMinutes;
            mark(mask, openMinutes, endMinutes);
        }
        OperatingRule previous = weekly.get(date.minusDays(1).getDayOfWeek().getValue());
        if (previous != null && previous.isOpen()) {
            int openMinutes = minutes(previous.getOpenTime());
            int closeMinutes = minutes(previous.getCloseTime());
            boolean nextDay = previous.isEndNextDay() || closeMinutes < openMinutes;
            if (nextDay) {
                mark(mask, 0, closeMinutes);
            }
        }
    }

    private List<Interval> intervalsOf(LocalDate date) {
        boolean[] mask = baseMask(date);
        List<Interval> intervals = new ArrayList<>();
        LocalDateTime runStart = null;
        for (int hour = 0; hour < 24; hour++) {
            boolean open = mask[hour] && !isClosureBlockedHour(date, hour);
            if (open && runStart == null) {
                runStart = date.atTime(hour, 0);
            } else if (!open && runStart != null) {
                intervals.add(new Interval(runStart, date.atTime(hour, 0)));
                runStart = null;
            }
        }
        if (runStart != null) {
            intervals.add(new Interval(runStart, date.plusDays(1).atStartOfDay()));
        }
        return List.copyOf(intervals);
    }

    private static void mark(boolean[] mask, int fromMinutes, int toMinutes) {
        for (int hour = 0; hour < 24; hour++) {
            int slotStart = hour * 60;
            int slotEnd = slotStart + 60;
            if (slotStart >= fromMinutes && slotEnd <= toMinutes) {
                mask[hour] = true;
            }
        }
    }

    private static int minutes(LocalTime time) {
        return time == null ? 0 : time.getHour() * 60 + time.getMinute();
    }

    /** 시작을 포함하고 종료를 포함하지 않는 이용 가능 구간이다. */
    public record Interval(LocalDateTime start, LocalDateTime end) {
    }
}
