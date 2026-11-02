package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import co.kr.spoit.mat.domain.MatchResult;
import co.kr.spoit.mat.domain.SportRating;
import co.kr.spoit.mat.service.RatingApplyService;
import co.kr.spoit.mat.service.RatingCalculator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")


class SportRatingMapperIntegrationTest extends AbstractMySqlMapperTest {

    private static final LocalDateTime CALCULATED_AT = LocalDateTime.of(2026, 10, 26, 13, 0);

    @Autowired
    private SportRatingMapper ratingMapper;

    @Autowired
    private MatchParticipantMapper participantMapper;

    @Test
    void mapsAllStoredRatingFields() {
        SportRating rating = ratingMapper.findByUserIdAndSportId(1L, 1L).orElseThrow();

        assertRating(rating, 1L, 1L, "1023.45", 7);
    }

    @Test
    void requiresBothKeysAndPreservesSignedDecimalCapacity() {
        // DECIMAL(8,2)의 저장·읽기 한계를 확인하며 허용 MMR 정책을 정의하지 않는다.
        SportRating otherSport = ratingMapper.findByUserIdAndSportId(1L, 2L).orElseThrow();
        SportRating otherUser = ratingMapper.findByUserIdAndSportId(2L, 1L).orElseThrow();

        assertRating(otherSport, 1L, 2L, "999999.99", 0);
        assertRating(otherUser, 2L, 1L, "-999999.99", 12);
    }

    @Test
    void returnsEmptyWithoutCreatingRatingForMissingKeyPair() {
        Long countBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sport_rating", Long.class);

        assertThat(ratingMapper.findByUserIdAndSportId(2L, 2L)).isEmpty();

        Long countAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sport_rating", Long.class);
        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    void createsMissingRatingWithDefaultsAndKeepsExistingRow() {
        ratingMapper.insertIfAbsent(2L, 2L);
        ratingMapper.insertIfAbsent(1L, 1L);

        SportRating created = ratingMapper.lockByUserIdAndSportId(2L, 2L).orElseThrow();
        assertThat(created.getCurrentMmr()).isEqualTo(new BigDecimal("1000.00"));
        assertThat(created.getRatedMatchCount()).isZero();
        assertRating(ratingMapper.lockByUserIdAndSportId(1L, 1L).orElseThrow(), 1L, 1L, "1023.45", 7);
    }

    @Test
    void updatesMmrAndIncrementsRatedMatchCount() {
        assertThat(ratingMapper.updateRating(1L, 1L, new BigDecimal("1031.14"))).isEqualTo(1);

        SportRating rating = ratingMapper.findByUserIdAndSportId(1L, 1L).orElseThrow();
        assertThat(rating.getCurrentMmr()).isEqualTo(new BigDecimal("1031.14"));
        assertThat(rating.getRatedMatchCount()).isEqualTo(8);
    }

    @Test
    void appliesMatchResultWithinHistoryConstraints() {
        ratingApplyService().apply(6L, 1L, MatchResult.A_WIN, CALCULATED_AT);

        // 회원 3: K 32, 회원 4: 레이팅 행이 없어 1000, 배치 K 64
        SportRating winner = ratingMapper.findByUserIdAndSportId(3L, 1L).orElseThrow();
        assertThat(winner.getCurrentMmr()).isEqualTo(new BigDecimal("1007.69"));
        assertThat(winner.getRatedMatchCount()).isEqualTo(8);
        SportRating loser = ratingMapper.findByUserIdAndSportId(4L, 1L).orElseThrow();
        assertThat(loser.getCurrentMmr()).isEqualTo(new BigDecimal("984.62"));
        assertThat(loser.getRatedMatchCount()).isEqualTo(1);

        var history = jdbcTemplate.queryForMap("SELECT * FROM sport_rating_history WHERE participant_id = 61");
        assertThat(history.get("match_id")).isEqualTo(6L);
        assertThat(history.get("user_id")).isEqualTo(4L);
        assertThat(history.get("mmr_before")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(history.get("mmr_after")).isEqualTo(new BigDecimal("984.62"));
        assertThat(history.get("elo_delta")).isEqualTo(new BigDecimal("-15.38"));
        assertThat(history.get("evaluation_delta")).isEqualTo(new BigDecimal("0.00"));
        assertThat(history.get("final_delta")).isEqualTo(new BigDecimal("-15.38"));
        assertThat(history.get("evaluation_score")).isNull();
        assertThat(history.get("actual_score")).isEqualTo(new BigDecimal("0.0"));
        assertThat(history.get("expected_score")).isEqualTo(new BigDecimal("0.2403"));
        assertThat(history.get("team_avg_mmr_a")).isEqualTo(new BigDecimal("1100.00"));
        assertThat(history.get("team_avg_mmr_b")).isEqualTo(new BigDecimal("900.00"));
        assertThat(history.get("algorithm_version")).isEqualTo("ELO-K32-V1");
        assertThat(history.get("calculated_at")).isEqualTo(CALCULATED_AT);
    }

    @Test
    void rejectsSecondApplicationForSameParticipant() {
        ratingApplyService().apply(6L, 1L, MatchResult.A_WIN, CALCULATED_AT);

        assertThatThrownBy(() -> ratingApplyService().apply(6L, 1L, MatchResult.A_WIN, CALCULATED_AT))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private RatingApplyService ratingApplyService() {
        return new RatingApplyService(participantMapper, ratingMapper, new RatingCalculator());
    }

    private static void assertRating(
            SportRating rating, Long userId, Long sportId, String mmr, Integer ratedMatchCount) {
        assertThat(rating.getUserId()).isEqualTo(userId);
        assertThat(rating.getSportId()).isEqualTo(sportId);
        assertThat(rating.getCurrentMmr()).isEqualTo(new BigDecimal(mmr));
        assertThat(rating.getCurrentMmr().scale()).isEqualTo(2);
        assertThat(rating.getRatedMatchCount()).isEqualTo(ratedMatchCount);
        assertThat(rating.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 21, 9, 30));
    }
}