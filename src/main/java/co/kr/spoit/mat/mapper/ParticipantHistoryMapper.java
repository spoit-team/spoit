package co.kr.spoit.mat.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import co.kr.spoit.mat.domain.ActivePenalty;
import co.kr.spoit.mat.domain.RecentRatingChange;

/** 시설 관리자의 참가자 이력 조회. report·penalty는 다른 담당 도메인 테이블이라 읽기만 한다. */
@Mapper
public interface ParticipantHistoryMapper {

    // 동호회 신청 팀원은 본인 신청 행 없이 대표 신청의 참가자로만 들어올 수 있어 참가자 행도 인정한다.
    @Select("""
            SELECT u.nickname
            FROM app_user u
            WHERE u.user_id = #{userId}
              AND (EXISTS (SELECT 1
                           FROM match_application ma
                           WHERE ma.match_id = #{matchId}
                             AND ma.applicant_user_id = u.user_id)
                   OR EXISTS (SELECT 1
                              FROM match_participant mp
                              WHERE mp.match_id = #{matchId}
                                AND mp.user_id = u.user_id))
            """)
    Optional<String> findNicknameInMatch(@Param("matchId") Long matchId, @Param("userId") Long userId);

    @Select("""
            SELECT calculated_at AS calculatedAt,
                   final_delta AS finalDelta
            FROM sport_rating_history
            WHERE user_id = #{userId}
              AND sport_id = #{sportId}
            ORDER BY calculated_at DESC, history_id DESC
            LIMIT #{limit}
            """)
    List<RecentRatingChange> findRecentChanges(@Param("userId") Long userId,
                                               @Param("sportId") Long sportId,
                                               @Param("limit") int limit);

    // 시작 시각에 자동 취소된 경기도 마감 때 EXCLUDED가 되지만 참가자 책임이 아니어서, 치른 경기만 센다.
    @Select("""
            SELECT COUNT(DISTINCT mp.match_id)
            FROM match_participant mp
            JOIN social_match sm ON sm.match_id = mp.match_id
            WHERE mp.user_id = #{userId}
              AND mp.participation_status = #{confirmedStatus}
              AND sm.rating_status = #{excludedStatus}
              AND sm.status IN (#{matchedStatus}, #{closedStatus})
            """)
    int countExcludedMatches(@Param("userId") Long userId,
                             @Param("confirmedStatus") String confirmedStatus,
                             @Param("excludedStatus") String excludedStatus,
                             @Param("matchedStatus") String matchedStatus,
                             @Param("closedStatus") String closedStatus);

    @Select("""
            SELECT COUNT(*)
            FROM report
            WHERE target_user_id = #{userId}
              AND status = #{status}
            """)
    int countReports(@Param("userId") Long userId, @Param("status") String status);

    @Select("""
            SELECT penalty_type AS penaltyType,
                   end_at AS endAt
            FROM penalty
            WHERE target_user_id = #{userId}
              AND status = #{activeStatus}
              AND (end_at IS NULL OR end_at > #{now})
            ORDER BY start_at DESC, penalty_id DESC
            """)
    List<ActivePenalty> findActivePenalties(@Param("userId") Long userId,
                                            @Param("activeStatus") String activeStatus,
                                            @Param("now") LocalDateTime now);
}
