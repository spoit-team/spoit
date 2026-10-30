package co.kr.spoit.match.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당 시설 매칭방 목록·상세 조회 결과 한 행 */
@Getter
@Setter
@NoArgsConstructor
public class RoomManagementRow {

    private Long matchId;
    private String title;
    private Long facilityId;
    private String facilityName;
    private Long sportId;
    private String sportName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private int maxParticipants;
    /** 정원 점유 인원: 참가자 행(대기·확정) + 확정 대기 중인 개인 신청 */
    private int occupiedCount;
    /** social_match.status */
    private String status;
}
