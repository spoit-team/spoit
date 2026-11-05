package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import co.kr.spoit.mat.domain.EvaluationItem;
import co.kr.spoit.mat.domain.ItemScore;
import co.kr.spoit.mat.domain.MatchEvaluation;
import co.kr.spoit.mat.domain.ObservationStatus;
import co.kr.spoit.mat.domain.ReceivedEvaluation;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
class MatchEvaluationMapperIntegrationTest extends AbstractMySqlMapperTest {

    private static final LocalDateTime SUBMITTED_AT = LocalDateTime.of(2026, 10, 26, 12, 30);

    @Autowired
    private MatchEvaluationMapper evaluationMapper;

    @Test
    void findsActiveItemsOfSportInDisplayOrder() {
        // 3은 비활성, 4는 다른 종목 항목이라 빠진다.
        assertThat(evaluationMapper.findActiveItems(1L))
                .extracting(EvaluationItem::getItemId, EvaluationItem::getItemName, EvaluationItem::getWeight)
                .containsExactly(tuple(2L, "MAT 테스트 항목 A", new BigDecimal("0.40")),
                        tuple(1L, "MAT 테스트 항목 B", new BigDecimal("0.60")));
        assertThat(evaluationMapper.findActiveItems(999L)).isEmpty();
    }

    @Test
    void savesEvaluationsWithScoresAndReadsThemBack() {
        // 경기 7: 팀장 70(회원 3)과 71(회원 4)이 서로를 평가한다.
        MatchEvaluation first = evaluation(70L, 71L, new BigDecimal("8.00"));
        evaluationMapper.insertEvaluation(first);
        evaluationMapper.insertScores(first.getEvaluationId(), 1L, List.of(
                new ItemScore(2L, new BigDecimal("0.40"), ObservationStatus.OBSERVED, 8),
                new ItemScore(1L, new BigDecimal("0.60"), ObservationStatus.NOT_OBSERVED, null)));
        MatchEvaluation second = evaluation(71L, 70L, null);
        evaluationMapper.insertEvaluation(second);
        evaluationMapper.insertScores(second.getEvaluationId(), 1L, List.of(
                new ItemScore(2L, new BigDecimal("0.40"), ObservationStatus.OBSERVED, 0)));

        assertThat(first.getEvaluationId()).isNotNull();
        assertThat(second.getEvaluationId()).isGreaterThan(first.getEvaluationId());
        var row = jdbcTemplate.queryForMap("SELECT sport_id, status, submitted_at FROM match_evaluation"
                + " WHERE evaluation_id = ?", first.getEvaluationId());
        assertThat(row.get("sport_id")).isEqualTo(1L);
        assertThat(row.get("status")).isEqualTo("제출 완료");
        assertThat(row.get("submitted_at")).isEqualTo(SUBMITTED_AT);
        // 0점은 정상 점수로, 관찰 불가는 NULL로 저장한다.
        assertThat(scoresOf(first.getEvaluationId())).containsExactly("1:-:NOT_OBSERVED", "2:8:OBSERVED");
        assertThat(scoresOf(second.getEvaluationId())).containsExactly("2:0:OBSERVED");

        assertThat(evaluationMapper.findEvaluatorIds(7L)).containsExactly(70L, 71L);
        assertThat(evaluationMapper.findEvaluatorIds(6L)).isEmpty();
        assertThat(evaluationMapper.findReceivedEvaluations(7L))
                .extracting(ReceivedEvaluation::getTargetParticipantId, ReceivedEvaluation::getOverallScore)
                .containsExactly(tuple(70L, null), tuple(71L, new BigDecimal("8.00")));
    }

    private List<String> scoresOf(Long evaluationId) {
        return jdbcTemplate.queryForList("SELECT CONCAT(item_id, ':', COALESCE(score, '-'), ':', observation_status)"
                + " FROM match_evaluation_score WHERE evaluation_id = ? ORDER BY item_id", String.class, evaluationId);
    }

    private static MatchEvaluation evaluation(Long evaluatorId, Long targetId, BigDecimal overallScore) {
        var evaluation = new MatchEvaluation();
        evaluation.setMatchId(7L);
        evaluation.setSportId(1L);
        evaluation.setEvaluatorParticipantId(evaluatorId);
        evaluation.setTargetParticipantId(targetId);
        evaluation.setOverallScore(overallScore);
        evaluation.setStatus("제출 완료");
        evaluation.setSubmittedAt(SUBMITTED_AT);
        return evaluation;
    }
}
