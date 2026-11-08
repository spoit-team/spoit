package co.kr.spoit.mat.domain;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AssignmentMember {

    private Long participantId;
    private String nickname;
    private BigDecimal entryMmr;
    private String matchSide;
    /** 동호회 신청으로 들어온 참가자면 동호회명, 아니면 null */
    private String clubName;
}
