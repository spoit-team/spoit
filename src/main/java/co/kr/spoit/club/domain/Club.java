package co.kr.spoit.club.domain;

import java.time.LocalTime;

import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
public class Club {

    private Long clubId;
    private Long sportId;
    private String clubName;
    private String intro;
    private Integer maxMembers;
    private String activityDays;
    private LocalTime activityStartTime;
    private LocalTime activityEndTime;

}
