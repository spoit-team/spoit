package co.kr.spoit.preview.member;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Preview fixture backing the my page screen.
 */
@Getter
@Builder
public class MyPageDto {

    private final MemberView member;
    private final SummaryView summary;
    private final List<OtherSportView> otherSports;
    private final List<SportView> sports;
    private final String selectedSport;
    private final int historyTotalCount;
    private final String historyFilterLabel;
    private final int historyCount;
    private final List<HistoryItemView> history;
    private final String historyEmptyNote;
    private final List<ReservationView> currentReservations;
    private final List<ReservationView> pastReservations;
    private final List<NotificationView> notifications;
    private final String message;

    @Getter
    @Builder
    public static class MemberView {

        private final String initial;
        private final String nickname;
        private final String roleLabel;
        private final String email;
        private final String regionText;
    }

    @Getter
    @Builder
    public static class SummaryView {

        private final String representativeRatingText;
        private final Long representativeSportId;
        private final String representativeSportName;
        private final String skillNote;
        private final int confirmedCount;
        private final int completedCount;
        private final int waitingCount;
        private final String otherSportsNote;
    }

    @Getter
    @Builder
    public static class OtherSportView {

        private final Long sportId;
        private final String name;
        private final String ratingText;
        private final String note;
    }

    @Getter
    @Builder
    public static class SportView {

        private final Long sportId;
        private final boolean selected;
        private final String name;
        private final int count;
        private final String note;
        private final String grade;
        private final String caption;
        private final boolean hasAverage;
        private final int meterPercent;
    }

    @Getter
    @Builder
    public static class HistoryItemView {

        private final Long id;
        private final String date;
        private final String title;
        private final String sportName;
        private final String place;
        private final String applicationType;
        private final boolean statusComplete;
        private final String statusLabel;
        private final String ratingText;
        private final boolean hasRating;
        private final int ratingPercent;
        private final boolean hasTeamAverages;
        private final String myTeam;
        private final String averageAText;
        private final String averageBText;
        private final List<HistoryFactView> facts;
        private final String note;
    }

    @Getter
    @Builder
    public static class HistoryFactView {

        private final String label;
        private final String value;
    }

    @Getter
    @Builder
    public static class ReservationView {

        private final Long applicationId;
        private final String sportName;
        private final String typeLabel;
        private final String stateClass;
        private final String stateLabel;
        private final String title;
        private final String metaLine;
        private final String seatLine;
        private final String statusNote;
        private final boolean canViewPaymentGuidance;
        private final String paymentGuidanceUrl;
        private final boolean canCancel;
        private final String cancelLabel;
    }

    @Getter
    @Builder
    public static class NotificationView {

        private final Long notificationId;
        private final boolean read;
        private final String type;
        private final String title;
        private final String body;
        private final String time;
        private final String href;
    }
}
