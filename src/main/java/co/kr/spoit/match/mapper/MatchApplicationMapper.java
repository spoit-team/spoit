package co.kr.spoit.match.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.dto.GroupMemberRow;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.MyApplicationRow;
import co.kr.spoit.match.dto.TeamClubRow;
import co.kr.spoit.match.dto.TeamMemberRow;

@Mapper
public interface MatchApplicationMapper {

    /**
     * 신청 전 시설 잠금 대상. 매칭방이 없으면 null.
     * 시설 행을 잠그기 전에 후보 id만 읽어, 잠금 순서를 시설 → 매칭방으로 맞춘다.
     */
    Long findFacilityId(@Param("matchId") long matchId);

    /** 매칭방 행 잠금 (없거나 시설이 삭제됐으면 null). 같은 방 신청·확정을 직렬화한다. */
    MatchRow lockMatch(@Param("matchId") long matchId);

    /** 이 회원이 이미 활성 신청 또는 활성 참가자인지 */
    boolean existsActiveEntry(@Param("matchId") long matchId,
                              @Param("userId") long userId,
                              @Param("applicationStatuses") List<String> applicationStatuses);

    /**
     * 정원 점유 인원: 참가자(대기·확정) + 확정 대기 중인 신청.
     * 팀 신청도 팀원마다 신청 행이 있으므로 대기 신청 행 수가 곧 대기 인원이다.
     */
    int countOccupiedSeats(@Param("matchId") long matchId,
                           @Param("participantStatuses") List<String> participantStatuses,
                           @Param("waitingStatus") String waitingStatus);

    int insert(MatchApplication application);

    /** RES-04 팀 신청 대표 행: INSERT 뒤 묶음 id를 자기 자신으로 채운다. */
    int markGroupLeader(@Param("applicationId") long applicationId);

    MatchApplication findById(@Param("applicationId") long applicationId);

    MatchApplication findByReceiptNo(@Param("receiptNo") String receiptNo);

    /** 대기 → 확정. 이미 처리된 신청이면 0 */
    int markConfirmed(@Param("applicationId") long applicationId,
                      @Param("waitingStatus") String waitingStatus,
                      @Param("confirmedStatus") String confirmedStatus,
                      @Param("now") LocalDateTime now);

    /** RES-05 신청 취소: 활성(대기·확정) 신청만 바꾼다. 이미 취소·반려됐으면 0 */
    int markCancelled(@Param("applicationId") long applicationId,
                      @Param("activeStatuses") List<String> activeStatuses,
                      @Param("cancelledStatus") String cancelledStatus,
                      @Param("cancelledBy") long cancelledBy,
                      @Param("now") LocalDateTime now);

    /** 취소한 신청으로 들어간 활성 참가자 행도 함께 취소해 정원을 돌려준다. */
    int cancelParticipants(@Param("applicationId") long applicationId,
                           @Param("occupyingStatuses") List<String> occupyingStatuses,
                           @Param("cancelledStatus") String cancelledStatus,
                           @Param("now") LocalDateTime now);

    /** RES-04 팀 신청 묶음 전체 취소. 바뀐 행 수 (모두 이미 취소됐으면 0) */
    int markGroupCancelled(@Param("groupApplicationId") long groupApplicationId,
                           @Param("activeStatuses") List<String> activeStatuses,
                           @Param("cancelledStatus") String cancelledStatus,
                           @Param("cancelledBy") long cancelledBy,
                           @Param("now") LocalDateTime now);

    /** 묶음에 속한 신청으로 들어간 활성 참가자 행을 모두 취소한다. */
    int cancelGroupParticipants(@Param("groupApplicationId") long groupApplicationId,
                                @Param("occupyingStatuses") List<String> occupyingStatuses,
                                @Param("cancelledStatus") String cancelledStatus,
                                @Param("now") LocalDateTime now);

    /** 같은 묶음의 신청 행 (대표가 먼저, 그다음 접수 순) */
    List<GroupMemberRow> findGroupMembers(@Param("groupApplicationId") long groupApplicationId);

    /** RES-06 내 신청 내역: 내가 낸 신청 + 내가 참가자로 들어간 신청 (경기 시작 순) */
    List<MyApplicationRow> findMyApplications(@Param("userId") long userId,
                                              @Param("participantStatuses") List<String> participantStatuses);

    /** 확정 시점에만 호출: application_id로 신청과 연결된 참가자 행 */
    int insertParticipant(@Param("applicationId") long applicationId,
                          @Param("matchId") long matchId,
                          @Param("userId") long userId,
                          @Param("status") String status,
                          @Param("entryMmr") BigDecimal entryMmr,
                          @Param("joinedAt") LocalDateTime joinedAt);

    /* ---------------- RES-04 팀 신청 대상 ---------------- */

    /** 이 회원이 활성 회원인 해체되지 않은 동호회 중 종목이 같은 것 */
    List<TeamClubRow> findTeamClubs(@Param("userId") long userId,
                                    @Param("sportId") long sportId);

    /** 이 회원이 활성 회원인 해체되지 않은 동호회 한 곳, 아니면 null */
    TeamClubRow findTeamClub(@Param("clubId") long clubId,
                             @Param("userId") long userId);

    /**
     * 동호회들의 활성 회원 (탈퇴 회원 제외). 매치 종목 레이팅과 이 매치에 이미 들어갔는지를 함께 읽는다.
     * 동호회장이 먼저, 그다음 가입 순.
     */
    List<TeamMemberRow> findTeamMembers(@Param("clubIds") List<Long> clubIds,
                                        @Param("matchId") long matchId,
                                        @Param("sportId") long sportId,
                                        @Param("applicationStatuses") List<String> applicationStatuses);
}
