package co.kr.spoit.mat.mapper;

import java.math.BigDecimal;
import java.util.Optional;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import co.kr.spoit.mat.domain.RatingChange;
import co.kr.spoit.mat.domain.SportRating;

@Mapper
public interface SportRatingMapper {

    /** 저장된 레이팅만 조회하며 미등록 회원·종목의 초기값을 만들지 않는다. */
    @Select("""
            SELECT user_id AS userId,
                   sport_id AS sportId,
                   current_mmr AS currentMmr,
                   rated_match_count AS ratedMatchCount,
                   updated_at AS updatedAt
            FROM sport_rating
            WHERE user_id = #{userId}
              AND sport_id = #{sportId}
            """)
    Optional<SportRating> findByUserIdAndSportId(
            @Param("userId") Long userId, @Param("sportId") Long sportId);

    /** 없는 행은 DB 기본값 1000으로 만들고, 있는 행은 값을 바꾸지 않고 잠근다. */
    @Insert("""
            INSERT INTO sport_rating (user_id, sport_id)
            VALUES (#{userId}, #{sportId})
            ON DUPLICATE KEY UPDATE user_id = user_id
            """)
    void insertIfAbsent(@Param("userId") Long userId, @Param("sportId") Long sportId);

    // 일반 SELECT는 트랜잭션 스냅샷을 읽어 다른 경기가 방금 커밋한 MMR을 놓칠 수 있다.
    @Select("""
            SELECT user_id AS userId,
                   sport_id AS sportId,
                   current_mmr AS currentMmr,
                   rated_match_count AS ratedMatchCount,
                   updated_at AS updatedAt
            FROM sport_rating
            WHERE user_id = #{userId}
              AND sport_id = #{sportId}
            FOR UPDATE
            """)
    Optional<SportRating> lockByUserIdAndSportId(
            @Param("userId") Long userId, @Param("sportId") Long sportId);

    @Update("""
            UPDATE sport_rating
            SET current_mmr = #{mmrAfter},
                rated_match_count = rated_match_count + 1
            WHERE user_id = #{userId}
              AND sport_id = #{sportId}
            """)
    int updateRating(@Param("userId") Long userId,
                     @Param("sportId") Long sportId,
                     @Param("mmrAfter") BigDecimal mmrAfter);

    @Insert("""
            INSERT INTO sport_rating_history (
                match_id, participant_id, user_id, sport_id, mmr_before, mmr_after,
                elo_delta, evaluation_delta, final_delta, evaluation_score,
                actual_score, expected_score, team_avg_mmr_a, team_avg_mmr_b,
                algorithm_version, calculated_at
            ) VALUES (
                #{matchId}, #{participantId}, #{userId}, #{sportId}, #{mmrBefore}, #{mmrAfter},
                #{eloDelta}, #{evaluationDelta}, #{finalDelta}, #{evaluationScore},
                #{actualScore}, #{expectedScore}, #{teamAvgMmrA}, #{teamAvgMmrB},
                #{algorithmVersion}, #{calculatedAt}
            )
            """)
    void insertHistory(RatingChange change);
}
