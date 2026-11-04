package co.kr.spoit.mat.service;

import co.kr.spoit.mat.domain.MatchParticipant;
import co.kr.spoit.mat.domain.MatchResult;
import co.kr.spoit.mat.domain.RatingChange;
import co.kr.spoit.mat.domain.RatingPolicy;
import co.kr.spoit.mat.domain.SportRating;
import co.kr.spoit.mat.mapper.MatchParticipantMapper;
import co.kr.spoit.mat.mapper.SportRatingMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정 결과와 평가 보정을 참가자 MMR에 반영한다.
 *
 * <p>잠금 순서는 social_match, sport_rating(user_id 오름차순)이다. 이중 반영은 호출자의
 * rating_status 검사가 막고, 뚫리더라도 이력의 참가자 UNIQUE가 전체를 롤백시킨다.</p>
 */
@Service
public class RatingApplyService {

    private static final String SIDE_A = "A";
    private static final String SIDE_B = "B";

    private final MatchParticipantMapper participantMapper;
    private final SportRatingMapper ratingMapper;
    private final RatingCalculator calculator;

    public RatingApplyService(MatchParticipantMapper participantMapper,
                              SportRatingMapper ratingMapper,
                              RatingCalculator calculator) {
        this.participantMapper = participantMapper;
        this.ratingMapper = ratingMapper;
        this.calculator = calculator;
    }

    /**
     * social_match를 잠그고 rating_status를 확인한 트랜잭션 안에서만 호출한다.
     * evaluationScores는 참가자 ID별 평가 점수이고, 평가 점수가 없는 참가자는 보정 없이 Elo만 반영한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(long matchId, long sportId, MatchResult result, Map<Long, BigDecimal> evaluationScores,
                      LocalDateTime now) {
        // 확정 참가자의 match_side는 NULL일 수 있어 배정된 인원만 반영한다.
        // 회원 순서를 고정해 같은 회원이 있는 두 경기가 서로의 sport_rating을 기다리며 교착되지 않게 한다.
        List<MatchParticipant> players = AutoAssignmentService.confirmedOf(participantMapper.findByMatchId(matchId))
                .stream()
                .filter(p -> p.getMatchSide() != null)
                .sorted(Comparator.comparing(MatchParticipant::getUserId))
                .toList();
        BigDecimal averageA = averageOf(players, SIDE_A, matchId);
        BigDecimal averageB = averageOf(players, SIDE_B, matchId);
        BigDecimal expectedA = calculator.expectedScore(averageA, averageB);
        BigDecimal expectedB = calculator.expectedScore(averageB, averageA);

        for (MatchParticipant player : players) {
            String side = player.getMatchSide();
            BigDecimal expected = SIDE_A.equals(side) ? expectedA : expectedB;
            BigDecimal actual = result.actualScoreOf(side);
            SportRating rating = lockRating(player.getUserId(), sportId);
            BigDecimal eloDelta = calculator.eloDelta(calculator.kFactor(rating.getRatedMatchCount()),
                    actual, expected);
            // 평가 보정은 배치 경기에도 그대로 더한다.
            BigDecimal evaluationScore = evaluationScores.get(player.getParticipantId());
            BigDecimal evaluationDelta = calculator.evaluationDelta(evaluationScore);
            BigDecimal finalDelta = eloDelta.add(evaluationDelta);
            BigDecimal mmrAfter = rating.getCurrentMmr().add(finalDelta);

            int updated = ratingMapper.updateRating(player.getUserId(), sportId, mmrAfter);
            if (updated != 1) {
                throw new IllegalStateException("레이팅 갱신에 실패했습니다. matchId=" + matchId
                        + ", userId=" + player.getUserId());
            }
            ratingMapper.insertHistory(new RatingChange(matchId, player.getParticipantId(), player.getUserId(),
                    sportId, rating.getCurrentMmr(), mmrAfter, eloDelta, evaluationDelta, finalDelta, evaluationScore,
                    actual, expected, averageA, averageB, RatingPolicy.ALGORITHM_VERSION, now));
        }
    }

    private BigDecimal averageOf(List<MatchParticipant> players, String side, long matchId) {
        List<BigDecimal> entryMmrs = players.stream()
                .filter(p -> side.equals(p.getMatchSide()))
                .map(MatchParticipant::getEntryMmr)
                .toList();
        if (entryMmrs.isEmpty()) {
            throw new IllegalStateException(side + "팀 확정 참가자가 없어 레이팅을 반영할 수 없습니다. matchId=" + matchId);
        }
        return calculator.teamAverage(entryMmrs);
    }

    // 행을 먼저 보장한다. 없는 행을 SELECT FOR UPDATE하면 갭 잠금끼리 교착될 수 있다.
    private SportRating lockRating(Long userId, long sportId) {
        ratingMapper.insertIfAbsent(userId, sportId);
        return ratingMapper.lockByUserIdAndSportId(userId, sportId)
                .orElseThrow(() -> new IllegalStateException("레이팅 행이 없습니다. userId=" + userId));
    }
}
