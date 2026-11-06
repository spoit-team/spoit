package co.kr.spoit.facility.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.facility.dto.ApplicationCountSummary;
import co.kr.spoit.facility.dto.HourRoomCount;
import co.kr.spoit.facility.dto.WeekdayRoomCount;

/**
 * 시설 관리자 예약 통계 조회용 읽기 전용 매퍼다.
 *
 * <p>모든 문장은 {@code facility_manager}와 {@code facility.deleted_at IS NULL}로 담당 범위를
 * 제한하고, 기간은 매칭방 {@code start_at}의 {@code [startAt, endAt]} 구간으로 자른다.
 * 취소된 매칭방은 매칭방 수·참가 인원·요일·시간대 집계에서 제외한다. 백분율·승인율 같은
 * 계산은 서비스가 맡고 이 매퍼는 단일 쿼리만 수행한다.</p>
 */
@Mapper
public interface FacilityStatsMapper {

    /** 선택 기간 내 취소 제외 매칭방 수 */
    long countMatches(@Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /** 대상 매칭방의 대기·확정 참가자 합계 */
    long sumParticipants(@Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /** 대상 매칭방의 대표 신청 행 기준 총계·개인·동호회·확정 수 */
    ApplicationCountSummary countApplications(@Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /** 요일별 취소 제외 매칭방 수 (DAYOFWEEK 기준) */
    List<WeekdayRoomCount> countByWeekday(@Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /** 시간대별 취소 제외 매칭방 수 (HOUR 기준) */
    List<HourRoomCount> countByHour(@Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);
}
