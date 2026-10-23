package co.kr.spoit.match.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** RES-02 매칭방 상세 화면(matches/detail.html) */
@Getter
@Builder
public class MatchDetailPage {

    private final Long matchId;
    private final String title;
    private final String statusLabel;
    private final String statusClass;
    private final String sportName;
    private final String place;
    private final String dateLabel;
    private final String time;
    private final String feeText;
    private final List<Fact> facts;
    private final String note;
    private final Facility facility;

    private final int confirmedCount;
    private final int pendingConfirmationCount;
    private final int maxParticipants;
    private final int occupiedSeats;
    private final int availableSeats;
    private final int occupancyPercent;
    private final String occupancyLabel;
    private final String occupancyCaption;

    private final String ratingNote;
    private final TeamRoster teamA;
    private final TeamRoster teamB;
    private final List<RosterMember> pendingParticipants;

    private final String applyMessage;
    private final String applyMessageClass;
    private final MyApplication myApplication;
    private final boolean canApply;
    /** RES-03 팀 신청에서 채운다. false면 팀 신청 버튼이 비활성화된다. */
    private final boolean hasTeams;
    private final List<?> clubs;

    @Getter
    @AllArgsConstructor
    public static class Facility {
        private final String image;
        private final String imageAlt;
        private final String fallbackName;
        private final String caption;
        private final boolean visible;
        private final List<Fact> facts;
    }

    @Getter
    @AllArgsConstructor
    public static class TeamRoster {
        private final String averageText;
        private final List<RosterMember> members;
    }

    @Getter
    @AllArgsConstructor
    public static class RosterMember {
        private final String nickname;
        /** 방장 여부 */
        private final boolean host;
        private final String meta;
    }

    /** 내 신청 내역. RES-04 전까지는 숨김만 쓴다. */
    @Getter
    @AllArgsConstructor
    public static class MyApplication {
        public static final MyApplication HIDDEN =
                new MyApplication(false, null, null, null, null, null, null, null, null);

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
