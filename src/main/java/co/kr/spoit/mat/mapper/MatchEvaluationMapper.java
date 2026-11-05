package co.kr.spoit.mat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import co.kr.spoit.mat.domain.EvaluationItem;
import co.kr.spoit.mat.domain.ItemScore;
import co.kr.spoit.mat.domain.MatchEvaluation;
import co.kr.spoit.mat.domain.ReceivedEvaluation;

@Mapper
public interface MatchEvaluationMapper {

    @Select("""
            SELECT item_id AS itemId,
                   item_name AS itemName,
                   weight AS weight
            FROM sport_evaluation_item
            WHERE sport_id = #{sportId}
              AND is_active = TRUE
            ORDER BY display_order, item_id
            """)
    List<EvaluationItem> findActiveItems(@Param("sportId") Long sportId);

    // (경기, 평가자) UNIQUE가 없어 중복 제출은 social_match 잠금 아래에서 이 결과로 막는다.
    @Select("""
            SELECT DISTINCT evaluator_participant_id
            FROM match_evaluation
            WHERE match_id = #{matchId}
            ORDER BY evaluator_participant_id
            """)
    List<Long> findEvaluatorIds(@Param("matchId") Long matchId);

    @Insert("""
            INSERT INTO match_evaluation (
                match_id, sport_id, evaluator_participant_id, target_participant_id,
                overall_score, status, submitted_at
            ) VALUES (
                #{matchId}, #{sportId}, #{evaluatorParticipantId}, #{targetParticipantId},
                #{overallScore}, #{status}, #{submittedAt}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "evaluationId")
    void insertEvaluation(MatchEvaluation evaluation);

    @Insert("""
            <script>
            INSERT INTO match_evaluation_score (evaluation_id, item_id, sport_id, score, observation_status)
            VALUES
            <foreach collection="scores" item="score" separator=",">
                (#{evaluationId}, #{score.itemId}, #{sportId}, #{score.score}, #{score.observationStatus})
            </foreach>
            </script>
            """)
    void insertScores(@Param("evaluationId") Long evaluationId,
                      @Param("sportId") Long sportId,
                      @Param("scores") List<ItemScore> scores);

    @Select("""
            SELECT target_participant_id AS targetParticipantId,
                   overall_score AS overallScore
            FROM match_evaluation
            WHERE match_id = #{matchId}
            ORDER BY target_participant_id, evaluation_id
            """)
    List<ReceivedEvaluation> findReceivedEvaluations(@Param("matchId") Long matchId);
}
