package co.kr.spoit.facility.service;

import java.time.LocalDateTime;

/**
 * 매칭 도메인이 시설 일정을 검사할 때 쓰는 공개 계약이다.
 *
 * <p>이 인터페이스는 match 담당과 합의한 컴파일 연결점이다. 구현은 호출자의 기존 쓰기
 * 트랜잭션에 참여하며({@code REQUIRED}), 시설 행을 잠근 뒤 최신 current/locking read로
 * 주간 규칙·날짜별 예외·적용 중 휴관을 확인한다. 검사에 실패하면
 * {@link FacilityScheduleUnavailableException}을 던진다.</p>
 *
 * <p>매칭 서비스는 시설 매퍼를 직접 공유하지 않고 이 API만 호출한다.</p>
 */
public interface FacilityReservationService {

    /**
     * 활성 시설 행을 {@code FOR UPDATE}로 잠근다.
     *
     * <p>호출자는 이 잠금을 유지한 채 같은 트랜잭션에서 {@link #requireAvailable}을 호출해
     * 최신 일정을 다시 읽어야 한다. 일반 조회로 만들어진 이전 스냅샷을 신뢰하지 않기 위한
     * 장치다. 시설이 없거나 삭제됐으면 예외를 던진다.</p>
     */
    void lockActiveFacility(long facilityId);

    /**
     * 시설 잠금을 이미 가진 트랜잭션에서 요청 구간 전체가 이용 가능한지 확인한다.
     *
     * <p>{@code [startAt, endAt)} 구간을 1시간 단위로 검사하며, 자정을 넘는 구간도 날짜별로
     * 나누어 확인한다. 여러 날에 걸친 구간도 임의의 기간 상한 없이 같은 판정을 적용하므로,
     * 일정 저장 후보 검사와 결과가 일치한다. 다음 경우 모두
     * {@link FacilityScheduleUnavailableException}을 던진다.</p>
     * <ul>
     *   <li>운영 시간이 한 번도 설정되지 않은 시설</li>
     *   <li>시설이 없거나 삭제된 경우</li>
     *   <li>휴관·요일 휴무·날짜별 0슬롯 휴무로 막힌 경우</li>
     *   <li>운영 시간 밖인 경우</li>
     * </ul>
     */
    void requireAvailable(long facilityId, LocalDateTime startAt, LocalDateTime endAt);
}
