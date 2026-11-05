package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;

import co.kr.spoit.mat.domain.ItemScore;
import co.kr.spoit.mat.domain.ObservationStatus;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RatingCalculatorTest {

    private final RatingCalculator calculator = new RatingCalculator();

    @Test
    void roundsTeamAverageHalfUpToTwoDecimals() {
        assertThat(calculator.teamAverage(List.of(new BigDecimal("1000.01"), new BigDecimal("1000.02"))))
                .isEqualTo(new BigDecimal("1000.02"));
    }

    @ParameterizedTest
    @CsvSource({
            "1000.00, 1000.00, 0.5000",
            "1100.00, 900.00, 0.7597",
            "900.00, 1100.00, 0.2403",
            "999999.99, -999999.99, 1.0000",
            "-999999.99, 999999.99, 0.0000"
    })
    void calculatesExpectedScoreToFourDecimals(BigDecimal own, BigDecimal opponent, BigDecimal expected) {
        assertThat(calculator.expectedScore(own, opponent)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0, 64", "4, 64", "5, 32", "6, 32"})
    void usesPlacementKFactorBeforeFiveRatedMatches(int ratedMatchCount, int kFactor) {
        assertThat(calculator.kFactor(ratedMatchCount)).isEqualTo(kFactor);
    }

    @ParameterizedTest
    @CsvSource({
            "32, 1.0, 0.7597, 7.69",
            "64, 0.0, 0.2403, -15.38",
            "32, 0.5, 0.5000, 0.00"
    })
    void roundsEloDeltaHalfUpToTwoDecimals(int kFactor, BigDecimal actual, BigDecimal expected, BigDecimal delta) {
        assertThat(calculator.eloDelta(kFactor, actual, expected)).isEqualTo(delta);
    }

    @Test
    void averagesObservedScoresByWeight() {
        // 시드 평가 1: (8×0.35 + 7×0.35 + 7×0.30) / 1.00
        assertThat(calculator.overallScore(List.of(observed("0.35", 8), observed("0.35", 7), observed("0.30", 7))))
                .isEqualTo(new BigDecimal("7.35"));
    }

    @Test
    void leavesNotObservedItemsOutOfWeightedAverage() {
        // (8×0.40 + 6×0.30) / 0.70 = 7.142…
        assertThat(calculator.overallScore(List.of(observed("0.40", 8), notObserved("0.30"), observed("0.30", 6))))
                .isEqualTo(new BigDecimal("7.14"));
    }

    @Test
    void givesNoScoreWithoutObservedWeight() {
        assertThat(calculator.overallScore(List.of(notObserved("0.40"), notObserved("0.60")))).isNull();
        assertThat(calculator.overallScore(List.of(observed("0.00", 9), notObserved("1.00")))).isNull();
    }

    @Test
    void averagesReceivedScoresIgnoringEvaluationsWithoutScore() {
        assertThat(calculator.playerScore(Arrays.asList(new BigDecimal("7.35"), null, new BigDecimal("8.00"))))
                .isEqualTo(new BigDecimal("7.68"));
        assertThat(calculator.playerScore(Arrays.asList(null, null))).isNull();
        assertThat(calculator.playerScore(List.of())).isNull();
    }

    @ParameterizedTest
    @CsvSource({"0.00, -5.00", "10.00, 5.00", "5.00, 0.00", "7.68, 2.68", ", 0.00"})
    void correctsByEvaluationScoreAroundFive(BigDecimal evaluationScore, BigDecimal delta) {
        assertThat(calculator.evaluationDelta(evaluationScore)).isEqualTo(delta);
    }

    private static ItemScore observed(String weight, int score) {
        return new ItemScore(1L, new BigDecimal(weight), ObservationStatus.OBSERVED, score);
    }

    private static ItemScore notObserved(String weight) {
        return new ItemScore(1L, new BigDecimal(weight), ObservationStatus.NOT_OBSERVED, null);
    }
}
