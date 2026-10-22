package co.kr.spoit.mat.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
