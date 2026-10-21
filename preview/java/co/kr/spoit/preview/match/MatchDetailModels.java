package co.kr.spoit.preview.match;

import java.util.List;

import co.kr.spoit.preview.match.MatchCommonModels.ClubView;
import co.kr.spoit.preview.match.MatchCommonModels.FacilityView;
import co.kr.spoit.preview.match.MatchCommonModels.Fact;
import co.kr.spoit.preview.match.MatchCommonModels.Person;
import co.kr.spoit.preview.match.MatchCommonModels.TeamView;
import lombok.Builder;
import lombok.Getter;

/**
 * Preview-only match detail view models.
 */
public final class MatchDetailModels {

    private MatchDetailModels() {
    }

    @Getter
    @Builder
    public static class MatchDetailPage {

        private final String unavailableMessage;
        private final MatchDetailView match;
    }

    @Getter
    @Builder
    public static class MatchDetailView {

        private final Long matchId;
        private final String title;
        private final String statusClass;
        private final String statusLabel;
        private final String sportName;
        private final String place;
        private final String dateLabel;
        private final String time;
        private final String feeText;
        private final FacilityView facility;
        private final List<Fact> facts;
        private final String note;
        private final int confirmedCount;
        private final int pendingConfirmationCount;
        private final int maxParticipants;
        private final String ratingNote;
        private final TeamView teamA;
        private final TeamView teamB;
        private final List<Person> pendingParticipants;
        private final String applyMessageClass;
        private final String applyMessage;
        private final String occupancyLabel;
        private final int occupiedSeats;
        private final int occupancyPercent;
        private final String occupancyCaption;
        private final MyApplicationView myApplication;
        private final boolean canApply;
        private final boolean hasTeams;
        private final List<ClubView> clubs;
        private final int availableSeats;
    }

    @Getter
    @Builder
    public static class MyApplicationView {

        private final boolean visible;
        private final String kindLabel;
        private final String seatsText;
        private final String receiptNo;
        private final String createdAtText;
        private final String statusClass;
        private final String statusLabel;
        private final String teamLabel;
        private final String note;
    }
}
