package co.kr.spoit.match.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/** 매칭방 상세 모달 */
@Getter
@Builder
public class MatchDetailView {

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
    private final List<ParticipantView> teamA;
    private final List<ParticipantView> teamB;
    private final List<ParticipantView> participants;
    private final List<ParticipantView> pendingParticipants;
    private final boolean canJoin;
}
