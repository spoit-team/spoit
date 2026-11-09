package co.kr.spoit.facility.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.facility.dto.ApplicationCountSummary;
import co.kr.spoit.facility.dto.HourParticipantCount;
import co.kr.spoit.facility.dto.WeekdayHourParticipantCount;
import co.kr.spoit.facility.dto.WeekdayParticipantCount;

/**
 * 시설 관리자 예약 통계 조회용 읽기 전용 매퍼다.
 *
 * <p>모든 문장은 {@code facility_manager}와 {@code facility.deleted_at IS NULL}로 담당 범위를
 * 제한하고, 기간은 매칭방 {@code start_at}의 {@code [startAt, endAt]} 구간으로 자른다.
 * 집계 대상은 상태가 {@code 종료}인 매칭방뿐이며 모집 중·매칭 완료·취소는 모두 빠진다.
 * 인원 지표는 {@code match_participant}의 {@code participation_status = '확정'} 행만 세므로,
 * 같은 사람이 두 경기에 참여하면 두 번 집계되고 노쇼는 구분하지 못한다. 백분율·승인율 같은
 * 계산은 서비스가 맡고 이 매퍼는 단일 쿼리만 수행한다.</p>
 */
@Mapper
public interface FacilityStatsMapper {

    /** 선택 기간 내 종료 매칭방 수 */
    long countFinishedMatches(@Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /** 선택 기간 내 종료 매칭의 확정 참가자 행 수 (매칭별 인원 합) */
    long countConfirmedParticipants(@Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /** 종료 매칭의 대표 신청 행 기준 총계·개인·동호회·확정 수 */
    ApplicationCountSummary countApplications(@Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /** 요일별 종료 매칭 확정 참가 인원 (DAYOFWEEK 기준) */
    List<WeekdayParticipantCount> countParticipantsByWeekday(@Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /** 시작 시간대별 종료 매칭 확정 참가 인원 (HOUR 기준) */
    List<HourParticipantCount> countParticipantsByHour(@Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);

    /** 요일 × 시작 시간대별 종료 매칭 확정 참가 인원 (DAYOFWEEK·HOUR 기준) */
    List<WeekdayHourParticipantCount> countParticipantsByWeekdayAndHour(
            @Param("managerUserId") Long managerUserId,
            @Param("facilityId") Long facilityId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);
}
