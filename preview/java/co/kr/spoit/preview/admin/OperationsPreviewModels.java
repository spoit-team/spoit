package co.kr.spoit.preview.admin;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class OperationsPreviewModels {

    private OperationsPreviewModels() {
    }

    @Getter
    @AllArgsConstructor
    public static class MatchOperationsView {
        private final Long matchId;
        private final String title;
        private final String dateLabel;
        private final String time;
        private final String status;
        private final String statusLabel;
        private final String place;
        private final int confirmedCount;
        private final int pendingConfirmationCount;
        private final int maxParticipants;
        private final boolean canAssign;
        private final boolean bothTeamsAssigned;
        private final List<MatchApplicationView> applications;
        private final List<PendingGroupView> pendingGroups;
        private final TeamView teamA;
        private final TeamView teamB;
        private final List<RosterPersonView> unassigned;
    }

    @Getter
    @AllArgsConstructor
    public static class MatchApplicationView {
        private final Long applicationId;
        private final String applicantName;
        private final int seats;
        private final String statusTone;
        private final String statusLabel;
        private final String kindLabel;
        private final String memberNames;
        private final String createdAt;
        private final String receiptNo;
        private final String rejectionReason;
        private final boolean pending;
    }

    @Getter
    @AllArgsConstructor
    public static class PendingGroupView {
        private final int count;
        private final String names;
        private final String receiptNo;
    }

    @Getter
    @AllArgsConstructor
    public static class TeamView {
        private final int limit;
        private final List<RosterPersonView> members;
    }

    @Getter
    @AllArgsConstructor
    public static class RosterPersonView {
        private final Long participantId;
        private final String nickname;
        private final String sourceLabel;
        private final String ratingText;
        private final String matchSide;
    }
}
