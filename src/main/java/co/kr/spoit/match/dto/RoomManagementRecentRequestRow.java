package co.kr.spoit.match.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 담당 시설 매칭방에 접수된 최근 참가 신청 한 건.
 *
 * <p>팀(동호회) 신청은 팀원마다 {@code match_application} 행이 있으므로 조회 SQL이 대표 행
 * ({@code group_application_id}가 자기 자신인 행)만 남겨 신청 하나를 한 번만 표현한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class RoomManagementRecentRequestRow {

    private Long applicationId;
    private Long matchId;
    private String matchTitle;
    private String facilityName;
    /** 신청자(동호회 신청이면 동호회장) 닉네임 */
    private String applicantName;
    /** 동호회 신청이면 동호회명, 개인이면 null */
    private String clubName;
    /** 개인 / 동호회 */
    private String applicationType;
    /** 대기 / 확정 / 취소 / 반려 */
    private String status;
    private LocalDateTime createdAt;
}
