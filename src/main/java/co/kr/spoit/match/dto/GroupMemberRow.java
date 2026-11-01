package co.kr.spoit.match.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** RES-04 같은 팀 신청 묶음에 속한 신청 행 하나 (대표 포함, 대표가 먼저) */
@Getter
@Setter
@NoArgsConstructor
public class GroupMemberRow {

    private Long applicationId;
    private Long userId;
    private String nickname;
    /** match_application.status */
    private String status;
}
