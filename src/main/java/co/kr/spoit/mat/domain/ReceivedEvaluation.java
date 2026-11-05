package co.kr.spoit.mat.domain;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 참가자가 경기에서 받은 평가자별 점수. 관찰한 항목이 없던 평가는 overallScore가 null이다. */
@Getter
@Setter
@NoArgsConstructor
public class ReceivedEvaluation {

    private Long targetParticipantId;
    private BigDecimal overallScore;
}
