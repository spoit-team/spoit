package co.kr.spoit.club.dto;

import co.kr.spoit.club.domain.ClubSummary;
import lombok.Builder;
import lombok.Getter;

@Getter 
@Builder 
public class ClubCardResponse {

    private final Long clubId;
    private final String clubName;
    private final String initials;
    private final Long sportId;
    private final String sportName;
    private final String statusLabel;
    private final boolean recruiting;
    private final String areaLabel;
    private final Integer memberCount;
    private final Integer maxMembers;
    private final String time;
    private final String footerNote;

    public static ClubCardResponse from(ClubSummary club) {

        boolean recruiting = ClubFormat.isRecruiting(club.getRecruitStatus());

        return ClubCardResponse.builder()
                .clubId(club.getClubId())
                .clubName(club.getClubName())
                .initials(ClubFormat.initials(club.getClubName()))
                .sportId(club.getSportId())
                .sportName(club.getSportName())
                .areaLabel(ClubFormat.areaLabel(club.getAreaLabel()))
                .recruiting(recruiting)
                .statusLabel(recruiting ? "팀원 모집 중" : "모집 마감")
                .memberCount(club.getMemberCount())
                .maxMembers(club.getMaxMembers())
                .time(ClubFormat.time(club))
                .footerNote(ClubFormat.footerNote(recruiting, club))
                .build();
    }

}
