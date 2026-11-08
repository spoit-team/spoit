package co.kr.spoit.club.dto;

import co.kr.spoit.club.domain.ClubSummary;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClubCardResponse {

    private final Long clubId;
    private final String clubName;
    private final Long sportId;
    private final String sportName;
    private final String statusLabel;
    private final boolean recruiting;
    private final String areaLabel;
    private final Integer memberCount;
    private final String tierLabel;
    private final Integer maxMembers;
    private final String time;
    private final String sportKey;
    private final String tierKey;
    private final int markTone;

    public static ClubCardResponse from(ClubSummary club) {

        boolean recruiting = ClubFormat.isRecruiting(club);

        return ClubCardResponse.builder()
                .clubId(club.getClubId())
                .clubName(club.getClubName())
                .sportId(club.getSportId())
                .sportName(club.getSportName())
                .areaLabel(ClubFormat.areaLabel(club.getAreaLabel()))
                .recruiting(recruiting)
                .statusLabel(recruiting ? "모집 중" : "모집 마감")
                .memberCount(club.getMemberCount())
                .maxMembers(club.getMaxMembers())
                .time(ClubFormat.time(club))
                .tierLabel(ClubFormat.tierLabel(club.getAverageMmr()))
                .sportKey(ClubFormat.sportKey(club.getSportId()))
                .tierKey(ClubFormat.tierKey(club.getAverageMmr()))
                .markTone(ClubFormat.markTone(club.getClubId()))
                .build();
    }

}
