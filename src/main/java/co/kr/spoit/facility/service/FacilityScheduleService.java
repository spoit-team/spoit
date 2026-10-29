package co.kr.spoit.facility.service;

import java.time.LocalDate;
import java.time.YearMonth;

import co.kr.spoit.facility.dto.ClosureForm;
import co.kr.spoit.facility.dto.ConditionsForm;
import co.kr.spoit.facility.dto.DateScheduleForm;
import co.kr.spoit.facility.dto.SchedulePage;
import co.kr.spoit.facility.dto.WeeklyScheduleForm;

/**
 * 예약 운영 일정 화면의 조회와 저장을 담당한다.
 *
 * <p>모든 변경은 인증 주체가 담당자로 매핑된 시설에만 허용하며, 소유권·시간 형식·충돌 검증과
 * 저장 트랜잭션을 이 서비스가 책임진다. 컨트롤러는 요청 값 전달과 화면 복귀만 한다.</p>
 */
public interface FacilityScheduleService {

    /**
     * 예약 운영 일정 화면 모델을 만든다.
     *
     * @param userId      인증 주체의 회원 ID
     * @param facilityId  선택 시설. 없거나 담당 시설이 아니면 화면은 빈 상태가 된다.
     * @param date        선택 날짜. 없으면 달력만 보여 준다.
     * @param sportId     모집 조건 탭에서 선택할 종목
     * @param tab         복귀할 탭 ID
     * @param month       달력에 표시할 월. 없으면 선택 날짜 또는 오늘 기준이다.
     */
    SchedulePage findSchedulePage(Long userId, Long facilityId, LocalDate date, Long sportId,
            String tab, YearMonth month);

    /** 요일별 운영 시간을 저장한다. 7일 전체 휴무도 허용한다. */
    void saveWeeklyHours(Long userId, WeeklyScheduleForm form);

    /** 날짜별 운영 시간을 저장한다. 0슬롯은 그 날짜 전체 휴무다. */
    void saveDateHours(Long userId, DateScheduleForm form);

    /** 날짜별 예외를 지워 주간 기준으로 되돌린다. */
    void resetDateHours(Long userId, Long facilityId, LocalDate date);

    /** 종목별 모집 조건을 저장한다. 신규 예약에만 적용하고 기존 방에 소급하지 않는다. */
    void saveConditions(Long userId, ConditionsForm form);

    /** 휴관을 등록한다. 활성 매칭방과 겹치면 저장하지 않는다. */
    void registerClosure(Long userId, ClosureForm form);

    /** 담당 시설의 휴관을 취소한다. */
    void cancelClosure(Long userId, long facilityId, long scheduleChangeId);
}
