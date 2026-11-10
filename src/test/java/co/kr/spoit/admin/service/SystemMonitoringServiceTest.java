package co.kr.spoit.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.kr.spoit.admin.dto.SystemDashboard;

/** 모니터링 dashboard가 실제 측정 범위를 지키고 DB 장애를 상태로만 표현하는지 검증한다. */
@ExtendWith(MockitoExtension.class)
class SystemMonitoringServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 10, 3);

    @Mock
    private DatabaseAvailabilityProbe databaseProbe;

    private final SystemResourceHistory history = new SystemResourceHistory();
    private final SystemErrorLogBuffer errorLogBuffer = new SystemErrorLogBuffer();

    @Test
    void reportsMeasuredValuesAndHealthyProbes() {
        when(databaseProbe.isReachable()).thenReturn(true);

        SystemDashboard dashboard = service(describeProbe(11, 42, 57)).currentDashboard();

        assertThat(dashboard.getApiStatus()).isEqualTo("정상");
        assertThat(dashboard.getApiUptime()).isNotBlank();
        assertThat(dashboard.getCpuPercent()).isEqualTo(11);
        assertThat(dashboard.getMemoryPercent()).isEqualTo(42);
        assertThat(dashboard.getMemoryCapacity()).isEqualTo("512MB");
        assertThat(dashboard.getDiskPercent()).isEqualTo(57);
        assertThat(dashboard.isAllHealthy()).isTrue();
        assertThat(dashboard.getServices())
                .extracting(SystemDashboard.ServiceStatus::getName)
                .containsExactly("API 서버", "데이터베이스");
        // 오류 버퍼가 비어 있으면 0건이고 목록도 비어 있다(0으로 위장한 값이 아니라 실제 수집 결과).
        assertThat(dashboard.getRecentErrorCount()).isZero();
        assertThat(dashboard.getRecentErrorAttention()).isZero();
        assertThat(dashboard.getRecentErrors()).isEmpty();
        assertThat(dashboard.getRecentReports()).isEmpty();
    }

    @Test
    void keepsMetricsUnavailableInTheJdkAsNullInsteadOfZero() {
        when(databaseProbe.isReachable()).thenReturn(true);

        SystemDashboard dashboard = service(describeProbe(null, null, null)).currentDashboard();

        assertThat(dashboard.getCpuPercent()).isNull();
        assertThat(dashboard.getCpuPeak()).isNull();
        assertThat(dashboard.getDiskPercent()).isNull();
        // 힙 사용률을 알 수 없으면 0%로 위장하지 않는다.
        assertThat(dashboard.getMemoryPercent()).isNull();
        assertThat(dashboard.getBars()).hasSize(1);
        assertThat(dashboard.getBars().get(0).getCpuHeight()).isNull();
        assertThat(dashboard.getBars().get(0).getMemoryHeight()).isNull();
    }

    @Test
    void reportsMemoryUnavailableWithoutFakingZeroPercent() {
        when(databaseProbe.isReachable()).thenReturn(true);

        SystemDashboard unavailable = service(describeProbe(10, null, 20)).currentDashboard();
        SystemDashboard available = service(describeProbe(10, 0, 20)).currentDashboard();

        // 미지원(null)과 실제 0%는 화면에서 구분되어야 한다.
        assertThat(unavailable.getMemoryPercent()).isNull();
        assertThat(available.getMemoryPercent()).isZero();
    }

    @Test
    void reportsUnhealthyWithoutFailingWhenDatabaseIsUnreachable() {
        when(databaseProbe.isReachable()).thenReturn(false);

        SystemDashboard dashboard = service(describeProbe(5, 10, 20)).currentDashboard();

        assertThat(dashboard.isAllHealthy()).isFalse();
        assertThat(dashboard.getServices())
                .filteredOn(service -> "데이터베이스".equals(service.getName()))
                .singleElement()
                .satisfies(service -> {
                    assertThat(service.getDetail()).isEqualTo("연결 확인 실패");
                    assertThat(service.getTone()).isEqualTo("bad");
                });
        // API 자체는 계속 정상으로 보고한다.
        assertThat(dashboard.getApiStatus()).isEqualTo("정상");
    }

    @Test
    void firstRequestSecuresASampleAndRepeatedRequestsInTheSameBucketDoNotDuplicate() {
        when(databaseProbe.isReachable()).thenReturn(true);
        SystemMonitoringService service = service(describeProbe(30, 40, 50));

        SystemDashboard first = service.currentDashboard();
        SystemDashboard second = service.currentDashboard();

        assertThat(first.getBars()).hasSize(1);
        assertThat(second.getBars()).hasSize(1);
        assertThat(second.getBars().get(0).getLabel()).isEqualTo("10:00");
        assertThat(history.size(NOW)).isEqualTo(1);
    }

    @Test
    void scheduledCollectionAndRequestInTheSameFifteenSecondWindowShareOneSample() {
        when(databaseProbe.isReachable()).thenReturn(true);
        SystemMonitoringService service = service(describeProbe(30, 40, 50));

        service.collectSample();
        service.currentDashboard();
        service.collectSample();

        assertThat(history.size(NOW)).isEqualTo(1);
        assertThat(service.currentDashboard().getBars()).hasSize(1);
    }

    @Test
    void aggregatesRawSamplesIntoFiveMinuteAverages() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 10, 10);
        history.record(now.minusMinutes(10), 10, 20);
        history.record(now.minusMinutes(10).plusSeconds(15), 20, 40);
        history.record(now.minusMinutes(10).plusSeconds(30), null, null);
        history.record(now.minusMinutes(9), 30, 60);

        SystemDashboard dashboard = service(describeProbe(40, 80, 10), now).currentDashboard();

        assertThat(dashboard.getBars()).hasSize(2);
        assertThat(dashboard.getBars().get(0).getLabel()).isEqualTo("10:00");
        // 10:00 구간의 측정 표본 3개 평균: CPU (10+20+30)/3, 힙 (20+40+60)/3. null 표본은 제외한다.
        assertThat(dashboard.getBars().get(0).getCpuHeight()).isEqualTo(20);
        assertThat(dashboard.getBars().get(0).getMemoryHeight()).isEqualTo(40);
        assertThat(dashboard.getBars().get(1).getLabel()).isEqualTo("10:10");
        assertThat(dashboard.getBars().get(1).getCpuHeight()).isEqualTo(40);
        assertThat(dashboard.getBars().get(1).getMemoryHeight()).isEqualTo(80);
    }

    @Test
    void averagesOnlyMeasuredValuesInsteadOfTreatingUnavailableAsZero() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
        history.record(now.minusMinutes(60), null, null);
        history.record(now.minusMinutes(60).plusSeconds(15), null, 0);
        history.record(now.minusMinutes(60).plusSeconds(30), 30, 30);

        SystemDashboard dashboard = service(describeProbe(30, 30, 10), now).currentDashboard();

        SystemDashboard.Bar bar = dashboard.getBars().get(0);
        assertThat(bar.getLabel()).isEqualTo("11:00");
        // null을 0으로 채웠다면 CPU는 10이 된다. 측정값만 평균하므로 30이다.
        assertThat(bar.getCpuHeight()).isEqualTo(30);
        // 실제 0%는 평균에 포함된다: (0 + 30) / 2.
        assertThat(bar.getMemoryHeight()).isEqualTo(15);
    }

    @Test
    void bucketWithOnlyUnavailableMetricsStaysNull() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 14, 5);
        history.record(now.minusMinutes(30), null, null);
        history.record(now.minusMinutes(30).plusSeconds(15), null, null);

        SystemDashboard dashboard = service(describeProbe(5, 7, 10), now).currentDashboard();

        assertThat(dashboard.getBars()).hasSize(2);
        assertThat(dashboard.getBars().get(0).getCpuHeight()).isNull();
        assertThat(dashboard.getBars().get(0).getMemoryHeight()).isNull();
    }

    @Test
    void keepsMeasuredZeroDistinctFromUnavailableInAverages() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 16, 5);
        history.record(now.minusMinutes(30), 0, 0);

        SystemDashboard dashboard = service(describeProbe(0, 0, 10), now).currentDashboard();

        assertThat(dashboard.getBars().get(0).getCpuHeight()).isZero();
        assertThat(dashboard.getBars().get(0).getMemoryHeight()).isZero();
    }

    @Test
    void cpuPeakComesFromRawSamplesNotFromAveragedBars() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 18, 5);
        history.record(now.minusMinutes(30), 10, 10);
        history.record(now.minusMinutes(30).plusSeconds(15), 90, 10);
        history.record(now.minusMinutes(30).plusSeconds(30), 20, 10);

        SystemDashboard dashboard = service(describeProbe(10, 10, 10), now).currentDashboard();

        // 막대는 평균이지만 피크는 원본 15초 표본의 최대치다.
        assertThat(dashboard.getBars().get(0).getCpuHeight()).isEqualTo(40);
        assertThat(dashboard.getCpuPeak()).isEqualTo(90);
    }

    @Test
    void keepsAtMostOneBarPerFiveMinutesForAFullTwelveHourWindow() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 10, 0);
        for (LocalDateTime time = now.minusHours(12).plusMinutes(5); !time.isAfter(now.minusSeconds(15));
                time = time.plusSeconds(15)) {
            history.record(time, 1, 2);
        }

        SystemDashboard dashboard = service(describeProbe(1, 2, 3), now).currentDashboard();

        // 2,861개 표본(요청 시점 포함)이 5분 구간으로 압축되어 144개 막대가 된다.
        assertThat(dashboard.getBars()).hasSize(144);
        assertThat(dashboard.getBars()).hasSizeLessThanOrEqualTo(SystemMonitoringService.MAX_BARS);
        // 12시간 전 구간부터 표시하고, 요청 시점 구간이 마지막이다.
        assertThat(dashboard.getBars().get(0).getLabel()).isEqualTo("22:05");
        assertThat(dashboard.getBars().get(143).getLabel()).isEqualTo("10:00");
    }

    private SystemMonitoringService service(SystemResourceProbe probe) {
        return service(probe, NOW);
    }

    private SystemMonitoringService service(SystemResourceProbe probe, LocalDateTime now) {
        Clock clock = Clock.fixed(now.atZone(ZONE).toInstant(), ZONE);
        return new SystemMonitoringService(probe, history, databaseProbe, errorLogBuffer, clock);
    }

    private static SystemResourceProbe describeProbe(Integer cpu, Integer heap, Integer disk) {
        return new SystemResourceProbe() {
            @Override
            public Integer cpuPercent() {
                return cpu;
            }

            @Override
            public Integer heapPercent() {
                return heap;
            }

            @Override
            public String heapCapacityLabel() {
                return "512MB";
            }

            @Override
            public Integer diskPercent() {
                return disk;
            }

            @Override
            public long uptimeMillis() {
                return 3_600_000L;
            }
        };
    }
}
