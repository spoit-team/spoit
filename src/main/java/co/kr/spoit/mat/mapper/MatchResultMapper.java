package co.kr.spoit.mat.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import co.kr.spoit.mat.domain.ReminderTarget;
import co.kr.spoit.mat.domain.ResultMatch;
import co.kr.spoit.mat.domain.ResultPageMatch;
import co.kr.spoit.mat.domain.ResultSubmission;
import co.kr.spoit.mat.domain.ResultTeamMember;

@Mapper
public interface MatchResultMapper {

    // 취소·종료된 경기는 시설 삭제를 막지 않는다. 시설이 삭제돼도 취소 경기의 마감 처리와 종료 경기의 결과 조회는
    // 해야 해서 시설 조건을 두지 않는다.
    String RESULT_MATCH_QUERY = """
            SELECT match_id AS matchId,
                   facility_id AS facilityId,
                   sport_id AS sportId,
                   start_at AS startAt,
                   end_at AS endAt,
                   status AS status,
                   captain_a_participant_id AS captainAParticipantId,
                   captain_b_participant_id AS captainBParticipantId,
                   result_status AS resultStatus,
                   final_result AS finalResult,
                   rating_status AS ratingStatus
            FROM social_match
            WHERE match_id = #{matchId}
            """;

    @Select(RESULT_MATCH_QUERY)
    Optional<ResultMatch> findResultMatch(@Param("matchId") Long matchId);

    // 제출 행과 결과·레이팅 상태는 이 잠금 아래에서만 바뀐다.
    @Select(RESULT_MATCH_QUERY + "FOR UPDATE")
    Optional<ResultMatch> lockResultMatch(@Param("matchId") Long matchId);

    // 결과 조회 API와 같은 이유로 삭제된 시설의 경기도 보여 준다.
    @Select("""
            SELECT sm.match_id AS matchId,
                   sm.title AS title,
                   sm.start_at AS startAt,
                   sm.end_at AS endAt,
                   sm.status AS status,
                   f.name AS facilityName,
                   s.name AS sportName,
                   sm.captain_a_participant_id AS captainAParticipantId,
                   sm.captain_b_participant_id AS captainBParticipantId
            FROM social_match sm
            JOIN facility f ON f.facility_id = sm.facility_id
            JOIN sport s ON s.sport_id = sm.sport_id
            WHERE sm.match_id = #{matchId}
            """)
    Optional<ResultPageMatch> findResultPageMatch(@Param("matchId") Long matchId);

    @Select("""
            SELECT mp.participant_id AS participantId,
                   mp.user_id AS userId,
                   u.nickname AS nickname,
                   mp.match_side AS matchSide
            FROM match_participant mp
            JOIN app_user u ON u.user_id = mp.user_id
            WHERE mp.match_id = #{matchId}
              AND mp.participation_status = #{confirmedStatus}
            ORDER BY mp.match_side, mp.participant_id
            """)
    List<ResultTeamMember> findConfirmedTeamMembers(@Param("matchId") Long matchId,
                                                    @Param("confirmedStatus") String confirmedStatus);

    @Select("""
            SELECT match_id
            FROM social_match
            WHERE status = #{matchedStatus}
              AND end_at <= #{now}
              AND rating_status IN (#{appliedStatus}, #{excludedStatus})
            ORDER BY end_at, match_id
            """)
    List<Long> findEndedMatchIds(@Param("matchedStatus") String matchedStatus,
                                 @Param("appliedStatus") String appliedStatus,
                                 @Param("excludedStatus") String excludedStatus,
                                 @Param("now") LocalDateTime now);

    @Update("""
            UPDATE social_match
            SET status = #{closedStatus}
            WHERE match_id = #{matchId}
              AND status = #{matchedStatus}
              AND end_at <= #{now}
              AND rating_status IN (#{appliedStatus}, #{excludedStatus})
            """)
    int markEnded(@Param("matchId") Long matchId,
                  @Param("closedStatus") String closedStatus,
                  @Param("matchedStatus") String matchedStatus,
                  @Param("appliedStatus") String appliedStatus,
                  @Param("excludedStatus") String excludedStatus,
                  @Param("now") LocalDateTime now);

    @Select("""
            SELECT match_id
            FROM social_match
            WHERE rating_status = #{waitingStatus}
              AND end_at <= #{endedBefore}
            ORDER BY end_at, match_id
            """)
    List<Long> findExpiredMatchIds(@Param("waitingStatus") String waitingStatus,
                                   @Param("endedBefore") LocalDateTime endedBefore);

