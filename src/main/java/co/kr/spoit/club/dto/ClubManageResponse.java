package co.kr.spoit.club.dto;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import co.kr.spoit.club.domain.ClubJoinRequest;
import co.kr.spoit.club.domain.ClubMember;
import co.kr.spoit.club.domain.ClubSchedule;
import co.kr.spoit.club.domain.ClubSummary;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClubManageResponse {

    private final Long clubId;
    private final String clubName;
    private final Long sportId;
    private final String sportName;
    private final String areaLabel;
    private final Integer memberCount;
    private final Integer maxMembers;
    private final String leaderName;
    private final String founded;
    private final String time;
    private final String intro;
    private final Long regionId;
    private final String recruitStatus;
    private final List<Integer> activityDays;
    private final LocalTime activityStartTime;
    private final LocalTime activityEndTime;
    private final boolean leader;
    private final boolean otherMembersRemain;
    private final List<ClubMemberResponse> members;
    private final List<ClubJoinRequestResponse> applications;
    private final Integer pendingApplicationCount;
    private final String recruitLabel;
    private final String sportKey;
    private final String tierKey;
    private final int markTone;
    private final List<ClubScheduleResponse> schedules;

    public static ClubManageResponse from(ClubSummary club, List<ClubMember> members, Long userId,
            List<ClubJoinRequest> joinRequests, List<ClubSchedule> schedules) {
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

        List<ClubJoinRequestResponse> applications = new ArrayList<>();
        for (ClubJoinRequest r : joinRequests) {
            applications.add(ClubJoinRequestResponse.from(r));
        }

        List<ClubScheduleResponse> scheduleResponses = new ArrayList<>();
        for (ClubSchedule s : schedules) {
            scheduleResponses.add(ClubScheduleResponse.from(s));
        }

        boolean otherMembersRemain = members.size() > 1;

        return ClubManageResponse.builder()
                .clubId(club.getClubId())
                .clubName(club.getClubName())
                .sportId(club.getSportId())
                .sportName(club.getSportName())
                .areaLabel(ClubFormat.areaLabel(club.getAreaLabel()))
                .memberCount(club.getMemberCount())
                .maxMembers(club.getMaxMembers())
                .leaderName(leaderName)
                .founded(club.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                .time(ClubFormat.time(club))
                .intro(club.getIntro())
                .regionId(club.getRegionId())
                .recruitStatus(club.getRecruitStatus())
                .activityDays(ClubFormat.days(club.getActivityDays()))
                .activityStartTime(club.getActivityStartTime())
                .activityEndTime(club.getActivityEndTime())
                .leader(leader)
                .members(memberResponses)
                .applications(applications)
                .pendingApplicationCount(applications.size())
                .otherMembersRemain(otherMembersRemain)
                .recruitLabel(ClubFormat.manageRecruitLabel(club))
                .sportKey(ClubFormat.sportKey(club.getSportId()))
                .tierKey(ClubFormat.tierKey(club.getAverageMmr()))
                .markTone(ClubFormat.markTone(club.getClubId()))
                .schedules(scheduleResponses)
                .build();

    }
}
