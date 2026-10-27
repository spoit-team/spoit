package co.kr.spoit.match.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.dto.MatchRow;

@Mapper
public interface MatchApplicationMapper {

    /** 매칭방 행 잠금 (없거나 시설이 삭제됐으면 null). 같은 방 신청·확정을 직렬화한다. */
    MatchRow lockMatch(@Param("matchId") long matchId);

    /** 이 회원이 이미 활성 신청 또는 활성 참가자인지 */
    boolean existsActiveEntry(@Param("matchId") long matchId,
                              @Param("userId") long userId,
                              @Param("applicationStatuses") List<String> applicationStatuses);

    /** 정원 점유 인원: 참가자(대기·확정) + 확정 대기 중인 개인 신청 */
    int countOccupiedSeats(@Param("matchId") long matchId,
                           @Param("participantStatuses") List<String> participantStatuses,
                           @Param("waitingStatus") String waitingStatus,
                           @Param("individualType") String individualType);

    int insert(MatchApplication application);

    MatchApplication findById(@Param("applicationId") long applicationId);

    MatchApplication findByReceiptNo(@Param("receiptNo") String receiptNo);

    /** 대기 → 확정. 이미 처리된 신청이면 0 */
    int markConfirmed(@Param("applicationId") long applicationId,
                      @Param("waitingStatus") String waitingStatus,
                      @Param("confirmedStatus") String confirmedStatus,
                      @Param("now") LocalDateTime now);

    /** 확정 시점에만 호출: application_id로 신청과 연결된 참가자 행 */
    int insertParticipant(@Param("applicationId") long applicationId,
                          @Param("matchId") long matchId,
                          @Param("userId") long userId,
                          @Param("status") String status,
                          @Param("entryMmr") BigDecimal entryMmr,
                          @Param("joinedAt") LocalDateTime joinedAt);
}
