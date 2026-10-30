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
    /** 이 신청 행의 회원. 팀 신청(RES-04)이면 팀원 각자, 이전 방식 동호회 신청이면 대표 */
    private Long applicantUserId;
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
    /** 이 신청으로 정원을 차지한 참가자 수 (이전 방식 동호회 신청 인원 표시용) */
    private int participantCount;

    /** RES-04 팀 신청 묶음 (대표 행 id), 팀 신청이 아니면 null */
    private Long groupApplicationId;
    /** 팀 신청 대표 회원: 이 회원만 묶음 전체를 취소할 수 있다 */
    private Long groupLeaderUserId;
    private String groupLeaderName;
    /** 같은 묶음에서 내 행과 상태가 같은 행 수 (팀 신청 인원 표시용) */
    private int groupSize;

    public boolean isTeamGroup() {
        return groupApplicationId != null;
    }
}
