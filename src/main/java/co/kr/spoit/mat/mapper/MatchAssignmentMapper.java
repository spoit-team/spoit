package co.kr.spoit.mat.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import co.kr.spoit.mat.domain.AssignmentMatch;
import co.kr.spoit.mat.domain.AssignmentMember;

@Mapper
public interface MatchAssignmentMapper {

    /** 삭제된 시설의 매칭방은 조회하지 않는다. */
    String ASSIGNMENT_MATCH_QUERY = """
            SELECT sm.match_id AS matchId,
                   sm.facility_id AS facilityId,
                   sm.start_at AS startAt,
                   sm.end_at AS endAt,
                   sm.status AS status,
                   sm.team_a_capacity AS teamACapacity,
                   sm.team_b_capacity AS teamBCapacity,
                   sm.assignment_method AS assignmentMethod,
                   sm.assignment_confirmed_at AS assignmentConfirmedAt,
                   sm.captain_a_participant_id AS captainAParticipantId,
                   sm.captain_b_participant_id AS captainBParticipantId
            FROM social_match sm
            JOIN facility f ON f.facility_id = sm.facility_id
                           AND f.deleted_at IS NULL
            WHERE sm.match_id = #{matchId}
            """;

    @Select(ASSIGNMENT_MATCH_QUERY)
    Optional<AssignmentMatch> findAssignmentMatch(@Param("matchId") Long matchId);

    // RES 신청 접수·확정·취소와 같이 매칭방 행부터 잠근다. 참가자 행은 이 잠금 아래에서만 바뀐다.
    @Select(ASSIGNMENT_MATCH_QUERY + "FOR UPDATE OF sm")
    Optional<AssignmentMatch> lockAssignmentMatch(@Param("matchId") Long matchId);

    @Select("""
            SELECT sm.match_id
            FROM social_match sm
            JOIN facility f ON f.facility_id = sm.facility_id
                           AND f.deleted_at IS NULL
            WHERE sm.status = #{recruitingStatus}
              AND sm.start_at <= #{now}
            ORDER BY sm.start_at, sm.match_id
            """)
    List<Long> findStartableMatchIds(@Param("recruitingStatus") String recruitingStatus,
                                     @Param("now") LocalDateTime now);

    @Update("""
            UPDATE social_match
            SET status = #{cancelledStatus},
                cancelled_at = #{cancelledAt}
            WHERE match_id = #{matchId}
              AND status = #{recruitingStatus}
            """)
    int markCancelled(@Param("matchId") Long matchId,
                      @Param("cancelledAt") LocalDateTime cancelledAt,
                      @Param("cancelledStatus") String cancelledStatus,
                      @Param("recruitingStatus") String recruitingStatus);

    @Select("""
            SELECT EXISTS (SELECT 1
                           FROM facility_manager
                           WHERE facility_id = #{facilityId}
                             AND user_id = #{userId})
            """)
    boolean isFacilityManager(@Param("facilityId") Long facilityId, @Param("userId") Long userId);

    @Select("""
            SELECT mp.participant_id AS participantId,
                   u.nickname AS nickname,
                   mp.entry_mmr AS entryMmr,
                   mp.match_side AS matchSide
            FROM match_participant mp
            JOIN app_user u ON u.user_id = mp.user_id
            WHERE mp.match_id = #{matchId}
              AND mp.participation_status = #{confirmedStatus}
            ORDER BY mp.participant_id
            """)
    List<AssignmentMember> findConfirmedMembers(@Param("matchId") Long matchId,
                                                @Param("confirmedStatus") String confirmedStatus);

    @Update("""
            <script>
            UPDATE match_participant
            SET match_side = #{side}
            WHERE match_id = #{matchId}
              AND participation_status = #{confirmedStatus}
              AND participant_id IN
                  <foreach collection="participantIds" item="participantId" open="(" separator="," close=")">
                      #{participantId}
                  </foreach>
            </script>
            """)
    int updateMatchSide(@Param("matchId") Long matchId,
                        @Param("participantIds") List<Long> participantIds,
                        @Param("side") String side,
                        @Param("confirmedStatus") String confirmedStatus);

    @Update("""
            UPDATE match_participant
            SET match_side = NULL
            WHERE match_id = #{matchId}
              AND participation_status = #{confirmedStatus}
            """)
    int clearMatchSides(@Param("matchId") Long matchId, @Param("confirmedStatus") String confirmedStatus);

    @Update("""
            UPDATE social_match
            SET status = #{matchedStatus},
                assignment_method = #{method},
                assigned_by = #{assignedBy},
                assignment_confirmed_at = #{confirmedAt},
                captain_a_participant_id = #{captainA},
                captain_b_participant_id = #{captainB}
            WHERE match_id = #{matchId}
              AND status = #{recruitingStatus}
            """)
    int markAssigned(@Param("matchId") Long matchId,
                     @Param("method") String method,
                     @Param("assignedBy") Long assignedBy,
                     @Param("confirmedAt") LocalDateTime confirmedAt,
                     @Param("captainA") Long captainA,
                     @Param("captainB") Long captainB,
                     @Param("matchedStatus") String matchedStatus,
                     @Param("recruitingStatus") String recruitingStatus);

    @Update("""
            UPDATE social_match
            SET status = #{recruitingStatus},
                assignment_method = #{manualMethod},
                assigned_by = NULL,
                assignment_confirmed_at = NULL,
                captain_a_participant_id = NULL,
                captain_b_participant_id = NULL
            WHERE match_id = #{matchId}
              AND status = #{matchedStatus}
            """)
    int clearAssignment(@Param("matchId") Long matchId,
                        @Param("manualMethod") String manualMethod,
                        @Param("recruitingStatus") String recruitingStatus,
                        @Param("matchedStatus") String matchedStatus);
}
