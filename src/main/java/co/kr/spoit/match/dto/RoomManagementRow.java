package co.kr.spoit.match.dto;

import java.math.BigDecimal;
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

    /* 아래는 목록 조회에서만 채운다. 수정 모달의 현재 값으로 쓴다. */
    private int teamACapacity;
    private int teamBCapacity;
    private BigDecimal feePerPerson;
    private String requiredSkill;
    /** 참가자 행(대기·확정) + 대기 신청(개인·팀). 1 이상이면 일정을 바꿀 수 없다. */
    private int reservedCount;
    /** 시설 사진 공개 URL. 없으면 null */
    private String photoUrl;
}
