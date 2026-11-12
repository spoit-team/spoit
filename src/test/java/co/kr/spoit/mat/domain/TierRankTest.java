package co.kr.spoit.mat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TierRankTest {

    @ParameterizedTest
    @CsvSource({
            "-10.00, 0, 브론즈 4",
            "799.99, 0, 브론즈 4",
            "800.00, 0, 브론즈 4",
            "849.99, 99, 브론즈 4",
            "850.00, 100, 브론즈 3",
            "999.99, 399, 브론즈 1",
            "1000.00, 400, 실버 4",
            "1015.50, 431, 실버 4",
            "1046.50, 493, 실버 4",
            "1200.00, 800, 골드 4",
            "1799.99, 1999, 다이아 1",
            "1800.00, 2000, 다이아 1",
            "1900.00, 2200, 다이아 1"
    })
    void totalPointsAccumulateFromBronzeFourAndDivisionLabelHasNoPoints(BigDecimal mmr, int totalPoints, String division) {
        TierRank rank = TierRank.of(mmr);

        assertThat(rank.totalPoints()).isEqualTo(totalPoints);
        assertThat(rank.divisionLabel()).isEqualTo(division);
    }

    @Test
    void totalPointsMatchTwicePointsAboveStartForEveryCent() {
        // 현재 정책에서는 floor((MMR - 800) x 2)와 같고 800 미만은 0이다.
        for (int cents = 70_000; cents <= 200_000; cents++) {
            BigDecimal mmr = BigDecimal.valueOf(cents, 2);
            int expected = Math.max(0, mmr.subtract(BigDecimal.valueOf(800)).multiply(BigDecimal.valueOf(2))
                    .setScale(0, RoundingMode.FLOOR).intValue());
            assertThat(TierRank.of(mmr).totalPoints()).as("MMR %s", mmr).isEqualTo(expected);
        }
    }

    @Test
    void labelKeepsPointsAfterDivisionLabel() {
        assertThat(TierRank.of(new BigDecimal("1032.00")).label()).isEqualTo("실버 4 · 64P");
    }

    @Test
    void stepIndexOrdersDivisionsFromBronzeFour() {
        assertThat(TierRank.of(new BigDecimal("799.99")).stepIndex()).isZero();
        assertThat(TierRank.of(new BigDecimal("999.99")).stepIndex()).isEqualTo(3);
        assertThat(TierRank.of(new BigDecimal("1000.00")).stepIndex()).isEqualTo(4);
        assertThat(TierRank.of(new BigDecimal("1900.00")).stepIndex()).isEqualTo(19);
    }

    @ParameterizedTest
    @CsvSource({"3.33, 7", "0.25, 1", "-0.25, -1", "0.24, 0", "-0.66, -1", "0, 0"})
    void roundedPointsDoubleTheMmrDeltaHalfUp(BigDecimal mmrDelta, int points) {
        assertThat(TierRank.roundedPoints(mmrDelta)).isEqualTo(points);
    }
}
