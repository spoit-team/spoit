package co.kr.spoit.mat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RatingTierTest {

    @ParameterizedTest
    @CsvSource({
            "-10.00, 브론즈 4 · 0P",
            "799.99, 브론즈 4 · 0P",
            "800.00, 브론즈 4 · 0P",
            "849.99, 브론즈 4 · 99P",
            "850.00, 브론즈 3 · 0P",
            "968.00, 브론즈 1 · 36P",
            "999.99, 브론즈 1 · 99P",
            "1000.00, 실버 4 · 0P",
            "1032.00, 실버 4 · 64P",
            "1200.00, 골드 4 · 0P",
            "1400.00, 플래티넘 4 · 0P",
            "1600.00, 다이아 4 · 0P",
            "1799.99, 다이아 1 · 99P",
            "1800.00, 다이아 1 · 100P",
            "1900.00, 다이아 1 · 300P"
    })
    void labelsTierDivisionAndPointsAfterPlacement(BigDecimal mmr, String label) {
        assertThat(RatingTier.labelOf(mmr, 5)).isEqualTo(label);
    }

    @ParameterizedTest
    @CsvSource({"0, 배치 중 (0/5)", "4, 배치 중 (4/5)"})
    void labelsPlacementBeforeFiveRatedMatchesRegardlessOfMmr(int ratedMatchCount, String label) {
        assertThat(RatingTier.labelOf(new BigDecimal("1500.00"), ratedMatchCount)).isEqualTo(label);
    }
}
