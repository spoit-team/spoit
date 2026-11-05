package co.kr.spoit.club.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter 
@Setter 
public class ClubSchedule {

    private Long matchId;
    private String title;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String facilityName;
}
