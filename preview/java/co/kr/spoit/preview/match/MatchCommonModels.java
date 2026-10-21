package co.kr.spoit.preview.match;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Shared preview-only match view models.
 */
public final class MatchCommonModels {

    private MatchCommonModels() {
    }

    @Getter
    @Builder
    public static class ActiveFilter {

        private final String label;
        private final String url;
    }

    @Getter
    @Builder
    public static class DateFilter {

        private final int idx;
        private final String label;
    }

    @Getter
    @Builder
    public static class Fact {

        private final String label;
        private final String value;
    }

    @Getter
    @Builder
    public static class Person {

        private final String nickname;
        private final String initial;
        private final boolean host;
        private final String meta;
        private final String roleLabel;
    }

    @Getter
    @Builder
    public static class RegionFilter {

        private final Long regionId;
        private final String name;
    }

    @Getter
    @Builder
    public static class SearchCriteria {

        private final Long sportId;
        private final Long regionId;
        private final Integer dateIdx;
        private final List<String> hours;
    }

    @Getter
    @Builder
    public static class SportFilter {

        private final Long sportId;
        private final String name;
        private final int count;
    }

    @Getter
    @Builder
    public static class ClubMemberView {

        private final Long clubMemberId;
        private final String nickname;
        private final String initial;
        private final String ratingText;
        private final boolean leaderOnly;
        private final boolean applicant;
    }

    @Getter
    @Builder
    public static class ClubView {

        private final Long clubId;
        private final String clubName;
        private final String leaderName;
        private final String leaderInitial;
        private final String leaderCaption;
        private final int memberCount;
        private final String averageRatingText;
        private final int selectionCount;
        private final boolean leaderAuto;
        private final boolean leaderIsApplicant;
        private final List<ClubMemberView> members;
    }

    @Getter
    @Builder
    public static class TeamView {

        private final List<Person> members;
        private final String averageText;
    }

    @Getter
    @Builder
    public static class FacilityView {

        private final String image;
        private final String imageAlt;
        private final String fallbackName;
        private final String caption;
        private final boolean visible;
        private final List<Fact> facts;
    }
}
