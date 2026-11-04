package co.kr.spoit.mat.dto;

import co.kr.spoit.mat.domain.ObservationStatus;
import java.math.BigDecimal;
import java.util.List;

/**
 * 팀장 한 명이 평가 대상 전원에게 매긴 항목 점수. 관찰 상태가 정해진 값이 아니면 역직렬화 단계에서 400이 된다.
 *
 * <p>score를 Integer로 받으면 Jackson이 7.5를 7로 잘라 받으므로, 소수를 거절하려고 BigDecimal로 받는다.</p>
 */
public record EvaluationSubmitRequest(List<TargetEvaluation> evaluations) {

    public record TargetEvaluation(Long targetParticipantId, List<Score> scores) {
    }

    public record Score(Long itemId, ObservationStatus observationStatus, BigDecimal score) {
    }
}
