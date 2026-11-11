package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

import co.kr.spoit.mat.domain.ItemScore;
import co.kr.spoit.mat.domain.MatchResult;
import co.kr.spoit.mat.domain.ObservationStatus;
import co.kr.spoit.mat.service.RatingCalculator;

/** 레이팅 시연 데이터 마이그레이션을 실제 MySQL에서 실행해, 넣은 이력이 RatingCalculator로 다시 계산한 값과 같은지 확인한다. */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
// 공용 fixture 행은 쓰지 않아 전용 fixture만 지정한다. 여기 @Sql이 상위 클래스의 @Sql을 대신한다.
@Sql(scripts = "/mat/rating-demo-migration-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class RatingDemoMigrationIntegrationTest extends AbstractMySqlMapperTest {

    private static final Path MIGRATION = Path.of("db/migrations/20261111-rating-demo-data.sql");
    private static final List<String> TABLES = List.of("app_user", "user_region", "user_preferred_sport", "sport_rating",
            "social_match", "match_application", "match_participant", "match_result_submission", "match_evaluation",
            "match_evaluation_score", "sport_rating_history");
    private static final BigDecimal START_MMR = new BigDecimal("1000.00");

    private final RatingCalculator calculator = new RatingCalculator();

    @Test
    void secondRunAddsNoRows() {
        migrate();
        Map<String, Long> afterFirstRun = rowCounts();

        migrate();

        assertThat(rowCounts()).isEqualTo(afterFirstRun);
    }

    @Test
    void historyMatchesRatingCalculatorAndChainsFromStartMmr() {
        migrate();
        List<HistoryRow> history = history();
        assertThat(history).isNotEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE email LIKE 'rating-__@spoit.test'", Long.class)).isEqualTo(24L);

        Map<Long, List<Participant>> participantsByMatch = participantsByMatch();
        Map<Long, Map<Long, List<BigDecimal>>> overallScoresByMatch = overallScoresByMatch();
        Map<String, BigDecimal> currentMmr = new HashMap<>();
        Map<String, Integer> ratedCount = new HashMap<>();
        // 반영 시각 순으로 따라가며 각 행을 실제 계산기로 다시 계산한다.
        for (HistoryRow row : history) {
            String key = row.userId + ":" + row.sportId;
            List<Participant> players = participantsByMatch.get(row.matchId);
            Participant me = players.stream().filter(p -> p.participantId == row.participantId).findFirst().orElseThrow();
            BigDecimal averageA = calculator.teamAverage(entryMmrs(players, "A"));
            BigDecimal averageB = calculator.teamAverage(entryMmrs(players, "B"));
            BigDecimal expected = "A".equals(me.side)
                    ? calculator.expectedScore(averageA, averageB)
                    : calculator.expectedScore(averageB, averageA);
            BigDecimal actual = MatchResult.valueOf(row.finalResult).actualScoreOf(me.side);
            int kFactor = calculator.kFactor(ratedCount.getOrDefault(key, 0));
            BigDecimal eloDelta = calculator.eloDelta(kFactor, actual, expected);
            List<BigDecimal> overalls = overallScoresByMatch.getOrDefault(row.matchId, Map.of()).get(row.participantId);
            BigDecimal evaluationScore = overalls == null ? null : calculator.playerScore(overalls);
            BigDecimal evaluationDelta = calculator.evaluationDelta(evaluationScore);
            BigDecimal before = currentMmr.getOrDefault(key, START_MMR);

            assertThat(row.teamAvgA).as("팀 A 평균 history=%d", row.historyId).isEqualByComparingTo(averageA);
            assertThat(row.teamAvgB).as("팀 B 평균 history=%d", row.historyId).isEqualByComparingTo(averageB);
            assertThat(row.expectedScore).as("기대 승률 history=%d", row.historyId).isEqualByComparingTo(expected);
            assertThat(row.actualScore).as("실제 점수 history=%d", row.historyId).isEqualByComparingTo(actual);
            assertThat(row.eloDelta).as("Elo 증감 history=%d", row.historyId).isEqualByComparingTo(eloDelta);
            if (evaluationScore == null) {
                assertThat(row.evaluationScore).as("평가 점수 history=%d", row.historyId).isNull();
            } else {
                assertThat(row.evaluationScore).as("평가 점수 history=%d", row.historyId).isEqualByComparingTo(evaluationScore);
            }
            assertThat(row.evaluationDelta).as("평가 보정 history=%d", row.historyId).isEqualByComparingTo(evaluationDelta);
            assertThat(row.finalDelta).isEqualByComparingTo(eloDelta.add(evaluationDelta));
            assertThat(row.mmrBefore).as("직전 이력과 이어짐 history=%d", row.historyId).isEqualByComparingTo(before);
            assertThat(row.mmrAfter).isEqualByComparingTo(before.add(row.finalDelta));
            assertThat(me.entryMmr).as("entry_mmr은 확정 시점 MMR history=%d", row.historyId).isEqualByComparingTo(before);

            currentMmr.put(key, row.mmrAfter);
            ratedCount.merge(key, 1, Integer::sum);
        }

        List<Map<String, Object>> ratings = jdbcTemplate.queryForList("""
                SELECT sr.user_id, sr.sport_id, sr.current_mmr, sr.rated_match_count
                FROM sport_rating sr
                JOIN app_user u ON u.user_id = sr.user_id
                WHERE u.email LIKE 'rating-__@spoit.test'
                """);
        assertThat(ratings).hasSize(currentMmr.size());
        for (Map<String, Object> rating : ratings) {
            String key = rating.get("user_id") + ":" + rating.get("sport_id");
            assertThat((BigDecimal) rating.get("current_mmr")).as("current_mmr %s", key).isEqualByComparingTo(currentMmr.get(key));
            assertThat(((Number) rating.get("rated_match_count")).intValue()).as("rated_match_count %s", key)
                    .isEqualTo(ratedCount.get(key));
        }
    }

    @Test
    void storedOverallScoresMatchItemScores() {
        migrate();
        Map<Long, BigDecimal> weights = new HashMap<>();
        jdbcTemplate.query("SELECT item_id, weight FROM sport_evaluation_item",
                rs -> { weights.put(rs.getLong("item_id"), rs.getBigDecimal("weight")); });
        Map<Long, List<ItemScore>> scoresByEvaluation = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT s.evaluation_id, s.item_id, s.score, s.observation_status
                FROM match_evaluation_score s
                JOIN match_evaluation e ON e.evaluation_id = s.evaluation_id
                JOIN match_participant mp ON mp.participant_id = e.evaluator_participant_id
                JOIN app_user u ON u.user_id = mp.user_id
                WHERE u.email LIKE 'rating-__@spoit.test'
                ORDER BY s.evaluation_id, s.item_id
                """, rs -> {
                    Integer score = rs.getObject("score") == null ? null : rs.getInt("score");
                    scoresByEvaluation.computeIfAbsent(rs.getLong("evaluation_id"), id -> new ArrayList<>())
                            .add(new ItemScore(rs.getLong("item_id"), weights.get(rs.getLong("item_id")),
                                    ObservationStatus.valueOf(rs.getString("observation_status")), score));
                });
        assertThat(scoresByEvaluation).isNotEmpty();
        Map<Long, BigDecimal> stored = new HashMap<>();
        jdbcTemplate.query("SELECT evaluation_id, overall_score FROM match_evaluation",
                rs -> { stored.put(rs.getLong("evaluation_id"), rs.getBigDecimal("overall_score")); });
        scoresByEvaluation.forEach((evaluationId, scores) -> {
            assertThat(scores).as("항목 수 evaluation=%d", evaluationId).hasSize(3);
            BigDecimal overall = calculator.overallScore(scores);
            if (overall == null) {
                assertThat(stored.get(evaluationId)).isNull();
            } else {
                assertThat(stored.get(evaluationId)).as("overall_score evaluation=%d", evaluationId).isEqualByComparingTo(overall);
            }
        });
    }

    private static List<BigDecimal> entryMmrs(List<Participant> players, String side) {
        return players.stream().filter(p -> side.equals(p.side)).map(p -> p.entryMmr).toList();
    }

    private Map<Long, List<Participant>> participantsByMatch() {
        Map<Long, List<Participant>> result = new HashMap<>();
        jdbcTemplate.query("""
                SELECT mp.match_id, mp.participant_id, mp.match_side, mp.entry_mmr
                FROM match_participant mp
                JOIN app_user u ON u.user_id = mp.user_id
                WHERE u.email LIKE 'rating-__@spoit.test'
                  AND mp.participation_status = '확정'
                  AND mp.match_side IS NOT NULL
                """, rs -> {
                    result.computeIfAbsent(rs.getLong("match_id"), id -> new ArrayList<>())
                            .add(new Participant(rs.getLong("participant_id"), rs.getString("match_side"),
                                    rs.getBigDecimal("entry_mmr")));
                });
        return result;
    }

    // MatchResultService.evaluationScoresOf와 같이 경기·대상별로 받은 overall_score를 모은다.
    private Map<Long, Map<Long, List<BigDecimal>>> overallScoresByMatch() {
        Map<Long, Map<Long, List<BigDecimal>>> result = new HashMap<>();
        jdbcTemplate.query("""
                SELECT e.match_id, e.target_participant_id, e.overall_score
                FROM match_evaluation e
                JOIN match_participant mp ON mp.participant_id = e.target_participant_id
                JOIN app_user u ON u.user_id = mp.user_id
                WHERE u.email LIKE 'rating-__@spoit.test'
                ORDER BY e.target_participant_id, e.evaluation_id
                """, rs -> {
                    result.computeIfAbsent(rs.getLong("match_id"), id -> new HashMap<>())
                            .computeIfAbsent(rs.getLong("target_participant_id"), id -> new ArrayList<>())
                            .add(rs.getBigDecimal("overall_score"));
                });
        return result;
    }

    private List<HistoryRow> history() {
        return jdbcTemplate.query("""
                SELECT h.history_id, h.match_id, h.participant_id, h.user_id, h.sport_id, h.mmr_before, h.mmr_after,
                       h.elo_delta, h.evaluation_delta, h.final_delta, h.evaluation_score, h.actual_score,
                       h.expected_score, h.team_avg_mmr_a, h.team_avg_mmr_b, h.calculated_at, sm.final_result
                FROM sport_rating_history h
                JOIN social_match sm ON sm.match_id = h.match_id
                JOIN app_user u ON u.user_id = h.user_id
                WHERE u.email LIKE 'rating-__@spoit.test'
                ORDER BY h.calculated_at, h.history_id
                """, (rs, rowNum) -> new HistoryRow(rs.getLong("history_id"), rs.getLong("match_id"),
                rs.getLong("participant_id"), rs.getLong("user_id"), rs.getLong("sport_id"),
                rs.getBigDecimal("mmr_before"), rs.getBigDecimal("mmr_after"), rs.getBigDecimal("elo_delta"),
                rs.getBigDecimal("evaluation_delta"), rs.getBigDecimal("final_delta"), rs.getBigDecimal("evaluation_score"),
                rs.getBigDecimal("actual_score"), rs.getBigDecimal("expected_score"), rs.getBigDecimal("team_avg_mmr_a"),
                rs.getBigDecimal("team_avg_mmr_b"), rs.getTimestamp("calculated_at").toLocalDateTime(),
                rs.getString("final_result")));
    }

    private void migrate() {
        assertThat(MIGRATION).as("Gradle 테스트는 저장소 루트에서 실행된다").exists();
        byte[] script = withoutTransactionControl(read(MIGRATION)).getBytes(StandardCharsets.UTF_8);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ByteArrayResource(script), StandardCharsets.UTF_8));
            return null;
        });
    }

    // 실제 적용은 한 트랜잭션으로 묶지만, 테스트에서는 롤백되는 테스트 트랜잭션 안에서 실행해야 한다.
    private static String withoutTransactionControl(String script) {
        String stripped = script.replaceAll("(?m)^(START TRANSACTION|COMMIT);\\r?$", "");
        assertThat(stripped).as("COMMIT이 남으면 테스트 데이터가 전용 DB에 확정된다")
                .doesNotContainPattern("(?im)^\\s*(START TRANSACTION|COMMIT|ROLLBACK)\\b");
        return stripped;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Long> rowCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : TABLES) {
            counts.put(table, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
        return counts;
    }

    private record Participant(long participantId, String side, BigDecimal entryMmr) {
    }

    private record HistoryRow(long historyId, long matchId, long participantId, long userId, long sportId,
                              BigDecimal mmrBefore, BigDecimal mmrAfter, BigDecimal eloDelta, BigDecimal evaluationDelta,
                              BigDecimal finalDelta, BigDecimal evaluationScore, BigDecimal actualScore,
                              BigDecimal expectedScore, BigDecimal teamAvgA, BigDecimal teamAvgB,
                              LocalDateTime calculatedAt, String finalResult) {
    }
}
