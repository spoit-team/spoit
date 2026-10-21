package co.kr.spoit.preview.admin;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class FacilityPreviewModels {

    private FacilityPreviewModels() {
    }

    @Getter
    @AllArgsConstructor
    public static class FacilityDashboardView {
        private final int matchCount;
        private final int recruitingCount;
        private final int openRequestCount;
        private final int maintenanceCount;
        private final List<HourlyBarView> hourlyBars;
        private final List<RecentRequestView> recentRequests;
        private final List<MatchRowView> recentMatches;
    }

    @Getter
    @AllArgsConstructor
    public static class HourlyBarView {
        private final int matchHeight;
        private final int completedHeight;
        private final String label;
    }

    @Getter
    @AllArgsConstructor
    public static class RecentRequestView {
        private final String tone;
        private final String title;
        private final String meta;
        private final String time;
    }

    @Getter
    @AllArgsConstructor
    public static class MatchRowView {
        private final Long matchId;
        private final String title;
        private final String facilityName;
        private final String sportName;
        private final String participation;
        private final String dateLabel;
        private final String statusTone;
        private final String statusLabel;
    }

    @Getter
    @AllArgsConstructor
    public static class FacilityRowView {
        private final Long facilityId;
        private final String name;
        private final String address;
        private final String sportsLabel;
        private final int capacity;
        private final int concurrentMatchLimit;
        private final String statusTone;
        private final String statusLabel;
    }

    @Getter
    @AllArgsConstructor
    public static class WeeklyHourView {
        private final String day;
        private final String dayLabel;
        private final boolean open;
        private final String start;
        private final String end;
    }

    @Getter
    @AllArgsConstructor
    public static class CalendarView {
        private final String prevUrl;
        private final String nextUrl;
        private final String monthLabel;
        private final String date;
        private final String dateLabel;
        private final String sourceLabel;
        private final int selectedCount;
        private final String rangesText;
        private final String note;
        private final String availabilityText;
        private final List<CalendarDayView> days;
        private final List<HourSlotView> hourSlots;
    }

    @Getter
    @AllArgsConstructor
    public static class CalendarDayView {
        private final int day;
        private final String url;
        private final String ariaLabel;
        private final boolean outsideMonth;
        private final boolean selected;
        private final boolean blocked;
        private final String customLabel;
        private final String closedLabel;
    }

    @Getter
    @AllArgsConstructor
    public static class HourSlotView {
        private final String time;
        private final String title;
        private final boolean selected;
        private final boolean blocked;
        private final boolean disabled;
        private final String blockedLabel;
    }

    @Getter
    @AllArgsConstructor
    public static class SportView {
        private final Long sportId;
        private final String name;
    }

    @Getter
    @AllArgsConstructor
    public static class ScheduleConditionView {
        private final Long sportId;
        private final int capacity;
        private final int deadline;
        private final String note;
    }

    @Getter
    @AllArgsConstructor
    public static class ClosureView {
        private final Long scheduleChangeId;
        private final String dateLabel;
        private final String facilityName;
        private final String timeRange;
        private final String reason;
        private final String statusTone;
        private final String statusLabel;
    }

    @Getter
    @AllArgsConstructor
    public static class BookingFiltersView {
        private final String q;
        private final String status;
        private final String sportId;
    }

    @Getter
    @AllArgsConstructor
    public static class FacilityStatsView {
        private final int period;
        private final int bookingCount;
        private final int participantCount;
        private final String popularDay;
        private final String peakTime;
        private final String periodLabel;
        private final List<WeekdayStatView> weekdays;
        private final List<PopularHourView> popularHours;
    }

    @Getter
    @AllArgsConstructor
    public static class WeekdayStatView {
        private final String label;
        private final String tone;
        private final int percent;
    }

    @Getter
    @AllArgsConstructor
    public static class PopularHourView {
        private final String label;
        private final int count;
        private final int height;
    }

    @Getter
    @AllArgsConstructor
    public static class RequestMetricsView {
        private final int openCount;
        private final int inProgressCount;
        private final int completedCount;
        private final String averageResponse;
    }

    @Getter
    @AllArgsConstructor
    public static class RequestFiltersView {
        private final String q;
        private final String status;
    }

    @Getter
    @AllArgsConstructor
    public static class FacilityRequestView {
        private final Long id;
        private final String summary;
        private final String requester;
        private final String receivedAt;
        private final String owner;
        private final String status;
        private final String statusTone;
        private final String statusLabel;
    }

    @Getter
    @AllArgsConstructor
    public static class MaintenanceMetricsView {
        private final int scheduledCount;
        private final int inProgressCount;
        private final int completedCount;
        private final int actionNeededCount;
    }

    @Getter
    @AllArgsConstructor
    public static class MaintenanceFiltersView {
        private final String status;
    }

    @Getter
    @AllArgsConstructor
    public static class MaintenanceEntryView {
        private final String dateLabel;
        private final String area;
        private final String category;
        private final String owner;
        private final String statusTone;
        private final String statusLabel;
        private final String result;
    }

    @Getter
    @AllArgsConstructor
    public static class NoticeView {
        private final String title;
        private final String statusTone;
        private final String statusLabel;
        private final String periodLabel;
        private final String author;
        private final String updatedAt;
    }

    @Getter
    @AllArgsConstructor
    public static class RuleView {
        private final String title;
        private final String category;
        private final String statusTone;
        private final String statusLabel;
        private final String updatedAt;
    }

    @Getter
    @AllArgsConstructor
    public static class RoomHourSlotView {
        private final String time;
        private final boolean disabled;
        private final String label;
    }
}
