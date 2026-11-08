package co.kr.spoit.mat.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 저장할 match_evaluation 행. evaluationId는 INSERT 뒤 생성 키로 채워진다. */
@Getter
@Setter
@NoArgsConstructor
public class MatchEvaluation {

    private Long evaluationId;
    private Long matchId;
    private Long sportId;
    private Long evaluatorParticipantId;
    private Long targetParticipantId;
    private BigDecimal overallScore;
    private String status;
    private LocalDateTime submittedAt;
}
