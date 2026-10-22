package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.domain.SportRating;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SportRatingServiceTest {

    @Mock
    private SportRatingMapper ratingMapper;

    @InjectMocks
    private SportRatingService service;

    @Test
    void preservesStoredMmrAndCountWithoutTierOrRoundingPolicy() {
        var rating = new SportRating();
        rating.setUserId(3L);
        rating.setSportId(5L);
        rating.setCurrentMmr(new BigDecimal("1023.45"));
        rating.setRatedMatchCount(7);
        rating.setUpdatedAt(LocalDateTime.of(2026, 10, 21, 9, 30));
        when(ratingMapper.findByUserIdAndSportId(3L, 5L)).thenReturn(Optional.of(rating));

        var response = service.findByUserIdAndSportId(3L, 5L).orElseThrow();

        assertThat(response.userId()).isEqualTo(3L);
        assertThat(response.sportId()).isEqualTo(5L);
        assertThat(response.currentMmr()).isEqualTo(new BigDecimal("1023.45"));
        assertThat(response.ratedMatchCount()).isEqualTo(7);
        assertThat(response.updatedAt()).isEqualTo(rating.getUpdatedAt());
    }

    @Test
    void doesNotSynthesizeDefaultRatingWhenRowIsMissing() {
        when(ratingMapper.findByUserIdAndSportId(3L, 5L)).thenReturn(Optional.empty());

        assertThat(service.findByUserIdAndSportId(3L, 5L)).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidUserIdBeforeQuerying(Long userId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.findByUserIdAndSportId(userId, 5L));

        verifyNoInteractions(ratingMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidSportIdBeforeQuerying(Long sportId) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.findByUserIdAndSportId(3L, sportId));

        verifyNoInteractions(ratingMapper);
    }
}
