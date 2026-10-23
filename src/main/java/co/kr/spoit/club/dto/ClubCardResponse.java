package co.kr.spoit.club.dto;

import co.kr.spoit.club.domain.ClubSummary;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor 
public class ClubCardResponse {

    private static final String[] DAYS = {"", "월", "화", "수", "목", "금", "토", "일"};

    private Long clubId;
    private String clubName;
    private String initials;
    private Long sportId;
    private String sportName;
    private String statusLabel;
    private boolean recruiting;
    private String areaLabel;
    private Integer memberCount;
    private Integer maxMembers;
    private String time;
    private String footerNote;

    public static ClubCardResponse from(ClubSummary club) {
        ClubCardResponse card = new ClubCardResponse();
        boolean recruiting = "모집중".equals(club.getRecruitStatus());

        card.setClubId(club.getClubId());
        card.setClubName(club.getClubName());
        card.setInitials(initials(club.getClubName()));
        card.setSportId(club.getSportId());
        card.setSportName(club.getSportName());
        card.setAreaLabel(club.getAreaLabel() != null ? club.getAreaLabel() : "지역 미정");
        card.setRecruiting(recruiting);
        card.setStatusLabel(recruiting ? "팀원 모집 중" : "모집 마감");
        card.setMemberCount(club.getMemberCount());
        card.setMaxMembers(club.getMaxMembers());
        card.setTime(time(club));
        card.setFooterNote(footerNote(recruiting, club));
        return card;
    }

    private static String initials(String clubName) {
        return clubName.length() <= 2 ? clubName : clubName.substring(0, 2);
    }

    private static String time(ClubSummary club) {
        String[] numbers = club.getActivityDays().split(",");
        String[] names = new String[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            names[i] = DAYS[Integer.parseInt(numbers[i])];
        }
        return String.join(", ", names) + " " + club.getActivityStartTime() + " - " + club.getActivityEndTime();
    }

    private static String footerNote(boolean recruiting, ClubSummary club) {
        if (!recruiting) {
            return "모집이 마감된 팀입니다.";
        }
        return "모집 자리: " + (club.getMaxMembers() - club.getMemberCount()) + "명";
    }
}
