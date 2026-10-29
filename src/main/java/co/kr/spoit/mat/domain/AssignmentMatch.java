package co.kr.spoit.mat.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AssignmentMatch {

    private Long matchId;
    private Long facilityId;
    private LocalDateTime startAt;
    private String status;
    private int teamACapacity;
    private int teamBCapacity;
    private String assignmentMethod;
    private LocalDateTime assignmentConfirmedAt;
    private Long captainAParticipantId;
    private Long captainBParticipantId;
}
