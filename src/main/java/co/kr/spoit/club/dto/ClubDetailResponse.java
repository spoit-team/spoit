package co.kr.spoit.club.dto;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubSummary;
import lombok.Builder;
import lombok.Getter;

@Getter 
@Builder 
public class ClubDetailResponse {

    private final Long clubId;
    private final String clubName;
    private final String initials;
    private final Long sportId;
    private final String sportName;
    private final String areaLabel;
    private final boolean recruiting;
    private final Integer memberCount;
    private final Integer maxMembers;
    private final String time;
    private final String footerNote;

    private final String founded;
    private final String intro;
    private final String leaderName;
    private final String leaderInitials;
    private final List<ClubMemberResponse> members;

    private final boolean member;
    private final boolean manager;
    private final Long pendingJoinRequestId;

    public static ClubDetailResponse from(ClubSummary club, List<ClubMember> members) {
        boolean recruiting = ClubFormat.isRecruiting(club.getRecruitStatus());

        String leaderName = "미정";
        String leaderInitials = "미정";
        List<ClubMemberResponse> memberResponses = new ArrayList<>();
        for (ClubMember m : members) {
            ClubMemberResponse response = ClubMemberResponse.from(m);
            memberResponses.add(response);
            if(response.isLeader()) {
                leaderName = response.getNickname();
                leaderInitials = response.getInitials();
            }

        }

        return  ClubDetailResponse.builder()
        .clubId(club.getClubId())
        .clubName(club.getClubName())
        .initials(ClubFormat.initials(club.getClubName()))
        .recruiting(recruiting)
        .memberCount(club.getMemberCount())
        .maxMembers(club.getMaxMembers())
        .time(ClubFormat.time(club))
        .footerNote(ClubFormat.footerNote(recruiting, club))
        .founded(club.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
        .leaderInitials(leaderInitials)
        .members(memberResponses)
        .sportId(club.getSportId())
        .sportName(club.getSportName())
        .areaLabel(ClubFormat.areaLabel(club.getAreaLabel()))
        .intro(club.getIntro())
        .leaderName(leaderName)
        .build();
    }
}
