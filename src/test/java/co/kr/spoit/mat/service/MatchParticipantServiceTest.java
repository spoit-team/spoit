package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.domain.MatchParticipant;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchParticipantServiceTest {

    @Mock
    private MatchParticipantMapper participantMapper;

    @InjectMocks
    private MatchParticipantService service;

    @Test
    void preservesUnassignedAndCancelledParticipantsAndEntryMmr() {
        var waiting = participant(11L, "대기", "987.65");
        var cancelled = participant(12L, "취소", "1012.34");
        cancelled.setMatchSide("B");
        cancelled.setCancelledAt(LocalDateTime.of(2026, 10, 21, 12, 0));
        when(participantMapper.findByMatchId(7L)).thenReturn(List.of(waiting, cancelled));

        var result = service.findByMatchId(7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).participantId()).isEqualTo(11L);
        assertThat(result.get(0).applicationId()).isEqualTo(21L);
        assertThat(result.get(0).matchId()).isEqualTo(7L);
        assertThat(result.get(0).userId()).isEqualTo(31L);
        assertThat(result.get(0).participationStatus()).isEqualTo("대기");
        assertThat(result.get(0).matchSide()).isNull();
        assertThat(result.get(0).entryMmr()).isEqualTo(new BigDecimal("987.65"));
        assertThat(result.get(0).joinedAt()).isEqualTo(waiting.getJoinedAt());
        assertThat(result.get(0).cancelledAt()).isNull();
        assertThat(result.get(1).participationStatus()).isEqualTo("취소");
        assertThat(result.get(1).matchSide()).isEqualTo("B");
        assertThat(result.get(1).entryMmr()).isEqualTo(new BigDecimal("1012.34"));
        assertThat(result.get(1).cancelledAt()).isEqualTo(cancelled.getCancelledAt());
    }

    @Test
    void returnsEmptyListWhenNoParticipantsAreStored() {
        when(participantMapper.findByMatchId(7L)).thenReturn(List.of());

        assertThat(service.findByMatchId(7L)).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidMatchIdBeforeQuerying(Long matchId) {
        assertThatIllegalArgumentException().isThrownBy(() -> service.findByMatchId(matchId));

        verifyNoInteractions(participantMapper);
    }

    private MatchParticipant participant(Long participantId, String status, String mmr) {
        var participant = new MatchParticipant();
        participant.setParticipantId(participantId);
        participant.setApplicationId(21L);
        participant.setMatchId(7L);
        participant.setUserId(31L);
        participant.setParticipationStatus(status);
        participant.setEntryMmr(new BigDecimal(mmr));
        participant.setJoinedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        return participant;
    }
}
