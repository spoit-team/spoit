package co.kr.spoit.mat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TeamCandidateTest {

    @Test
    void normalizesMmrToTwoDecimalPlaces() {
        var candidate = new TeamCandidate(1L, new BigDecimal("1000.5"));

        assertThat(candidate.mmr()).isEqualTo(new BigDecimal("1000.50"));
        assertThat(candidate.mmrCents()).isEqualTo(100050L);
        assertThat(candidate).isEqualTo(new TeamCandidate(1L, new BigDecimal("1000.500")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "999999.99", "1E+3", "-0.01", "-1000", "-999999.99"})
    void acceptsMmrWithinColumnRange(String mmr) {
        var candidate = new TeamCandidate(1L, new BigDecimal(mmr));

        assertThat(candidate.mmr().scale()).isEqualTo(2);
    }

    @Test
    void keepsNegativeMmrAsNormalValue() {
        // 레이팅에 하한이 없어 음수 MMR도 배정에 쓰는 정상 값이다.
        var candidate = new TeamCandidate(1L, new BigDecimal("-12.5"));

        assertThat(candidate.mmr()).isEqualTo(new BigDecimal("-12.50"));
        assertThat(candidate.mmrCents()).isEqualTo(-1250L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidParticipantId(Long participantId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TeamCandidate(participantId, new BigDecimal("1000")));
    }

    @Test
    void rejectsNullMmr() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TeamCandidate(1L, null))
                .withMessageContaining("null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000.001", "999.999", "0.005"})
    void rejectsMmrFinerThanTwoDecimalPlaces(String mmr) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TeamCandidate(1L, new BigDecimal(mmr)))
                .withMessageContaining("소수 둘째 자리");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1000000.00", "1E+30", "-1000000.00", "-1E+30"})
    void rejectsMmrOutsideColumnRange(String mmr) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TeamCandidate(1L, new BigDecimal(mmr)))
                .withMessageContaining("-999999.99 이상 999999.99 이하");
    }
}
