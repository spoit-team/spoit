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
public class ClubManageResponse {

    private final Long clubId;
    private final String clubName;
    private final String initials;
    private final Long sportId;
    private final String sportName;
    private final String areaLabel;
    private final Integer memberCount;
    private final Integer maxMembers;
    private final String leaderName;
    private final String founded;
    private final String time;
    private final String intro;
    // 로그인한 내가 이 동호회의 동호회장인지
    private final boolean leader;
    private final List<ClubMemberResponse> members;

    public static ClubManageResponse from(ClubSummary club, List<ClubMember> members, Long userId) {
        String leaderName = "미정";
        boolean leader = false;
        List<ClubMemberResponse> memberResponses = new ArrayList<>();
        for (ClubMember m : members) {
            ClubMemberResponse response = ClubMemberResponse.from(m, userId);
            memberResponses.add(response);
            if (response.isLeader()) {
                leaderName = response.getNickname();
            }
            if (response.isLeader() && response.isCurrent()) {
                leader = true;
            }
        }

        return ClubManageResponse.builder()
                .clubId(club.getClubId())
                .clubName(club.getClubName())
                .initials(ClubFormat.initials(club.getClubName()))
                .sportId(club.getSportId())
                .sportName(club.getSportName())
                .areaLabel(ClubFormat.areaLabel(club.getAreaLabel()))
                .memberCount(club.getMemberCount())
                .maxMembers(club.getMaxMembers())
                .leaderName(leaderName)
                .founded(club.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                .time(ClubFormat.time(club))
                .intro(club.getIntro())
                .leader(leader)
                .members(memberResponses)
                .build();

    }
}
