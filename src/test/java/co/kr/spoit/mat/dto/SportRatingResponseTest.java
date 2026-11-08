package co.kr.spoit.mat.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SportRatingResponseTest {

    @Test
    void tierLabelUsesCurrentMmrAndRatedMatchCount() {
        BigDecimal mmr = new BigDecimal("1032.00");

        assertThat(new SportRatingResponse(1L, 1L, mmr, 5, null).tierLabel()).isEqualTo("실버 4 · 64P");
        assertThat(new SportRatingResponse(1L, 1L, mmr, 3, null).tierLabel()).isEqualTo("배치 중 (3/5)");
    }
}
