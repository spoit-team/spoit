package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.kr.spoit.mat.domain.MatchParticipant;
import co.kr.spoit.mat.domain.MatchResult;
import co.kr.spoit.mat.domain.RatingChange;
import co.kr.spoit.mat.domain.SportRating;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingApplyServiceTest {

    private static final long MATCH_ID = 10L;
    private static final long SPORT_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 12, 0);

    @Mock
    private MatchParticipantMapper participantMapper;

    @Mock
    private SportRatingMapper ratingMapper;

    private RatingApplyService service;

    @BeforeEach
    void setUp() {
        service = new RatingApplyService(participantMapper, ratingMapper, new RatingCalculator());
        lenient().when(ratingMapper.updateRating(any(), any(), any())).thenReturn(1);
    }

    @ParameterizedTest
    @CsvSource({
            "A_WIN, 1.0, 7.69, 0.0, -7.69",
            "DRAW, 0.5, -8.31, 0.5, 8.31",
            "B_WIN, 0.0, -24.31, 1.0, 24.31"
    })
    void appliesEloFromEntryMmrAverageForEachResult(MatchResult result, BigDecimal actualA, BigDecimal deltaA,
                                                    BigDecimal actualB, BigDecimal deltaB) {
        givenParticipants(participant(1L, 11L, "확정", "A", "1100.00"), participant(2L, 12L, "확정", "B", "900.00"));
        givenRating(11L, "1200.00", 7);
        givenRating(12L, "800.00", 7);

        service.apply(MATCH_ID, SPORT_ID, result, Map.of(), NOW);

        // 기대 점수는 entry_mmr 평균으로 구하고, 증감은 잠근 current_mmr에 더한다.
        assertThat(savedChanges())
                .extracting(RatingChange::userId, RatingChange::mmrBefore, RatingChange::mmrAfter,
                        RatingChange::eloDelta, RatingChange::actualScore, RatingChange::expectedScore)
                .containsExactly(
                        tuple(11L, new BigDecimal("1200.00"), new BigDecimal("1200.00").add(deltaA),
                                deltaA, actualA, new BigDecimal("0.7597")),
                        tuple(12L, new BigDecimal("800.00"), new BigDecimal("800.00").add(deltaB),
                                deltaB, actualB, new BigDecimal("0.2403")));
        verify(ratingMapper).updateRating(11L, SPORT_ID, new BigDecimal("1200.00").add(deltaA));
        verify(ratingMapper).updateRating(12L, SPORT_ID, new BigDecimal("800.00").add(deltaB));
    }

    @Test
    void usesPlacementKFactorPerMember() {
        givenParticipants(participant(1L, 11L, "확정", "A", "1000.00"), participant(2L, 12L, "확정", "A", "1000.00"),
                participant(3L, 13L, "확정", "B", "1000.00"), participant(4L, 14L, "확정", "B", "1000.00"));
        givenRating(11L, "1000.00", 4);
        givenRating(12L, "1000.00", 5);
        givenRating(13L, "1000.00", 4);
        givenRating(14L, "1000.00", 5);

        service.apply(MATCH_ID, SPORT_ID, MatchResult.A_WIN, Map.of(), NOW);

        assertThat(savedChanges()).extracting(RatingChange::eloDelta).containsExactly(
                new BigDecimal("32.00"), new BigDecimal("16.00"),
                new BigDecimal("-32.00"), new BigDecimal("-16.00"));
    }

    @Test
    void addsEvaluationCorrectionToEloIncludingPlacementMatches() {
        givenParticipants(participant(1L, 11L, "확정", "A", "1000.00"), participant(2L, 12L, "확정", "A", "1000.00"),
                participant(3L, 13L, "확정", "B", "1000.00"), participant(4L, 14L, "확정", "B", "1000.00"));
        givenRating(11L, "1000.00", 7);
        givenRating(12L, "1000.00", 2);
        givenRating(13L, "1000.00", 7);
        givenRating(14L, "1000.00", 7);

        // 참가자 4는 평가 점수가 없어 보정하지 않는다. 회원 12는 배치 중이어도 보정을 받는다.
        service.apply(MATCH_ID, SPORT_ID, MatchResult.A_WIN, Map.of(1L, new BigDecimal("8.00"),
                2L, new BigDecimal("0.00"), 3L, new BigDecimal("10.00")), NOW);

        assertThat(savedChanges())
                .extracting(RatingChange::userId, RatingChange::eloDelta, RatingChange::evaluationScore,
                        RatingChange::evaluationDelta, RatingChange::finalDelta, RatingChange::mmrAfter)
                .containsExactly(
                        tuple(11L, new BigDecimal("16.00"), new BigDecimal("8.00"), new BigDecimal("3.00"),
                                new BigDecimal("19.00"), new BigDecimal("1019.00")),
                        tuple(12L, new BigDecimal("32.00"), new BigDecimal("0.00"), new BigDecimal("-5.00"),
                                new BigDecimal("27.00"), new BigDecimal("1027.00")),
                        tuple(13L, new BigDecimal("-16.00"), new BigDecimal("10.00"), new BigDecimal("5.00"),
                                new BigDecimal("-11.00"), new BigDecimal("989.00")),
                        tuple(14L, new BigDecimal("-16.00"), null, new BigDecimal("0.00"),
                                new BigDecimal("-16.00"), new BigDecimal("984.00")));
        verify(ratingMapper).updateRating(12L, SPORT_ID, new BigDecimal("1027.00"));
    }

    @Test
    void savesTeamAveragesAndAlgorithmVersionInHistory() {
        givenParticipants(participant(1L, 11L, "확정", "A", "1100.00"), participant(2L, 12L, "확정", "B", "900.00"));
        givenRating(11L, "1000.00", 7);
        givenRating(12L, "1000.00", 7);

        service.apply(MATCH_ID, SPORT_ID, MatchResult.DRAW, Map.of(), NOW);

        assertThat(savedChanges()).allSatisfy(change -> {
            assertThat(change.matchId()).isEqualTo(MATCH_ID);
            assertThat(change.sportId()).isEqualTo(SPORT_ID);
            assertThat(change.teamAvgMmrA()).isEqualTo(new BigDecimal("1100.00"));
            assertThat(change.teamAvgMmrB()).isEqualTo(new BigDecimal("900.00"));
            assertThat(change.algorithmVersion()).isEqualTo("ELO-K32-V2");
            assertThat(change.calculatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void appliesOnlyAssignedConfirmedParticipants() {
        givenParticipants(participant(1L, 11L, "확정", "A", "1000.00"), participant(2L, 12L, "확정", "B", "1000.00"),
                participant(3L, 13L, "취소", "A", "5000.00"), participant(4L, 14L, "확정", null, "5000.00"));
        givenRating(11L, "1000.00", 7);
        givenRating(12L, "1000.00", 7);

        service.apply(MATCH_ID, SPORT_ID, MatchResult.A_WIN, Map.of(), NOW);

        assertThat(savedChanges()).extracting(RatingChange::userId).containsExactly(11L, 12L);
        assertThat(savedChanges()).extracting(RatingChange::expectedScore)
                .containsOnly(new BigDecimal("0.5000"));
    }

    @Test
    void createsRowThenLocksInUserIdOrder() {
        givenParticipants(participant(1L, 12L, "확정", "A", "1000.00"), participant(2L, 11L, "확정", "B", "1000.00"));
        givenRating(11L, "1000.00", 0);
        givenRating(12L, "1000.00", 0);

        service.apply(MATCH_ID, SPORT_ID, MatchResult.DRAW, Map.of(), NOW);

        InOrder order = inOrder(ratingMapper);
        order.verify(ratingMapper).insertIfAbsent(11L, SPORT_ID);
        order.verify(ratingMapper).lockByUserIdAndSportId(11L, SPORT_ID);
        order.verify(ratingMapper).insertIfAbsent(12L, SPORT_ID);
        order.verify(ratingMapper).lockByUserIdAndSportId(12L, SPORT_ID);
    }

    @Test
    void rejectsMatchWithEmptyTeam() {
        givenParticipants(participant(1L, 11L, "확정", "A", "1000.00"));

        assertThatThrownBy(() -> service.apply(MATCH_ID, SPORT_ID, MatchResult.A_WIN, Map.of(), NOW))
                .isInstanceOf(IllegalStateException.class);
        verify(ratingMapper, never()).updateRating(any(), eq(SPORT_ID), any());
    }

    private List<RatingChange> savedChanges() {
        ArgumentCaptor<RatingChange> captor = ArgumentCaptor.forClass(RatingChange.class);
        verify(ratingMapper, atLeastOnce()).insertHistory(captor.capture());
        return captor.getAllValues();
    }

    private void givenParticipants(MatchParticipant... participants) {
        when(participantMapper.findByMatchId(MATCH_ID)).thenReturn(List.of(participants));
    }

    private void givenRating(Long userId, String mmr, int ratedMatchCount) {
        var rating = new SportRating();
        rating.setUserId(userId);
        rating.setSportId(SPORT_ID);
        rating.setCurrentMmr(new BigDecimal(mmr));
        rating.setRatedMatchCount(ratedMatchCount);
        when(ratingMapper.lockByUserIdAndSportId(userId, SPORT_ID)).thenReturn(Optional.of(rating));
    }

    private MatchParticipant participant(Long participantId, Long userId, String status, String side, String entryMmr) {
        var participant = new MatchParticipant();
        participant.setParticipantId(participantId);
        participant.setUserId(userId);
        participant.setMatchId(MATCH_ID);
        participant.setParticipationStatus(status);
        participant.setMatchSide(side);
        participant.setEntryMmr(new BigDecimal(entryMmr));
        return participant;
    }
}
