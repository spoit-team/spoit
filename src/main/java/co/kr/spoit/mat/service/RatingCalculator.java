package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.ItemScore;
import co.kr.spoit.mat.domain.ObservationStatus;
import co.kr.spoit.mat.domain.RatingPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Elo와 평가 보정 계산. 단계마다 저장 자릿수로 반올림하고 그 값으로 다음 단계를 계산해,
 * sport_rating_history와 match_evaluation에 남은 값만으로 증감을 재현할 수 있게 한다.
 */
@Component
public class RatingCalculator {

    public BigDecimal teamAverage(List<BigDecimal> entryMmrs) {
        BigDecimal sum = entryMmrs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(entryMmrs.size()), RatingPolicy.MMR_SCALE, RatingPolicy.ROUNDING);
    }

    public BigDecimal expectedScore(BigDecimal ownAverage, BigDecimal opponentAverage) {
        double exponent = opponentAverage.subtract(ownAverage).doubleValue() / RatingPolicy.ELO_DIVISOR;
        return BigDecimal.valueOf(1 / (1 + Math.pow(10, exponent)))
                .setScale(RatingPolicy.EXPECTED_SCORE_SCALE, RatingPolicy.ROUNDING);
    }

    public int kFactor(int ratedMatchCount) {
        return ratedMatchCount < RatingPolicy.PLACEMENT_MATCHES
                ? RatingPolicy.PLACEMENT_K_FACTOR
                : RatingPolicy.K_FACTOR;
    }

    public BigDecimal eloDelta(int kFactor, BigDecimal actualScore, BigDecimal expectedScore) {
        return BigDecimal.valueOf(kFactor)
                .multiply(actualScore.subtract(expectedScore))
                .setScale(RatingPolicy.MMR_SCALE, RatingPolicy.ROUNDING);
    }

    /** 평가자 한 명이 대상 한 명에게 준 점수. 관찰한 항목만 가중 평균한다. */
    public BigDecimal overallScore(List<ItemScore> scores) {
        List<ItemScore> observed = scores.stream()
                .filter(score -> score.observationStatus() == ObservationStatus.OBSERVED)
                .toList();
        BigDecimal weightSum = observed.stream().map(ItemScore::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        // 관찰한 항목이 없거나 그 가중치가 모두 0이면 나눌 값이 없어 점수를 매기지 않는다.
        if (weightSum.signum() == 0) {
            return null;
        }
        BigDecimal weighted = observed.stream()
                .map(score -> score.weight().multiply(BigDecimal.valueOf(score.score())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return weighted.divide(weightSum, RatingPolicy.EVALUATION_SCORE_SCALE, RatingPolicy.ROUNDING);
    }

    /** 참가자가 경기에서 받은 평가자별 점수의 평균. 점수가 하나도 없으면 null */
    public BigDecimal playerScore(List<BigDecimal> overallScores) {
        List<BigDecimal> scored = overallScores.stream().filter(Objects::nonNull).toList();
        if (scored.isEmpty()) {
            return null;
        }
        BigDecimal sum = scored.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(scored.size()), RatingPolicy.EVALUATION_SCORE_SCALE,
                RatingPolicy.ROUNDING);
    }

    public BigDecimal evaluationDelta(BigDecimal evaluationScore) {
        if (evaluationScore == null) {
            return BigDecimal.ZERO.setScale(RatingPolicy.MMR_SCALE);
        }
        return evaluationScore.subtract(BigDecimal.valueOf(RatingPolicy.EVALUATION_BASE_SCORE))
                .multiply(RatingPolicy.EVALUATION_MULTIPLIER)
                .setScale(RatingPolicy.MMR_SCALE, RatingPolicy.ROUNDING);
    }
}
