package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
}
