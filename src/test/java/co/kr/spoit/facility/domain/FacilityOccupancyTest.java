package co.kr.spoit.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 시간대별 동시 점유 최대값 계산을 검증한다. */
class FacilityOccupancyTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    @Test
    @DisplayName("인접한 두 방은 동시 점유 1로 계산한다")
    void adjacentPeriodsDoNotStack() {
        List<MatchPeriod> periods = List.of(
                period(1L, 10, 11),
                period(2L, 11, 12));

        assertThat(FacilityOccupancy.peak(periods)).isEqualTo(1);
    }

    @Test
    @DisplayName("겹치는 방만 동시 점유로 세고 최대값을 돌려준다")
    void overlappingPeriodsStack() {
        List<MatchPeriod> periods = List.of(
                period(1L, 10, 12),
                period(2L, 11, 13),
                period(3L, 20, 21));

        assertThat(FacilityOccupancy.peak(periods)).isEqualTo(2);
    }

    @Test
    @DisplayName("대상 구간과 겹치는 점유만 골라 최대값을 계산한다")
    void peakWithinTargetRange() {
        List<MatchPeriod> periods = List.of(
                period(1L, 10, 11),
                period(2L, 11, 12),
                period(3L, 20, 21));

        // 10~12시와 겹치는 기존 두 방은 서로 인접해 동시 점유가 1이다.
        // 새 방 1개를 더한 값(2)은 호출부가 계산하므로 여기서는 기존 점유만 확인한다.
        assertThat(FacilityOccupancy.peakWithin(periods, DAY.atTime(10, 0), DAY.atTime(12, 0)))
                .isEqualTo(1);
        // 12~20시 구간은 기존 방과 겹치지 않는다.
        assertThat(FacilityOccupancy.peakWithin(periods, DAY.atTime(12, 0), DAY.atTime(20, 0)))
                .isZero();
    }

    @Test
    @DisplayName("빈 목록과 잘못된 구간은 0으로 계산한다")
    void emptyAndInvalidPeriods() {
        assertThat(FacilityOccupancy.peak(List.of())).isZero();
        assertThat(FacilityOccupancy.peak(List.of(period(1L, 12, 10)))).isZero();
        assertThat(FacilityOccupancy.peakWithin(List.of(), DAY.atTime(10, 0), DAY.atTime(11, 0)))
                .isZero();
    }

    private static MatchPeriod period(Long matchId, int startHour, int endHour) {
        MatchPeriod period = new MatchPeriod();
        period.setMatchId(matchId);
        period.setTitle("방 " + matchId);
        period.setStartAt(LocalDateTime.of(DAY, java.time.LocalTime.of(startHour, 0)));
        period.setEndAt(LocalDateTime.of(DAY, java.time.LocalTime.of(endHour, 0)));
        return period;
    }
}
