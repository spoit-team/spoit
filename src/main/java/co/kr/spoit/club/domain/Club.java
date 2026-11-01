package co.kr.spoit.club.domain;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
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
