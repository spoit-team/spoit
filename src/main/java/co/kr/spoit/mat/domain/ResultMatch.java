package co.kr.spoit.mat.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResultMatch {

    private Long matchId;
    private Long facilityId;
    private Long sportId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private Long captainAParticipantId;
    private Long captainBParticipantId;
    private String resultStatus;
    private String finalResult;
    private String ratingStatus;
}
