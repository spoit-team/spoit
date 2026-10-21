package co.kr.spoit.preview.match;

import java.util.List;

import co.kr.spoit.preview.match.MatchCommonModels.ActiveFilter;
import co.kr.spoit.preview.match.MatchCommonModels.DateFilter;
import co.kr.spoit.preview.match.MatchCommonModels.Fact;
import co.kr.spoit.preview.match.MatchCommonModels.Person;
import co.kr.spoit.preview.match.MatchCommonModels.RegionFilter;
import co.kr.spoit.preview.match.MatchCommonModels.SearchCriteria;
import co.kr.spoit.preview.match.MatchCommonModels.SportFilter;
import lombok.Builder;
import lombok.Getter;

/**
 * Preview-only match list and room dialog view models.
 */
public final class MatchListModels {

    private MatchListModels() {
    }

    @Getter
    @Builder
    public static class MatchListPage {

        private final SearchCriteria search;
        private final List<SportFilter> sports;
        private final List<RegionFilter> districts;
        private final List<DateFilter> dates;
        private final List<String> hourOptions;
        private final List<String> selectedHours;
        private final String selectedHoursLabel;
        private final String selectedSportName;
        private final String sportImageUrl;
        private final int totalRoomCount;
        private final int roomCount;
        private final int recruitingCount;
        private final int matchedCount;
        private final List<ActiveFilter> activeFilters;
        private final List<MatchCardView> matches;
    }

    @Getter
    @Builder
    public static class MatchCardView {

        private final Long matchId;
        private final String sportName;
        private final String photoUrl;
        private final String photoAlt;
        private final String facilityDistrictName;
        private final String levelLabel;
        private final String statusClass;
        private final String statusLabel;
        private final String title;
        private final String dateLabel;
        private final String time;
        private final String place;
        private final String feeText;
        private final String gender;
        private final String age;
        private final List<Person> avatarParticipants;
        private final int extraParticipantCount;
        private final String participantSummary;
        private final int remainingSeats;
        private final String remainingLabel;
        private final int pendingConfirmationCount;
        private final int maxParticipants;
        private final int occupiedSeats;
        private final int occupiedPercent;
        private final boolean showTeams;
        private final int teamACount;
        private final int teamBCount;
        private final boolean canApply;
        private final String actionLabel;
        private final int participantCount;
        private final RoomDialogDetail detail;
    }

    @Getter
    @Builder
    public static class RoomDialogDetail {

        private final String badge;
        private final String title;
        private final String facilityName;
        private final List<Fact> facts;
        private final String note;
        private final boolean facilityVisible;
        private final List<Fact> facilityFacts;
        private final boolean matched;
        private final int teamACount;
        private final int teamBCount;
        private final List<Person> teamA;
        private final List<Person> teamB;
        private final List<Person> participants;
        private final List<Person> pendingParticipants;
        private final boolean canJoin;
    }
}
