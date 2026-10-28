package co.kr.spoit.mat.mapper;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import co.kr.spoit.mat.domain.AssignmentMatch;

@Mapper
public interface MatchAssignmentMapper {

    /** 삭제된 시설의 매칭방은 조회하지 않는다. */
    @Select("""
            SELECT sm.match_id AS matchId,
                   sm.facility_id AS facilityId,
                   sm.start_at AS startAt,
                   sm.status AS status,
                   sm.team_a_capacity AS teamACapacity,
                   sm.team_b_capacity AS teamBCapacity,
                   sm.assignment_confirmed_at AS assignmentConfirmedAt
            FROM social_match sm
            JOIN facility f ON f.facility_id = sm.facility_id
                           AND f.deleted_at IS NULL
            WHERE sm.match_id = #{matchId}
            """)
    Optional<AssignmentMatch> findAssignmentMatch(@Param("matchId") Long matchId);

    @Select("""
            SELECT EXISTS (SELECT 1
                           FROM facility_manager
                           WHERE facility_id = #{facilityId}
                             AND user_id = #{userId})
            """)
    boolean isFacilityManager(@Param("facilityId") Long facilityId, @Param("userId") Long userId);
}
