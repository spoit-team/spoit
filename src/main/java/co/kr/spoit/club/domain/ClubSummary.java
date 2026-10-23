package co.kr.spoit.club.domain;

import java.time.LocalDateTime;
import java.time.LocalTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter 
@Setter 
@NoArgsConstructor 
public class ClubSummary {

    private Long clubId;
    private String clubName;
    private Long sportId;
    private String sportName;
    private String recruitStatus;
    private Integer maxMembers;
    private String activityDays;
    private LocalTime activityStartTime;
    private LocalTime activityEndTime;
    private String imageUrl;
    private LocalDateTime createdAt;
    private Integer memberCount;
    private String areaLabel;
}
