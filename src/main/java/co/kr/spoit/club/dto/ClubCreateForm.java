package co.kr.spoit.club.dto;

import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import lombok.Data;

@Data
public class ClubCreateForm {

    private String clubName;
    private Long sportId;
    private Integer maxMembers;
    private Long regionId;
    private String intro;

    private List<Integer> activityDays;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime activityStartTime;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime activityEndTime;

}
