package co.kr.spoit.preview.match;

import java.util.List;

import co.kr.spoit.preview.match.MatchCommonModels.Person;
import lombok.Builder;
import lombok.Getter;

/**
 * Preview-only match join view models.
 */
public final class MatchJoinModels {

    private MatchJoinModels() {
    }

    @Getter
    @Builder
    public static class MatchJoinPage {

        private final Long matchId;
        private final boolean completed;
        private final String unavailableMessage;
        private final JoinMatchView match;
        private final JoinSelectionView selection;
        private final boolean canReserve;
        private final String capacityNotice;
        private final String error;
        private final String feeTotalText;
        private final String feeSummaryNote;
        private final JoinCompletionView completion;
    }

    @Getter
    @Builder
    public static class JoinMatchView {

        private final Long matchId;
        private final String sportName;
        private final String statusLabel;
        private final String title;
        private final String facilityName;
        private final String dateLabel;
        private final String time;
        private final String place;
        private final String levelLabel;
        private final String feeText;
        private final String capacityLabel;
        private final int maxParticipants;
        private final int occupiedSeats;
        private final int occupancyPercent;
        private final String remainingLabel;
    }

    @Getter
    @Builder
    public static class JoinSelectionView {

        private final String kindLabel;
        private final String applicantName;
        private final int seats;
        private final String applicantPhone;
        private final String applicationType;
        private final List<Person> participants;
    }

    @Getter
    @Builder
    public static class JoinCompletionView {

        private final String title;
        private final String message;
        private final String roomTitle;
        private final String applicantText;
        private final String participantText;
        private final String receiptNo;
        private final String stateLabel;
        private final String paymentGuidance;
        private final String totalText;
        private final boolean teamAssigned;
        private final int teamACount;
        private final int teamBCount;
    }
}
