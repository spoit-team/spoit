package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import co.kr.spoit.mat.domain.MatchParticipant;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MatchParticipantMapperIntegrationTest extends AbstractMySqlMapperTest {

    private static final LocalDateTime JOINED_AT = LocalDateTime.of(2026, 10, 20, 9, 0);

    @Autowired
    private MatchParticipantMapper participantMapper;

    @Test
    void mapsAllFieldsAndStatusesInParticipantIdOrder() {
        var participants = participantMapper.findByMatchId(1L);

        assertThat(participants)
                .extracting(MatchParticipantMapperIntegrationTest::fields)
                .containsExactly(
                        tuple(10L, 10L, 1L, 1L, "대기", null,
                                new BigDecimal("987.65"), JOINED_AT, null),
                        tuple(20L, 20L, 1L, 2L, "확정", "A",
                                new BigDecimal("1000.00"), JOINED_AT, null),
                        tuple(30L, 30L, 1L, 1L, "취소", "B",
                                new BigDecimal("1012.34"), JOINED_AT,
                                LocalDateTime.of(2026, 10, 21, 12, 0)));
        assertThat(participants).allSatisfy(
                participant -> assertThat(participant.getEntryMmr().scale()).isEqualTo(2));
    }

    @Test
    void restrictsQueryToRequestedMatchAndPreservesDecimalCapacity() {
        var participants = participantMapper.findByMatchId(2L);

        assertThat(participants)
                .extracting(MatchParticipantMapperIntegrationTest::fields)
                .containsExactly(tuple(5L, 5L, 2L, 2L, "확정", "B",
                        new BigDecimal("999999.99"), JOINED_AT, null));
        assertThat(participants.get(0).getEntryMmr().scale()).isEqualTo(2);
    }

    @Test
    void returnsEmptyListWhenNoParticipantRowsMatch() {
        assertThat(participantMapper.findByMatchId(999L)).isEmpty();
    }

    private static Tuple fields(MatchParticipant participant) {
        return tuple(
                participant.getParticipantId(),
                participant.getApplicationId(),
                participant.getMatchId(),
                participant.getUserId(),
                participant.getParticipationStatus(),
                participant.getMatchSide(),
                participant.getEntryMmr(),
                participant.getJoinedAt(),
                participant.getCancelledAt());
    }
}