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
import co.kr.spoit.admin.dto.SystemErrorRecord;

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
        // 표본이 없어도 6시간을 36칸으로 나눈 자리를 모두 유지한다.
        assertThat(dashboard.getBars()).hasSize(36);
        SystemDashboard.Bar latest = dashboard.getBars().get(35);
        assertThat(latest.getCpuHeight()).isNull();
        assertThat(latest.getMemoryHeight()).isNull();
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

        assertThat(first.getBars()).hasSize(36);
        assertThat(second.getBars()).hasSize(36);
        assertThat(second.getBars().get(35).getLabel()).isEqualTo("09:53");
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
        assertThat(service.currentDashboard().getBars()).hasSize(36);
    }

    @Test
    void aggregatesRawSamplesIntoTenMinuteAverages() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 10, 10);
        history.record(now.minusMinutes(10), 10, 20);
        history.record(now.minusMinutes(10).plusSeconds(15), 20, 40);
        history.record(now.minusMinutes(10).plusSeconds(30), null, null);
        history.record(now.minusMinutes(9), 30, 60);

        SystemDashboard dashboard = service(describeProbe(40, 80, 10), now).currentDashboard();

        // 15초 표본 네 개가 모두 마지막 10분 구간([10:00, 10:10))에 들어간다.
        assertThat(dashboard.getBars()).hasSize(36);
        SystemDashboard.Bar last = dashboard.getBars().get(35);
        assertThat(last.getLabel()).isEqualTo("10:00");
        // 조회 시점 표본(40, 80)까지 더해 측정 표본 4개 평균: CPU (10+20+30+40)/4, 힙 (20+40+60+80)/4.
        assertThat(last.getCpuHeight()).isEqualTo(25);
        assertThat(last.getMemoryHeight()).isEqualTo(50);
        // 표본이 없는 앞 구간은 빠지지 않고 값만 비어 있다.
        assertThat(dashboard.getBars().get(0).getLabel()).isEqualTo("04:10");
        assertThat(dashboard.getBars().get(0).getCpuHeight()).isNull();
    }

    @Test
    void averagesOnlyMeasuredValuesInsteadOfTreatingUnavailableAsZero() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 12, 0);
        history.record(now.minusMinutes(60), null, null);
        history.record(now.minusMinutes(60).plusSeconds(15), null, 0);
        history.record(now.minusMinutes(60).plusSeconds(30), 30, 30);

        SystemDashboard dashboard = service(describeProbe(30, 30, 10), now).currentDashboard();

        // 11:00 구간은 6시간을 36칸으로 나눈 30번째 칸이다.
        SystemDashboard.Bar bar = dashboard.getBars().get(30);
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

        assertThat(dashboard.getBars()).hasSize(36);
        SystemDashboard.Bar bar = dashboard.getBars().get(33);
        assertThat(bar.getLabel()).isEqualTo("13:35");
        assertThat(bar.getCpuHeight()).isNull();
        assertThat(bar.getMemoryHeight()).isNull();
    }

    @Test
    void keepsMeasuredZeroDistinctFromUnavailableInAverages() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 16, 5);
        history.record(now.minusMinutes(30), 0, 0);

        SystemDashboard dashboard = service(describeProbe(0, 0, 10), now).currentDashboard();

        assertThat(dashboard.getBars().get(33).getCpuHeight()).isZero();
        assertThat(dashboard.getBars().get(33).getMemoryHeight()).isZero();
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
        assertThat(dashboard.getBars().get(33).getCpuHeight()).isEqualTo(40);
        assertThat(dashboard.getCpuPeak()).isEqualTo(90);
    }

    @Test
    void showsThirtySixTenMinuteBarsCoveringRecentSixHours() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 10, 0);
        for (LocalDateTime time = now.minusHours(12).plusMinutes(5); !time.isAfter(now.minusSeconds(15));
                time = time.plusSeconds(15)) {
            history.record(time, 1, 2);
        }

        SystemDashboard dashboard = service(describeProbe(1, 2, 3), now).currentDashboard();

        // 12시간 원본 표본 중 최근 6시간만 10분 묶음 36칸으로 표시한다.
        assertThat(dashboard.getBars()).hasSize(SystemMonitoringService.MAX_BARS);
        // 칸은 조회 시점부터 거슬러 10분씩, 빠지는 시간 없이 이어진다.
        assertThat(dashboard.getBars().get(0).getLabel()).isEqualTo("04:00");
        assertThat(dashboard.getBars().get(35).getLabel()).isEqualTo("09:50");
        // 36칸을 모두 채우지 못한 구간도 36칸 자체는 유지된다.
        assertThat(dashboard.getBars()).allSatisfy(bar -> assertThat(bar.getLabel()).isNotBlank());
    }

    @Test
    void excludesSamplesBeforeSixHourBoundaryEvenWhenHistoryIsSparse() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 10, 3);
        history.record(now.minusHours(6).minusSeconds(15), 99, 99);
        history.record(now.minusHours(6), 20, 30);
        history.record(now.minusMinutes(1), 40, 50);

        SystemDashboard dashboard = service(describeProbe(10, 10, 10), now).currentDashboard();

        assertThat(dashboard.getBars()).hasSize(36);
        // 6시간 경계(04:03)에 정확히 걸친 표본은 첫 칸에 들어간다.
        assertThat(dashboard.getBars().get(0).getLabel()).isEqualTo("04:03");
        assertThat(dashboard.getBars().get(0).getCpuHeight()).isEqualTo(20);
        // 나머지 칸은 표본이 없어 값이 비어 있다.
        assertThat(dashboard.getBars().get(1).getCpuHeight()).isNull();
        // 피크는 차트 표시 범위가 아닌 보관된 12시간 원본에서 계산한다.
        assertThat(dashboard.getCpuPeak()).isEqualTo(99);
    }

    @Test
    void keepsAnEmptyBucketAsALabelWithoutBars() {
        when(databaseProbe.isReachable()).thenReturn(true);
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 20, 0);

        SystemDashboard dashboard = service(describeProbe(null, null, 10), now).currentDashboard();

        // 측정값이 전혀 없어도 36칸의 라벨은 모두 채워지고 막대 값만 null이다.
        assertThat(dashboard.getBars()).hasSize(36);
        assertThat(dashboard.getBars())
                .allSatisfy(bar -> {
                    assertThat(bar.getCpuHeight()).isNull();
                    assertThat(bar.getMemoryHeight()).isNull();
                    assertThat(bar.getLabel()).isNotBlank();
                });
        assertThat(dashboard.getBars().get(0).getLabel()).isEqualTo("14:00");
        assertThat(dashboard.getBars().get(35).getLabel()).isEqualTo("19:50");
    }

    @Test
    void aggregatesOnlyRecentErrorRecordsAndCapsDisplayedGroupsAtTenNewestFirst() {
        when(databaseProbe.isReachable()).thenReturn(true);
        // 버퍼는 나중에 들어온 기록을 최신으로 돌려주므로 오래된 것부터 넣는다.
        // 정확히 1시간 전(경계)은 포함되고, 그보다 1초 이전·2시간 전·WARN은 제외된다.
        errorLogBuffer.recordForTest(error(NOW.minusHours(2), "ERROR", "club", "두 시간 전 오류"));
        errorLogBuffer.recordForTest(error(NOW.minusHours(1).minusSeconds(1), "ERROR", "club", "경계 밖 오류"));
        errorLogBuffer.recordForTest(error(NOW.minusSeconds(30), "WARN", "facility", "경고는 제외"));
        errorLogBuffer.recordForTest(error(NOW.minusHours(1), "ERROR", "club", "경계 오류"));
        // 화면 표는 10행까지만 보여 주므로, 서로 다른 12개 그룹을 더해 상한을 넘긴다.
        for (int index = 12; index >= 1; index--) {
            errorLogBuffer.recordForTest(
                    error(NOW.minusMinutes(3L + index), "ERROR", "service" + index, "오류 " + index));
        }
        // 같은 서비스·코드·요약 3건은 한 행으로 묶이고 횟수는 3이 된다.
        errorLogBuffer.recordForTest(error(NOW.minusMinutes(3), "ERROR", "match", "중복 오류"));
        errorLogBuffer.recordForTest(error(NOW.minusMinutes(2), "ERROR", "match", "중복 오류"));
        errorLogBuffer.recordForTest(error(NOW.minusMinutes(1), "ERROR", "match", "중복 오류"));

        SystemDashboard dashboard = service(describeProbe(10, 20, 30)).currentDashboard();

        // 표에 보이지 않는 그룹까지 포함한 실제 오류 기록 수(3 + 12 + 경계 1)다.
        assertThat(dashboard.getRecentErrorCount()).isEqualTo(16);
        assertThat(dashboard.getRecentErrorAttention()).isEqualTo(16);

        assertThat(dashboard.getRecentErrors()).hasSize(SystemMonitoringService.RECENT_ERROR_ROWS);
        assertThat(dashboard.getRecentErrors())
                .extracting(row -> ((SystemDashboard.RecentError) row).getSummary())
                .containsExactly("중복 오류", "오류 1", "오류 2", "오류 3", "오류 4", "오류 5", "오류 6",
                        "오류 7", "오류 8", "오류 9");
        // 최신 기록이 속한 그룹부터 놓고, 묶인 횟수를 그대로 보여 준다.
        SystemDashboard.RecentError aggregated = (SystemDashboard.RecentError) dashboard.getRecentErrors().get(0);
        assertThat(aggregated.getCount()).isEqualTo(3);
        assertThat(aggregated.getService()).isEqualTo("match");
        assertThat(aggregated.getCode()).isEqualTo("IllegalStateException");
        assertThat(aggregated.getSeverityLabel()).isEqualTo("오류");
        assertThat(aggregated.getSeverityTone()).isEqualTo("bad");
        assertThat(aggregated.getTime()).isEqualTo("2026-11-02 10:02:00");
        // WARN과 1시간을 넘긴 기록은 목록에도 횟수에도 들어가지 않는다.
        assertThat(dashboard.getRecentErrors())
                .extracting(row -> ((SystemDashboard.RecentError) row).getSummary())
                .doesNotContain("경고는 제외", "경계 밖 오류", "두 시간 전 오류");
        // 경계 기록은 집계에는 들어가지만 10행 상한 밖이라 표시되지는 않는다.
        assertThat(dashboard.getRecentErrors())
                .extracting(row -> ((SystemDashboard.RecentError) row).getSummary())
                .doesNotContain("경계 오류", "오류 10", "오류 11", "오류 12");
    }

    private static SystemErrorRecord error(LocalDateTime time, String level, String service, String summary) {
        return new SystemErrorRecord(time, level, "co.kr.spoit." + service + ".Test", service,
                "IllegalStateException", summary, summary);
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
