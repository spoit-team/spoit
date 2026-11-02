package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.RatingPolicy;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Elo 계산. 단계마다 저장 자릿수로 반올림하고 그 값으로 다음 단계를 계산해,
 * sport_rating_history에 남은 값만으로 증감을 재현할 수 있게 한다.
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
}
