package co.kr.spoit.preview.club;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Typed view models for the preview-only team pages.
 */
public final class TeamPageModels {

    private TeamPageModels() {
    }

    @Getter
    @Builder
    public static class SportTab {
        private final String name;
        private final String emoji;
        private final String url;
        private final boolean active;
        private final long count;
    }

    @Getter
    @Builder
    public static class Statistic {
        private final String label;
        private final String value;
        private final String unit;
    }

    @Getter
    @Builder
    public static class Filters {
        private final String sportId;
        private final String query;
        private final Long regionId;
        private final Integer level;
        private final String sort;
        private final boolean recruitingOnly;
    }

    @Getter
    @Builder
    public static class District {
        private final Long regionId;
        private final String name;
    }

    @Getter
    @Builder
    public static class ClubCard {
        private final Long clubId;
        private final Long sportId;
        private final String initials;
        private final String clubName;
        private final String sportName;
        private final String areaLabel;
        private final boolean recruiting;
        private final String statusLabel;
        private final int level;
        private final List<String> tags;
        private final int memberCount;
        private final int maxMembers;
        private final String ratingText;
        private final String time;
        private final String footerNote;
    }

    @Getter
    @Builder
    public static class RecruitPosition {
        private final String name;
        private final int count;
    }

    @Getter
    @Builder
    public static class TeamMember {
        private final String initials;
        private final String nickname;
        private final boolean leader;
    }

    @Getter
    @Builder
    public static class ClubDetail {
        private final Long clubId;
        private final Long sportId;
        private final String initials;
        private final String clubName;
        private final String sportName;
        private final String areaLabel;
        private final boolean recruiting;
        private final int level;
        private final List<String> tags;
        private final int memberCount;
        private final int maxMembers;
        private final String ratingText;
        private final String time;
        private final String founded;
        private final boolean manager;
        private final String intro;
        private final String leaderName;
        private final String leaderInitials;
        private final List<RecruitPosition> recruitPositions;
        private final boolean member;
        private final Long pendingJoinRequestId;
        private final List<TeamMember> members;
    }

    @Getter
    @Builder
    public static class Schedule {
        private final String date;
        private final String dateLabel;
        private final String title;
        private final String place;
        private final String time;
    }

    @Getter
    @Builder
    public static class ManagedMember {
        private final Long clubMemberId;
        private final String initials;
        private final String nickname;
        private final String handle;
        private final String joined;
        private final boolean current;
        private final boolean leader;
    }

    @Getter
    @Builder
    public static class Application {
        private final Long joinRequestId;
        private final String nickname;
        private final String handle;
        private final String date;
        private final String message;
        private final boolean pending;
        private final boolean approved;
        private final String statusLabel;
    }

    @Getter
    @Builder
    public static class ManagedClub {
        private final Long clubId;
        private final Long sportId;
        private final Long regionId;
        private final String initials;
        private final String clubName;
        private final String sportName;
        private final String areaLabel;
        private final boolean leader;
        private final int memberCount;
        private final int maxMembers;
        private final String leaderName;
        private final String founded;
        private final String time;
        private final String intro;
        private final boolean otherMembersRemain;
        private final int level;
        private final String recruitStatus;
        private final List<Integer> activityDays;
        private final String activityStartTime;
        private final String activityEndTime;
        private final Integer pendingApplicationCount;
        private final List<Schedule> schedules;
        private final List<ManagedMember> members;
        private final List<Application> applications;
    }

    @Getter
    @Builder
    public static class ManagedClubSummary {
        private final Long clubId;
        private final String clubName;
        private final int memberCount;
        private final boolean active;
    }

    @Getter
    @Builder
    public static class SportOption {
        private final Long sportId;
        private final String name;
    }
}
