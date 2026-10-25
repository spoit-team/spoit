package co.kr.spoit.mat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import co.kr.spoit.mat.domain.MatchParticipant;

@Mapper
public interface MatchParticipantMapper {

    /** 취소와 반려를 포함한 경기의 전체 참가자 행을 조회한다. */
    @Select("""
            SELECT participant_id AS participantId,
                   application_id AS applicationId,
                   match_id AS matchId,
                   user_id AS userId,
                   participation_status AS participationStatus,
                   match_side AS matchSide,
                   entry_mmr AS entryMmr,
                   joined_at AS joinedAt,
                   cancelled_at AS cancelledAt
            FROM match_participant
            WHERE match_id = #{matchId}
            ORDER BY participant_id
            """)
    List<MatchParticipant> findByMatchId(@Param("matchId") Long matchId);
}
