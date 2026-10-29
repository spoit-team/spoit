package co.kr.spoit.facility.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.facility.domain.DateOverride;
import co.kr.spoit.facility.domain.FacilityClosure;
import co.kr.spoit.facility.domain.FacilitySportCondition;
import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.domain.OperatingRule;

/**
 * 예약 운영 일정·모집 조건·휴관의 단일 조회·변경 SQL을 담당한다.
 *
 * <p>구성·정규화 같은 흐름 로직은 서비스가 담당하고 이 매퍼는 한 번에 한 쿼리만 수행한다.
 * 잠금이 필요한 읽기는 {@code FOR UPDATE}를 쓰며, 호출 서비스가 이미 시설 잠금을 가진
 * 트랜잭션에서 최신 값을 다시 읽는다.</p>
 */
@Mapper
public interface FacilityScheduleMapper {

    /** 시설의 요일별 운영 규칙을 잠그고 최신 값으로 조회한다(요일 순). */
    List<OperatingRule> selectWeeklyRulesForUpdate(@Param("facilityId") long facilityId);

    /** 시설의 요일별 운영 규칙을 조회한다(읽기 전용 화면용). */
    List<OperatingRule> selectWeeklyRules(@Param("facilityId") long facilityId);

    /** 시설의 날짜별 예외 헤더를 잠그고 조회한다. */
    List<DateOverride> selectDateOverridesForUpdate(@Param("facilityId") long facilityId);

    /** 시설의 날짜별 예외 헤더를 조회한다(읽기 전용 화면용). */
    List<DateOverride> selectDateOverrides(@Param("facilityId") long facilityId);

    /** 예외 헤더에 속한 1시간 슬롯 시각(0~23) 전체를 조회한다. */
    List<Integer> selectDateOverrideHours(@Param("overrideId") long overrideId);

    /**
     * 예외 헤더에 속한 슬롯을 잠금 current read로 조회한다.
     *
     * <p>호출자가 이미 만든 REPEATABLE READ 스냅샷의 이전 값을 쓰지 않도록, 검증 경로는
     * 반드시 이 메서드를 쓴다.</p>
     */
    List<Integer> selectDateOverrideHoursForUpdate(@Param("overrideId") long overrideId);

    /** 적용 중인 휴관을 잠그고 조회한다. 취소된 휴관은 제외한다. */
    List<FacilityClosure> selectActiveClosuresForUpdate(@Param("facilityId") long facilityId);

    /** 적용 중인 휴관을 조회한다(읽기 전용 화면용). 취소된 휴관은 제외한다. */
    List<FacilityClosure> selectActiveClosures(@Param("facilityId") long facilityId);

    /** 적용 중인 휴관을 시설 단위로 잠그고 조회한다(취소 대상 확인용). */
    FacilityClosure selectClosureForUpdate(@Param("facilityId") long facilityId,
            @Param("scheduleChangeId") long scheduleChangeId);

    /** 등록된 휴관 전체를 조회한다. 취소된 휴관도 상태 표시를 위해 포함한다. */
    List<FacilityClosure> selectClosures(@Param("facilityId") long facilityId);

    /** 휴관 한 건을 ID로 조회한다(담당 시설 확인 전 대상 탐색용). 없으면 {@code null}이다. */
    FacilityClosure selectClosureById(@Param("scheduleChangeId") long scheduleChangeId);

    /** 대상 구간과 겹치는 적용 중 휴관을 잠그고 전체 조회한다. */
    List<FacilityClosure> selectActiveClosuresOverlappingForUpdate(@Param("facilityId") long facilityId,
            @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    /** 지정 상태의 매칭방 시간 구간을 잠그고 조회한다. 점유·충돌 계산의 단일 원천이다. */
    List<MatchPeriod> selectMatchPeriodsForUpdate(@Param("facilityId") long facilityId,
            @Param("statuses") List<String> statuses);

    /** 대상 구간과 겹치는 지정 상태의 매칭방 전체를 잠그고 조회한다. */
    List<MatchPeriod> selectOverlappingMatchPeriodsForUpdate(@Param("facilityId") long facilityId,
            @Param("statuses") List<String> statuses,
            @Param("startAt") LocalDateTime startAt, @Param("endAt") LocalDateTime endAt);

    /** 요일별 운영 규칙 7행을 한 번에 저장한다. 기존 행은 서비스가 먼저 지운다. */
    int insertWeeklyRules(@Param("facilityId") long facilityId, @Param("rows") List<OperatingRule> rows);

    /** 시설의 요일별 운영 규칙을 전량 삭제한다. */
    int deleteWeeklyRules(@Param("facilityId") long facilityId);

    /** 날짜별 예외 슬롯을 전량 삭제한다. */
    int deleteDateOverrideSlots(@Param("overrideId") long overrideId);

    /** 날짜별 예외 헤더를 삭제한다. 슬롯이 먼저 지워져야 한다. */
    int deleteDateOverride(@Param("overrideId") long overrideId);

    /** 날짜별 예외 헤더를 저장하고 생성 ID를 도메인 객체에 채운다. */
    int insertDateOverride(@Param("override") DateOverride override);

    /** 날짜별 예외 1시간 슬롯을 저장한다. 0건이면 그 날짜 전체 휴무다. */
    int insertDateOverrideHours(@Param("overrideId") long overrideId, @Param("hours") List<Integer> hours);

    /** 시설·종목 매핑의 기본 최대 인원을 잠그고 조회한다. 없으면 {@code null}이다. */
    Integer selectSportCapacityForUpdate(@Param("facilityId") long facilityId, @Param("sportId") long sportId);

    /** 시설 종목의 모집 조건을 저장한다. */
    int updateSportConditions(@Param("facilityId") long facilityId, @Param("sportId") long sportId,
            @Param("capacity") int capacity, @Param("note") String note);

    /** 시설 종목별 모집 조건을 종목 순서로 조회한다. */
    List<FacilitySportCondition> selectSportConditions(@Param("facilityId") long facilityId);

    /** 휴관을 등록하고 생성 ID를 파라미터에 채운다. */
    int insertClosure(@Param("closure") FacilityClosure closure);

    /** 적용 중 휴관을 취소 상태로 바꾼다. */
    int cancelClosure(@Param("facilityId") long facilityId,
            @Param("scheduleChangeId") long scheduleChangeId,
            @Param("status") String status);

    /** 시설 수정 시 최신 구역 수를 잠그고 읽는다. */
    Integer selectConcurrentLimitForUpdate(@Param("facilityId") long facilityId);

    /** 시설 관리 화면의 담당 시설 행을 잠그고 이름을 읽는다. 없으면 {@code null}이다. */
    String selectFacilityNameForUpdate(@Param("facilityId") long facilityId);

    /** 시설 이름을 조회한다(읽기 전용 화면용). 없거나 삭제됐으면 {@code null}이다. */
    String selectFacilityName(@Param("facilityId") long facilityId);
}
