package co.kr.spoit.match.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.match.domain.MatchApplication;
import co.kr.spoit.match.dto.GroupMemberRow;
import co.kr.spoit.match.dto.MatchRow;
import co.kr.spoit.match.dto.MatchTitleRow;
import co.kr.spoit.match.dto.MyApplicationRow;
import co.kr.spoit.match.dto.TeamClubRow;
import co.kr.spoit.match.dto.TeamMemberRow;
import co.kr.spoit.match.dto.TimeConflictRow;

@Mapper
public interface MatchApplicationMapper {

    /**
     * 신청 전 시설 잠금 대상. 매칭방이 없으면 null.
     * 시설 행을 잠그기 전에 후보 id만 읽어, 잠금 순서를 시설 → 매칭방으로 맞춘다.
     */
    Long findFacilityId(@Param("matchId") long matchId);

    /**
     * 회원들이 이미 들어가 있는(활성 신청·참가) 다른 매치 중 이 매치와 시간이 겹치는 것.
     * 끝나는 시각에 바로 시작하는 연속 매치는 겹치지 않는다. 취소된 매치는 제외한다.
     */
    List<TimeConflictRow> findTimeConflicts(@Param("matchId") long matchId,
                                            @Param("userIds") List<Long> userIds,
                                            @Param("applicationStatuses") List<String> applicationStatuses,
                                            @Param("cancelledMatchStatus") String cancelledMatchStatus);

    /** 매칭방 행 잠금 (없거나 시설이 삭제됐으면 null). 같은 방 신청·확정을 직렬화한다. */
    MatchRow lockMatch(@Param("matchId") long matchId);

    /** 시작 시각이 지났는데 결제한 대기 신청이 남은 매칭방 (취소된 매칭방 제외) */
    List<Long> findStartedMatchIdsWithWaiting(@Param("now") LocalDateTime now,
                                              @Param("waitingStatus") String waitingStatus,
                                              @Param("cancelledMatchStatus") String cancelledMatchStatus);

    /** 결제한 대기 신청 */
    List<MatchApplication> findWaitingApplications(@Param("matchId") long matchId,
                                                   @Param("waitingStatus") String waitingStatus);

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

    /** 대기 → 반려. 이미 처리된 신청이면 0 */
    int markRejected(@Param("applicationId") long applicationId,
                     @Param("waitingStatus") String waitingStatus,
                     @Param("rejectedStatus") String rejectedStatus);

    /** 신청이 이 시설 관리자가 담당하는(삭제되지 않은) 시설의 매칭방 것이면 1, 아니면 0 */
    int countManagedApplication(@Param("applicationId") long applicationId,
                                @Param("managerUserId") long managerUserId);

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

    /**
     * 팀원 일부 취소 전 묶음 행 잠금 (findGroupMembers와 같은 순서).
     * 잠금 읽기라 트랜잭션 앞부분의 스냅숏이 아닌 최신 상태를 읽는다.
     */
    List<GroupMemberRow> lockGroupMembers(@Param("groupApplicationId") long groupApplicationId);

    /**
     * 대표가 빠질 때 남은 팀원 행을 새 대표 행의 묶음으로 옮긴다.
     * 아직 이전 묶음에 있는 행만 바꾸며, 바뀐 행 수를 돌려준다.
     */
    int moveGroup(@Param("applicationIds") List<Long> applicationIds,
                  @Param("fromGroupApplicationId") long fromGroupApplicationId,
                  @Param("toGroupApplicationId") long toGroupApplicationId);

    /** RES-06 내 신청 내역: 내가 낸 신청 + 내가 참가자로 들어간 신청 (경기 시작 순) */
    List<MyApplicationRow> findMyApplications(@Param("userId") long userId,
                                              @Param("participantStatuses") List<String> participantStatuses);

    /**
     * 회원 탈퇴를 막는 매치 신청(개인·동호회)의 매치 (매치별 한 번, 같은 제목의 다른 매치도 각각 포함).
     *
     * <p>"매칭 완료"는 경기 진행 중 상태이며, 경기 종료 후 스케줄러가 "종료"로 바꾼다.
     * 따라서 모집 중·매칭 완료만 차단하고 종료·취소된 매치는 막지 않는다.</p>
     */
    List<MatchTitleRow> findActiveIndividualMatchTitles(@Param("userId") long userId,
                                                        @Param("applicationStatuses") List<String> applicationStatuses,
                                                        @Param("matchStatuses") List<String> matchStatuses);

    /** 확정 시점에만 호출: application_id로 신청과 연결된 참가자 행 */
    int insertParticipant(@Param("applicationId") long applicationId,
                          @Param("matchId") long matchId,
                          @Param("userId") long userId,
                          @Param("status") String status,
                          @Param("entryMmr") BigDecimal entryMmr,
                          @Param("joinedAt") LocalDateTime joinedAt);

    /* ---------------- 알림 수신자 ---------------- */

    /** 매칭방 담당 시설의 관리자 회원 번호 (알림 수신자) */
    List<Long> findFacilityManagerUserIds(@Param("matchId") long matchId);

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
