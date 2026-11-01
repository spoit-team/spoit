package co.kr.spoit.facility.dto;

import java.util.List;

import lombok.Getter;

/**
 * 날짜별 예약 시간 편집 화면 모델이다.
 *
 * <p>달력 링크와 시간 슬롯은 모두 서버가 완성한 값이다. 화면은 계산 없이 그대로 렌더링하며,
 * 선택 날짜가 없으면 {@code null} 대신 선택 안내 문구만 담은 기본값을 쓴다.</p>
 */
@Getter
public class CalendarView {

    private final String monthLabel;
    private final String prevUrl;
    private final String nextUrl;
    private final List<Day> days;
    private final String date;
    private final String dateLabel;
    private final String sourceLabel;
    private final int selectedCount;
    private final List<HourSlot> hourSlots;
    private final String rangesText;
    private final String note;
    private final String availabilityText;

    public CalendarView(String monthLabel, String prevUrl, String nextUrl, List<Day> days,
            String date, String dateLabel, String sourceLabel, int selectedCount,
            List<HourSlot> hourSlots, String rangesText, String note, String availabilityText) {
        this.monthLabel = monthLabel;
        this.prevUrl = prevUrl;
        this.nextUrl = nextUrl;
        this.days = List.copyOf(days);
        this.date = date;
        this.dateLabel = dateLabel;
        this.sourceLabel = sourceLabel;
        this.selectedCount = selectedCount;
        this.hourSlots = List.copyOf(hourSlots);
        this.rangesText = rangesText;
        this.note = note;
        this.availabilityText = availabilityText;
    }

    /** 선택 날짜가 없을 때 달력만 보여 주는 기본값이다. */
    public static CalendarView empty(String monthLabel, String prevUrl, String nextUrl, List<Day> days) {
        return new CalendarView(monthLabel, prevUrl, nextUrl, days, null, null,
                "주간 운영 시간을 기준으로 표시합니다.", 0, List.of(), "선택된 시간이 없습니다.", null, null);
    }

    /** 달력의 하루 칸이다. */
    @Getter
    public static class Day {
        private final int day;
        private final String url;
        private final String ariaLabel;
        private final boolean selected;
        private final boolean outsideMonth;
        private final boolean blocked;
        private final String customLabel;
        private final String closedLabel;

        public Day(int day, String url, String ariaLabel, boolean selected, boolean outsideMonth,
                boolean blocked, String customLabel, String closedLabel) {
            this.day = day;
            this.url = url;
            this.ariaLabel = ariaLabel;
            this.selected = selected;
            this.outsideMonth = outsideMonth;
            this.blocked = blocked;
            this.customLabel = customLabel;
            this.closedLabel = closedLabel;
        }
    }

    /**
     * 1시간 선택 칸이다.
     *
     * <p>{@code blocked}는 휴관이 겹쳐 차단된 칸이며, 이때 {@code disabled}가 참이 되어
     * 폼에서 전송되지 않는다. 서버는 전송되지 않은 차단 칸의 기존 선택을 보존해야 한다.</p>
     */
    @Getter
    public static class HourSlot {
        private final String time;
        private final boolean selected;
        private final boolean disabled;
        private final boolean blocked;
        private final String blockedLabel;
        private final String title;

        public HourSlot(String time, boolean selected, boolean disabled, boolean blocked,
                String blockedLabel, String title) {
            this.time = time;
            this.selected = selected;
            this.disabled = disabled;
            this.blocked = blocked;
            this.blockedLabel = blockedLabel;
            this.title = title;
        }
    }
}
