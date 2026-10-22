package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import co.kr.spoit.mat.domain.SportRating;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SportRatingMapperIntegrationTest extends AbstractMySqlMapperTest {

    @Autowired
    private SportRatingMapper ratingMapper;

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