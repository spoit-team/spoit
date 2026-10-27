package co.kr.spoit.match.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** findMyApplications 조회 결과: 신청 + 매칭방 + 내 참가자 행(있으면) */
@Getter
@Setter
@NoArgsConstructor
public class MyApplicationRow {

    private Long applicationId;
    private Long matchId;
    private String applicationType;
    /** match_application.status */
    private String status;
    private LocalDateTime createdAt;

    private String title;
    private String sportName;
    private String facilityName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** social_match.status */
    private String matchStatus;

    /** 내 참가자 행의 A/B, 확정 전이거나 미배정이면 null */
    private String matchSide;
    /** 이 신청으로 정원을 차지한 참가자 수 (동호회 신청 인원 표시용) */
    private int participantCount;
}
