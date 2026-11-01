package co.kr.spoit.match.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당 시설 매칭방의 참가자 한 명 */
@Getter
@Setter
@NoArgsConstructor
public class RoomManagementParticipantRow {

    private Long participantId;
    private String nickname;
    /** 대기 / 확정 */
    private String participationStatus;
    /** A, B, 미배정이면 null */
    private String matchSide;
    private LocalDateTime joinedAt;
}
