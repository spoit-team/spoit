package co.kr.spoit.facility.service;

import java.time.LocalDateTime;

/**
 * 시설별 매칭방 생성 시 대관 가능 구역 수(동시 예약 한도)를 검사하는 별도 API다.
 *
 * <p>매칭방 생성 핸들러는 이번 범위 밖이므로 이 인터페이스는 생성 경로가 아니라 생성
 * 담당이 연결할 검증 지점이다. 구현은 호출자의 쓰기 트랜잭션에 참여하고, 시설 잠금을 이미
 * 가진 상태에서 최신 점유를 다시 읽는다.</p>
 */
public interface FacilityCapacityService {

    /**
     * 요청 구간에 새 매칭방 하나를 더했을 때 시설 전체 동시 점유가 한도를 넘지 않는지 확인한다.
     *
     * <p>점유 대상은 모집 중·매칭 완료 매칭방이며 취소·종료는 제외한다. 한도는 종목과 무관하게
     * 시설 전체에 적용하고, 각 시간 구간의 동시 점유 최대값으로 판단한다(단순 합계 아님).
     * 종료 이벤트를 먼저 처리해 경계가 맞닿은 구간을 과대 계산하지 않는다.</p>
     *
     * @throws FacilityScheduleUnavailableException 한도를 넘거나 시설이 없을 때
     */
    void requireCapacity(long facilityId, LocalDateTime startAt, LocalDateTime endAt);
}
