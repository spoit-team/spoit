package co.kr.spoit.facility.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 시설 전체의 시간대별 동시 점유 수를 계산한다.
 *
 * <p>대관 가능 구역 수는 종목과 무관하게 시설 전체에 적용하므로, 각 시간 구간에서 동시에
 * 이용하는 매칭방 수의 최대값을 구한다. 단순 합계가 아니다. 경계가 맞닿은 구간을
 * 과대 계산하지 않도록 같은 시각에서는 종료 이벤트를 시작 이벤트보다 먼저 처리한다.</p>
 */
public final class FacilityOccupancy {

    private FacilityOccupancy() {
    }

    /** 주어진 구간들 전체에서 동시 점유의 최대값이다. */
    public static int peak(List<MatchPeriod> periods) {
        if (periods == null || periods.isEmpty()) {
            return 0;
        }
        List<Event> events = new ArrayList<>(periods.size() * 2);
        for (MatchPeriod period : periods) {
            if (period == null || period.getStartAt() == null || period.getEndAt() == null
                    || !period.getStartAt().isBefore(period.getEndAt())) {
                continue;
            }
            events.add(new Event(period.getStartAt(), 1));
            events.add(new Event(period.getEndAt(), -1));
        }
        events.sort(Comparator.comparing(Event::at).thenComparingInt(Event::delta));
        int current = 0;
        int peak = 0;
        for (Event event : events) {
            current += event.delta();
            peak = Math.max(peak, current);
        }
        return peak;
    }

    /**
     * 대상 구간과 겹치는 점유만 골라 동시 점유의 최대값을 구한다.
     * 겹침 조건은 {@code 시작 < 대상 종료 && 종료 > 대상 시작}이며 경계가 맞닿으면 겹치지 않는다.
     */
    public static int peakWithin(List<MatchPeriod> periods, LocalDateTime start, LocalDateTime end) {
        if (periods == null || start == null || end == null || !start.isBefore(end)) {
            return 0;
        }
        List<MatchPeriod> overlapping = new ArrayList<>();
        for (MatchPeriod period : periods) {
            if (period != null && period.getStartAt() != null && period.getEndAt() != null
                    && period.getStartAt().isBefore(end) && period.getEndAt().isAfter(start)) {
                overlapping.add(period);
            }
        }
        return peak(overlapping);
    }

    private record Event(LocalDateTime at, int delta) {
    }
}
