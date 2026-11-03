package co.kr.spoit.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/** 표본 저장소가 15초 구간 중복, 개수 상한, 12시간 유효기간을 조회 시점 기준으로 지키는지 검증한다. */
class SystemResourceHistoryTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 2, 0, 0);

    @Test
    void keepsOneSamplePerFifteenSecondBucket() {
        SystemResourceHistory history = new SystemResourceHistory();

        history.record(START.plusSeconds(1), 10, 20);
        history.record(START.plusSeconds(14).plusNanos(999_000_000), 11, 21);
        history.record(START.plusSeconds(15), 12, 22);

        assertThat(history.size(START.plusSeconds(15))).isEqualTo(2);
        assertThat(history.samples(START.plusSeconds(15)))
                .extracting(SystemResourceHistory.Sample::cpuPercent)
                .containsExactly(10, 12);
    }

    @Test
    void capsSamplesAtTwelveHoursOfFifteenSecondIntervals() {
        SystemResourceHistory history = new SystemResourceHistory();

        for (int i = 0; i < SystemResourceHistory.MAX_SAMPLES + 10; i++) {
            history.record(START.plusSeconds(15L * i), i, i);
        }

        LocalDateTime last = START.plusSeconds(15L * (SystemResourceHistory.MAX_SAMPLES + 9));
        assertThat(history.size(last)).isEqualTo(SystemResourceHistory.MAX_SAMPLES);
        // 가장 오래된 표본이 먼저 밀려난다.
        assertThat(history.samples(last).get(0).cpuPercent()).isEqualTo(10);
    }

    @Test
    void dropsSamplesOlderThanTwelveHoursEvenWhenCountIsUnderTheCap() {
        SystemResourceHistory history = new SystemResourceHistory();
        // 수집이 뜸한 상황: 개수 상한(2,880)에 한참 못 미치지만 오래된 표본이 섞여 있다.
        history.record(START, 1, 2);
        history.record(START.plusHours(6), 3, 4);
        history.record(START.plusHours(12), 5, 6);

        LocalDateTime now = START.plusHours(12);
        assertThat(history.samples(now))
                .extracting(SystemResourceHistory.Sample::cpuPercent)
                .containsExactly(1, 3, 5);
        assertThat(history.size(now)).isEqualTo(3);

        // 12시간이 지나면 가장 오래된 표본만 만료된다.
        LocalDateTime later = START.plusHours(12).plusSeconds(15);
        assertThat(history.samples(later))
                .extracting(SystemResourceHistory.Sample::cpuPercent)
                .containsExactly(3, 5);
    }

    @Test
    void keepsSampleExactlyAtTheRetentionBoundary() {
        SystemResourceHistory history = new SystemResourceHistory();
        history.record(START, 1, 2);

        // 경계(정확히 12시간 전)는 포함한다.
        assertThat(history.size(START.plus(SystemResourceHistory.RETENTION))).isEqualTo(1);
        // 1ms만 지나도 제외한다.
        assertThat(history.size(START.plus(SystemResourceHistory.RETENTION).plusNanos(1_000_000))).isZero();
    }

    @Test
    void expiredSamplesDoNotReappearWhenLaterQueriedWithAnEarlierClock() {
        SystemResourceHistory history = new SystemResourceHistory();
        history.record(START, 1, 2);

        // 만료 조회가 저장소에서 실제로 제거하므로 이후 이른 시각으로 조회해도 되살아나지 않는다.
        assertThat(history.size(START.plusDays(2))).isZero();
        assertThat(history.size(START)).isZero();
    }

    @Test
    void retentionMatchesTwelveHoursOfFifteenSecondIntervals() {
        assertThat(SystemResourceHistory.SAMPLE_INTERVAL).isEqualTo(Duration.ofSeconds(15));
        assertThat(SystemResourceHistory.RETENTION)
                .isEqualTo(SystemResourceHistory.SAMPLE_INTERVAL.multipliedBy(SystemResourceHistory.MAX_SAMPLES));
        assertThat(SystemResourceHistory.RETENTION).isEqualTo(Duration.ofHours(12));
        assertThat(SystemResourceHistory.MAX_SAMPLES).isEqualTo(2880);
    }

    @Test
    void concurrentRecordsInTheSameBucketKeepASingleSample() throws Exception {
        SystemResourceHistory history = new SystemResourceHistory();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                Callable<Void> task = () -> {
                    start.await();
                    history.record(START.plusSeconds(3), 1, 2);
                    return null;
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(history.size(START.plusSeconds(15))).isEqualTo(1);
    }

    @Test
    void concurrentRecordsKeepTheCapAndStayConsistent() throws Exception {
        SystemResourceHistory history = new SystemResourceHistory();
        int threads = 8;
        int perThread = 40;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        LocalDateTime last = START.plusSeconds(15L * (threads * perThread - 1));
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int thread = 0; thread < threads; thread++) {
                int offset = thread * perThread;
                Callable<Void> task = () -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        history.record(START.plusSeconds(15L * (offset + i)), 1, 2);
                    }
                    return null;
                };
                futures.add(executor.submit(task));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(history.size(last)).isLessThanOrEqualTo(SystemResourceHistory.MAX_SAMPLES);
        assertThat(history.samples(last)).isSortedAccordingTo(
                (a, b) -> a.bucketStart().compareTo(b.bucketStart()));
        // 서로 다른 스레드가 넣은 표본도 구간당 하나만 남는다.
        assertThat(history.samples(last))
                .extracting(SystemResourceHistory.Sample::bucketStart)
                .doesNotHaveDuplicates();
    }
}