    @Select("""
            SELECT submission_id AS submissionId,
                   captain_participant_id AS captainParticipantId,
                   reported_result AS reportedResult,
                   submission_status AS submissionStatus,
                   submitted_at AS submittedAt
            FROM match_result_submission
            WHERE match_id = #{matchId}
              AND submission_status <> #{replacedStatus}
            ORDER BY submission_id
            """)
    List<ResultSubmission> findActiveSubmissions(@Param("matchId") Long matchId,
                                                 @Param("replacedStatus") String replacedStatus);

    @Insert("""
            INSERT INTO match_result_submission (
                match_id, captain_participant_id, reported_result, submission_status, submitted_at
            ) VALUES (
                #{matchId}, #{captainParticipantId}, #{reportedResult}, #{submissionStatus}, #{submittedAt}
            )
            """)
    void insertSubmission(@Param("matchId") Long matchId,
                          @Param("captainParticipantId") Long captainParticipantId,
                          @Param("reportedResult") String reportedResult,
                          @Param("submissionStatus") String submissionStatus,
                          @Param("submittedAt") LocalDateTime submittedAt);

    @Update("""
            UPDATE match_result_submission
            SET submission_status = #{submissionStatus}
            WHERE submission_id = #{submissionId}
            """)
    int updateSubmissionStatus(@Param("submissionId") Long submissionId,
                               @Param("submissionStatus") String submissionStatus);

    @Update("""
            UPDATE social_match
            SET result_status = #{resultStatus},
                final_result = #{finalResult},
                result_confirmed_at = #{confirmedAt}
            WHERE match_id = #{matchId}
            """)
    int markResult(@Param("matchId") Long matchId,
                   @Param("resultStatus") String resultStatus,
                   @Param("finalResult") String finalResult,
                   @Param("confirmedAt") LocalDateTime confirmedAt);

    @Update("""
            UPDATE social_match
            SET rating_status = #{ratingStatus},
                rating_processed_at = #{processedAt},
                result_reviewed_by = #{reviewedBy}
            WHERE match_id = #{matchId}
              AND rating_status = #{currentStatus}
            """)
    int markRatingProcessed(@Param("matchId") Long matchId,
                            @Param("ratingStatus") String ratingStatus,
                            @Param("processedAt") LocalDateTime processedAt,
                            @Param("reviewedBy") Long reviewedBy,
                            @Param("currentStatus") String currentStatus);

    // 제출 행이 하나라도 있는 팀장은 뺀다. 불일치 상태의 팀장은 이미 불일치 알림을 받았다.
    @Select("""
            SELECT sm.match_id AS matchId,
                   mp.user_id AS userId
            FROM social_match sm
            JOIN match_participant mp
              ON mp.match_id = sm.match_id
             AND mp.participant_id IN (sm.captain_a_participant_id, sm.captain_b_participant_id)
            WHERE sm.status IN (#{matchedStatus}, #{closedStatus})
              AND sm.rating_status = #{waitingStatus}
              AND sm.end_at > #{endedAfter}
              AND sm.end_at <= #{endedUntil}
              AND mp.participation_status = #{confirmedStatus}
              AND NOT EXISTS (SELECT 1
                              FROM match_result_submission s
                              WHERE s.match_id = sm.match_id
                                AND s.captain_participant_id = mp.participant_id)
            ORDER BY sm.match_id, mp.user_id
            """)
    List<ReminderTarget> findReminderTargets(@Param("matchedStatus") String matchedStatus,
                                             @Param("closedStatus") String closedStatus,
                                             @Param("waitingStatus") String waitingStatus,
                                             @Param("confirmedStatus") String confirmedStatus,
                                             @Param("endedAfter") LocalDateTime endedAfter,
                                             @Param("endedUntil") LocalDateTime endedUntil);

    // 결과 API·결과 화면과 같이 확정 참가 중인 팀장만 본다.
    @Select("""
            SELECT sm.match_id
            FROM social_match sm
            JOIN match_participant mp
              ON mp.match_id = sm.match_id
             AND mp.participant_id IN (sm.captain_a_participant_id, sm.captain_b_participant_id)
            WHERE mp.user_id = #{userId}
              AND mp.participation_status = #{confirmedStatus}
              AND sm.status = #{matchedStatus}
              AND sm.rating_status = #{waitingStatus}
              AND sm.end_at > #{endedAfter}
              AND sm.end_at <= #{endedUntil}
            ORDER BY sm.match_id
            """)
    List<Long> findResultSubmittableMatchIds(@Param("userId") Long userId,
                                             @Param("matchedStatus") String matchedStatus,
                                             @Param("waitingStatus") String waitingStatus,
                                             @Param("confirmedStatus") String confirmedStatus,
                                             @Param("endedAfter") LocalDateTime endedAfter,
                                             @Param("endedUntil") LocalDateTime endedUntil);
}
