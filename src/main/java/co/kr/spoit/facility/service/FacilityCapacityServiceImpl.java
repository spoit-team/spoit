package co.kr.spoit.facility.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.facility.domain.FacilityOccupancy;
import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.mapper.FacilityScheduleMapper;
import co.kr.spoit.match.domain.MatchStatus;

/**
 * 대관 가능 구역 수(동시 예약 한도) 검증 구현이다.
 *
 * <p>호출자의 쓰기 트랜잭션에 참여하고, 항상 시설 잠금 → 매칭방 점유 순서로 최신 값을 다시
 * 읽는다. 점유 대상은 모집 중·매칭 완료뿐이며 취소·종료는 제외한다.</p>
 */
@Service
public class FacilityCapacityServiceImpl implements FacilityCapacityService {

    /** 동시 점유로 인정하는 매칭방 상태다. 취소·종료는 제외한다. */
    static final List<String> OCCUPYING_STATUSES = List.of(
            MatchStatus.RECRUITING.dbValue(), MatchStatus.MATCHED.dbValue());

    private final FacilityScheduleMapper scheduleMapper;

    public FacilityCapacityServiceImpl(FacilityScheduleMapper scheduleMapper) {
        this.scheduleMapper = scheduleMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void requireCapacity(long facilityId, LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new FacilityScheduleUnavailableException("이용 시작과 종료 시각을 확인해 주세요.");
        }
        Integer limit = scheduleMapper.selectConcurrentLimitForUpdate(facilityId);
        if (limit == null) {
            throw new FacilityScheduleUnavailableException("예약할 시설을 찾을 수 없습니다.");
        }
        if (limit < 1) {
            throw new FacilityScheduleUnavailableException("시설의 대관 가능 구역 수가 설정되지 않았습니다.");
        }

        List<MatchPeriod> existing = scheduleMapper.selectMatchPeriodsForUpdate(facilityId, OCCUPYING_STATUSES);
        int peak = FacilityOccupancy.peakWithin(nullSafe(existing), startAt, endAt);
        if (peak + 1 > limit) {
            throw new FacilityScheduleUnavailableException(
                    "선택한 시간대의 동시 대관 한도(" + limit + "구역)를 넘어 매칭방을 만들 수 없습니다.");
        }
    }

    private static List<MatchPeriod> nullSafe(List<MatchPeriod> values) {
        return values == null ? new ArrayList<>() : values;
    }
}
