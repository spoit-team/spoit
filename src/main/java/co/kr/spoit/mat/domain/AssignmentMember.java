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
}
