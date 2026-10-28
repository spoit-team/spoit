package co.kr.spoit.mat.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 팀 배정 가능 여부를 판단할 때 쓰는 매칭방 정보 */
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
    private LocalDateTime assignmentConfirmedAt;
}
