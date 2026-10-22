package co.kr.spoit.match.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 매칭방 목록 한 줄 */
@Getter
@Builder
public class MatchCardView {

    private final Long matchId;
    private final String title;
    private final String sportName;
    private final String photoUrl;
    private final String photoAlt;
    private final String facilityDistrictName;
    private final String levelLabel;
    private final String statusLabel;
    private final String statusClass;
    private final String dateLabel;
    private final String time;
    private final String place;
    private final String feeText;
    private final List<ParticipantView> avatarParticipants;
    private final int extraParticipantCount;
    private final int participantCount;
    private final int pendingConfirmationCount;
    private final int occupiedSeats;
    private final int maxParticipants;
    private final int remainingSeats;
    private final int occupiedPercent;
    private final String participantSummary;
    private final String remainingLabel;
    private final boolean showTeams;
    private final int teamACount;
    private final int teamBCount;
    private final boolean canApply;
    private final String actionLabel;
    private final MatchDetailView detail;
}
