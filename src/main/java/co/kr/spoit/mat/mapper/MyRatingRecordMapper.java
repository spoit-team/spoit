package co.kr.spoit.mat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import co.kr.spoit.mat.domain.MyRatingHistoryRow;
import co.kr.spoit.mat.domain.MyRatingRow;

/**
 * 마이페이지 종목별 레이팅과 경기 이력 조회. 이력은 sport_rating_history 행만 본다. 레이팅을 반영할 때만 이 행이 생기므로
 * 반영 전·제외·취소 경기는 따로 거르지 않아도 빠진다.
 */
@Mapper
public interface MyRatingRecordMapper {

    // 시드 데이터에는 반영 없이 만든 0경기 행이 있어, 반영된 경기가 있는 종목만 내 레이팅으로 본다.
    @Select("""
            SELECT sr.sport_id AS sportId,
                   s.name AS sportName,
                   sr.current_mmr AS currentMmr,
                   sr.rated_match_count AS ratedMatchCount
            FROM sport_rating sr
            JOIN sport s ON s.sport_id = sr.sport_id
            WHERE sr.user_id = #{userId}
              AND sr.rated_match_count > 0
            ORDER BY sr.sport_id
            """)
    List<MyRatingRow> findRatings(@Param("userId") Long userId);

    /** sportId가 null이면 전체 종목 */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM sport_rating_history
            WHERE user_id = #{userId}
            <if test="sportId != null">
              AND sport_id = #{sportId}
            </if>
            </script>
            """)
    int countHistory(@Param("userId") Long userId, @Param("sportId") Long sportId);

    // 신청 행은 참가자 행의 application_id로 찾는다. 동호회 신청 팀원은 본인 신청 행 없이 대표 신청의 참가자로만 있다.
    // 시설·신청은 표시 값이라, 찾지 못해도 반영된 경기가 이력에서 빠지지 않게 LEFT JOIN한다.
    @Select("""
            <script>
            SELECT h.history_id AS historyId,
                   h.sport_id AS sportId,
                   sm.title AS title,
                   sm.start_at AS startAt,
                   s.name AS sportName,
                   f.name AS facilityName,
                   ma.application_type AS applicationType,
                   mp.match_side AS matchSide,
                   h.mmr_before AS mmrBefore,
                   h.mmr_after AS mmrAfter,
                   h.elo_delta AS eloDelta,
                   h.evaluation_delta AS evaluationDelta,
                   h.final_delta AS finalDelta,
                   h.evaluation_score AS evaluationScore,
                   h.actual_score AS actualScore,
                   h.team_avg_mmr_a AS teamAvgMmrA,
                   h.team_avg_mmr_b AS teamAvgMmrB,
                   h.calculated_at AS calculatedAt
            FROM sport_rating_history h
            JOIN social_match sm ON sm.match_id = h.match_id
            JOIN sport s ON s.sport_id = h.sport_id
            JOIN match_participant mp ON mp.participant_id = h.participant_id
            LEFT JOIN facility f ON f.facility_id = sm.facility_id
            LEFT JOIN match_application ma ON ma.application_id = mp.application_id
            WHERE h.user_id = #{userId}
            <if test="sportId != null">
              AND h.sport_id = #{sportId}
            </if>
            ORDER BY h.calculated_at DESC, h.history_id DESC
            LIMIT #{limit}
            </script>
            """)
    List<MyRatingHistoryRow> findHistory(@Param("userId") Long userId,
                                         @Param("sportId") Long sportId,
                                         @Param("limit") int limit);
}
