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
    private final Long sportId;
    private final String sportName;
    private final String areaLabel;
    private final boolean recruiting;
    private final Integer memberCount;
    private final Integer maxMembers;
    private final String tierLabel;
    private final String time;
    private final String sportKey;
    private final String tierKey;
    private final int markTone;

    private final String founded;
    private final String intro;
    private final List<ClubMemberResponse> members;

    private final boolean member;
    private final boolean manager;
    private final Long pendingJoinRequestId;

    public static ClubDetailResponse from(ClubSummary club, List<ClubMember> members,
            Long userId, Long pendingJoinRequestId) {
        boolean recruiting = ClubFormat.isRecruiting(club);

        boolean member = false;
        boolean manager = false;
        List<ClubMemberResponse> memberResponses = new ArrayList<>();
        for (ClubMember m : members) {
            ClubMemberResponse response = ClubMemberResponse.from(m, userId);
            memberResponses.add(response);
            if (response.isCurrent()) {
                member = true;
                manager = response.isLeader();
            }

        }

        return ClubDetailResponse.builder()
                .clubId(club.getClubId())
                .clubName(club.getClubName())
                .recruiting(recruiting)
                .memberCount(club.getMemberCount())
                .maxMembers(club.getMaxMembers())
                .time(ClubFormat.time(club))
                .founded(club.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                .members(memberResponses)
                .sportId(club.getSportId())
                .sportName(club.getSportName())
                .areaLabel(ClubFormat.areaLabel(club.getAreaLabel()))
                .intro(club.getIntro())
                .member(member)
                .manager(manager)
                .pendingJoinRequestId(pendingJoinRequestId)
                .tierLabel(ClubFormat.tierLabel(club.getAverageMmr()))
                .sportKey(ClubFormat.sportKey(club.getSportId()))
                .tierKey(ClubFormat.tierKey(club.getAverageMmr()))
                .markTone(ClubFormat.markTone(club.getClubId()))
                .build();
    }
}
