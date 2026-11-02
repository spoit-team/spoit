package co.kr.spoit.mat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RatingTierTest {

    @ParameterizedTest
    @CsvSource({
            "-10.00, 브론즈",
            "899.99, 브론즈",
            "900.00, 실버",
            "1049.99, 실버",
            "1050.00, 골드",
            "1199.99, 골드",
            "1200.00, 플래티넘",
            "1349.99, 플래티넘",
            "1350.00, 다이아"
    })
    void labelsTierFromLowerBoundAfterPlacement(BigDecimal mmr, String label) {
        assertThat(RatingTier.labelOf(mmr, 5)).isEqualTo(label);
    }

    @ParameterizedTest
    @CsvSource({"0, 배치 중 (0/5)", "4, 배치 중 (4/5)"})
    void labelsPlacementBeforeFiveRatedMatchesRegardlessOfMmr(int ratedMatchCount, String label) {
        assertThat(RatingTier.labelOf(new BigDecimal("1500.00"), ratedMatchCount)).isEqualTo(label);
    }
}
