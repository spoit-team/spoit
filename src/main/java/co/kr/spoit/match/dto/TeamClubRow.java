package co.kr.spoit.match.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** RES-04 팀 신청에 쓸 수 있는 동호회 (로그인 회원이 활성 회원인 해체되지 않은 동호회) */
@Getter
@Setter
@NoArgsConstructor
public class TeamClubRow {

    private Long clubId;
    private String clubName;
    private Long sportId;
    /** 동호회장 닉네임, 없으면 null */
    private String leaderName;
    /** 활성 회원 수 */
    private int memberCount;
}
