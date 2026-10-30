package co.kr.spoit.match.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** RES-04 팀 신청 대상 동호회원 한 명 (활성 소속만) */
@Getter
@Setter
@NoArgsConstructor
public class TeamMemberRow {

    private Long clubId;
    private Long clubMemberId;
    private Long userId;
    private String nickname;
    /** 동호회장 또는 동호회원 */
    private String role;
    private LocalDateTime deletedAt;
    private LocalDateTime penaltyTime;
    /** 매치 종목의 sport_rating, 행이 없으면 null */
    private BigDecimal currentMmr;
    private Integer ratedMatchCount;
    /** 이 매치에 이미 활성 신청 또는 활성 참가자인지 */
    private boolean alreadyApplied;

    public boolean isLeader() {
        return "동호회장".equals(role);
    }
}
