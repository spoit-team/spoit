package co.kr.spoit.facility.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.facility.domain.FacilitySchedule;
import co.kr.spoit.facility.mapper.FacilityScheduleMapper;

/**
 * 매칭 도메인에 공개하는 시설 일정 검사 구현이다.
 *
 * <p>기존 트랜잭션이 있으면 참여한다({@link Propagation#REQUIRED}). 단독 호출 시에는 새
 * 트랜잭션이 메서드 종료와 함께 끝나므로, 시설 잠금과 후속 검증·저장은 반드시 같은 외부 쓰기
 * 트랜잭션에서 수행해야 한다. 모든 읽기는 {@code FOR UPDATE} current read로 수행해
 * RR(REPEATABLE READ) 스냅샷의 이전 값을 쓰지 않는다.</p>
 */
@Service
public class FacilityReservationServiceImpl implements FacilityReservationService {

    private final FacilityScheduleMapper scheduleMapper;
    private final FacilityScheduleLoader scheduleLoader;

    public FacilityReservationServiceImpl(FacilityScheduleMapper scheduleMapper,
            FacilityScheduleLoader scheduleLoader) {
        this.scheduleMapper = scheduleMapper;
        this.scheduleLoader = scheduleLoader;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void lockActiveFacility(long facilityId) {
        requireActiveFacilityRow(facilityId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void requireAvailable(long facilityId, LocalDateTime startAt, LocalDateTime endAt) {
        requireActiveFacilityRow(facilityId);
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new FacilityScheduleUnavailableException("이용 시작과 종료 시각을 확인해 주세요.");
        }

        // 기간 상한은 두지 않는다. 저장 후보 검사(activeConflicts)와 완전히 같은 covers 판정을
        // 써야 "저장은 되는데 신규 신청만 거부"되는 불일치가 생기지 않는다.
        FacilitySchedule schedule = scheduleLoader.snapshotForUpdate(facilityId).toSchedule();
        if (!schedule.isConfigured()) {
            throw new FacilityScheduleUnavailableException(
                    "시설 운영 시간이 아직 설정되지 않아 예약할 수 없습니다. 시설 관리자에게 운영 시간 등록을 요청해 주세요.");
        }
        if (!schedule.covers(startAt, endAt)) {
            throw new FacilityScheduleUnavailableException(
                    "선택한 시간은 시설 운영 시간이 아니거나 휴관과 겹쳐 예약할 수 없습니다.");
        }
    }

    /** 활성 시설 행을 잠근다. 없거나 논리 삭제됐으면 사용자 안내 예외를 던진다. */
    private void requireActiveFacilityRow(long facilityId) {
        if (scheduleMapper.selectFacilityNameForUpdate(facilityId) == null) {
            throw new FacilityScheduleUnavailableException("예약할 시설을 찾을 수 없습니다.");
        }
    }
}
