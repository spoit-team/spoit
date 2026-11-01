package co.kr.spoit.match.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당 시설 매칭방의 참가 신청 한 건 */
@Getter
@Setter
@NoArgsConstructor
public class RoomManagementApplicationRow {

    private Long applicationId;
    private String applicantName;
    /** 개인 / 동호회 */
    private String applicationType;
    /** 대기 / 확정 / 취소 / 반려 */
    private String status;
    private String receiptNo;
    private LocalDateTime createdAt;
    /** 동호회 신청이면 동호회명, 개인이면 null */
    private String clubName;
}
