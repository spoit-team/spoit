package co.kr.spoit.match.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.match.dto.RoomCommand;
import co.kr.spoit.match.dto.RoomEditTarget;

/**
 * 시설 관리자의 매칭방 생성·수정·취소 매퍼다.
 *
 * <p>대상 조회는 모두 {@code facility_manager}와 {@code facility.deleted_at IS NULL}로 담당 범위를
 * 제한한다. 검증과 잠금 순서는 {@code RoomCommandService}가 맡는다.</p>
 */
@Mapper
public interface RoomCommandMapper {

    /** 담당자의 활성 시설이 해당 종목을 제공하면 1, 아니면 0 */
    int countManagedFacilitySport(@Param("managerUserId") Long managerUserId,
                                  @Param("facilityId") Long facilityId,
                                  @Param("sportId") Long sportId);

    /** 담당 시설의 매칭방 현재 값. 없거나 담당 밖·삭제된 시설이면 null */
    RoomEditTarget findRoom(@Param("managerUserId") Long managerUserId,
                            @Param("matchId") Long matchId);

    /** {@link #findRoom}과 같은 범위에서 매칭방 행을 {@code FOR UPDATE}로 잠그고 읽는다. */
    RoomEditTarget findRoomForUpdate(@Param("managerUserId") Long managerUserId,
                                     @Param("matchId") Long matchId);

    /**
     * 정원을 차지했거나 확정을 기다리는 인원. 참가자 행(대기·확정)과 대기 신청(개인·팀 모두)의 합이다.
     * 확정 신청은 참가자 행으로 이미 세므로 더하지 않는다.
     */
    int countReserved(@Param("matchId") Long matchId,
                      @Param("participationStatuses") List<String> participationStatuses,
                      @Param("waitingApplicationStatus") String waitingApplicationStatus);

    void insertRoom(RoomCommand command);

    /** 시설·종목·일정까지 모두 바꾼다. 신청자가 없는 방에만 쓴다. */
    int updateRoom(RoomCommand command);

    /** 제목·정원·참가비·실력 수준만 바꾼다. 신청자가 있는 방에 쓴다. */
    int updateRoomDetails(RoomCommand command);

    /** {@code fromStatuses} 상태인 방만 취소 상태로 바꾼다. */
    int cancelRoom(@Param("matchId") Long matchId,
                   @Param("cancelledStatus") String cancelledStatus,
                   @Param("fromStatuses") List<String> fromStatuses,
                   @Param("now") LocalDateTime now);

    /** 활성 신청을 취소 상태로 바꾸고 처리자를 기록한다. */
    int cancelApplications(@Param("matchId") Long matchId,
                           @Param("cancelledStatus") String cancelledStatus,
                           @Param("fromStatuses") List<String> fromStatuses,
                           @Param("cancelledBy") Long cancelledBy,
                           @Param("now") LocalDateTime now);

    /** 활성 참가자를 취소 상태로 바꾼다. */
    int cancelParticipants(@Param("matchId") Long matchId,
                           @Param("cancelledStatus") String cancelledStatus,
                           @Param("fromStatuses") List<String> fromStatuses,
                           @Param("now") LocalDateTime now);
}
