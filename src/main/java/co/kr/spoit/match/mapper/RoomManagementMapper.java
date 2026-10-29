package co.kr.spoit.match.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.match.dto.RoomManagementApplicationRow;
import co.kr.spoit.match.dto.RoomManagementParticipantRow;
import co.kr.spoit.match.dto.RoomManagementQuery;
import co.kr.spoit.match.dto.RoomManagementRow;
import co.kr.spoit.match.dto.RoomManagementSport;

/**
 * 시설 관리자가 담당 시설의 매칭방을 조회하는 읽기 전용 매퍼다.
 *
 * <p>모든 문장은 {@code facility_manager}와 {@code facility.deleted_at IS NULL}로 담당 범위를
 * 제한한다. 조합·검증은 {@code RoomManagementService}가 맡고 이 매퍼는 단일 쿼리만 수행한다.</p>
 */
@Mapper
public interface RoomManagementMapper {

    /** 담당 시설의 매칭방 목록 (모든 상태·기간) */
    List<RoomManagementRow> findRooms(RoomManagementQuery query);

    /** 담당 시설의 매칭방 한 건. 없거나 다른 관리자 시설·삭제된 시설이면 null */
    RoomManagementRow findRoom(RoomManagementQuery query);

    /** 담당 시설이 제공하는 종목 선택지 (매칭방이 없는 종목도 포함) */
    List<RoomManagementSport> findSports(@Param("managerUserId") Long managerUserId);

    /** 매칭방 참가 신청 내역 (접수 순) */
    List<RoomManagementApplicationRow> findApplications(RoomManagementQuery query);

    /** 매칭방 참가자 명단 (참가 순) */
    List<RoomManagementParticipantRow> findParticipants(RoomManagementQuery query);
}
