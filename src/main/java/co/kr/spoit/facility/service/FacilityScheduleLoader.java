package co.kr.spoit.facility.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import co.kr.spoit.facility.domain.DateOverride;
import co.kr.spoit.facility.domain.FacilityClosure;
import co.kr.spoit.facility.domain.OperatingRule;
import co.kr.spoit.facility.mapper.FacilityScheduleMapper;

/**
 * 시설 일정 계산에 필요한 값을 모아 재료로 만든다.
 *
 * <p>변경 경로는 반드시 {@code ...ForUpdate} 메서드를 써서 시설 잠금을 가진 트랜잭션 안에서
 * 최신 current read를 얻는다. 화면 조회 경로는 잠금 없는 읽기를 쓴다(읽기 전용 트랜잭션에서
 * {@code FOR UPDATE}는 MySQL이 거부한다).</p>
 */
@Component
class FacilityScheduleLoader {

    private final FacilityScheduleMapper scheduleMapper;

    FacilityScheduleLoader(FacilityScheduleMapper scheduleMapper) {
        this.scheduleMapper = scheduleMapper;
    }

    /** 변경 검증용: 주간·날짜별·적용 중 휴관을 잠금 current read로 읽는다. */
    ScheduleSnapshot snapshotForUpdate(long facilityId) {
        List<OperatingRule> rules = nullSafe(scheduleMapper.selectWeeklyRulesForUpdate(facilityId));
        List<DateOverride> overrides = withHours(
                nullSafe(scheduleMapper.selectDateOverridesForUpdate(facilityId)), true);
        List<FacilityClosure> closures = nullSafe(scheduleMapper.selectActiveClosuresForUpdate(facilityId));
        return new ScheduleSnapshot(rules, overrides, closures);
    }

    /** 화면 조회용: 잠금 없는 읽기다. */
    ScheduleSnapshot snapshot(long facilityId) {
        List<OperatingRule> rules = nullSafe(scheduleMapper.selectWeeklyRules(facilityId));
        List<DateOverride> overrides = withHours(nullSafe(scheduleMapper.selectDateOverrides(facilityId)), false);
        List<FacilityClosure> closures = nullSafe(scheduleMapper.selectActiveClosures(facilityId));
        return new ScheduleSnapshot(rules, overrides, closures);
    }

    private List<DateOverride> withHours(List<DateOverride> headers, boolean forUpdate) {
        List<DateOverride> overrides = new ArrayList<>();
        for (DateOverride header : headers) {
            if (header == null || header.getOverrideId() == null) {
                continue;
            }
            List<Integer> hours = forUpdate
                    ? nullSafe(scheduleMapper.selectDateOverrideHoursForUpdate(header.getOverrideId()))
                    : nullSafe(scheduleMapper.selectDateOverrideHours(header.getOverrideId()));
            header.setHours(new ArrayList<>(hours));
            overrides.add(header);
        }
        return overrides;
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    /** 일정 재료다. */
    record ScheduleSnapshot(List<OperatingRule> rules, List<DateOverride> overrides,
            List<FacilityClosure> closures) {

        /** 계산 모델로 바꾼다. */
        co.kr.spoit.facility.domain.FacilitySchedule toSchedule() {
            return co.kr.spoit.facility.domain.FacilitySchedule.of(rules, overrides, closures);
        }

        DateOverride findOverride(LocalDate date) {
            for (DateOverride override : overrides) {
                if (date.equals(override.getDate())) {
                    return override;
                }
            }
            return null;
        }
    }
}
