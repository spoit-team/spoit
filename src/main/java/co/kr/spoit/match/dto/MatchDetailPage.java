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
    /** 요구 실력 조건 (신청 화면 요약에 쓴다) */
    private final String levelLabel;
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
    /** RES-04 로그인 회원이 팀 신청에 쓸 수 있는 동호회가 있는지. false면 팀 신청 버튼이 비활성화된다. */
    private final boolean hasTeams;
    private final List<TeamOption> clubs;

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

    /** RES-04 팀 신청 다이얼로그의 동호회 하나 (detail.html #team-panel) */
    @Getter
    @AllArgsConstructor
    public static class TeamOption {
        private final Long clubId;
        private final String clubName;
        private final String leaderName;
        /** 활성 회원 수 (신청자 포함) */
        private final int memberCount;
        private final String averageRatingText;
        /** 처음 선택 인원: 신청 대표 1명 */
        private final int selectionCount;
        /** 신청 대표 본인을 뺀 활성 회원 */
        private final List<TeamMemberOption> members;
    }

    @Getter
    @AllArgsConstructor
    public static class TeamMemberOption {
        private final Long clubMemberId;
        private final String nickname;
        private final String initial;
        private final boolean leader;
        private final String ratingText;
        /** 이 매치에 이미 신청했으면 체크된 채 잠근다 */
        private final boolean alreadyApplied;
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
