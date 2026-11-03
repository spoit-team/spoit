package co.kr.spoit.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

/** DB 장애가 예외로 새어 나오지 않고, 폭주·지연·복구에서도 대기열이 무한히 자라지 않는지 검증한다. */
class DatabaseAvailabilityProbeTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(120);
    private static final Duration NO_CACHE = Duration.ZERO;

    @Test
    void reportsUnreachableWhenConnectionFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("연결 실패"));

        assertThat(probe(dataSource).isReachable()).isFalse();
    }

    @Test
    void reportsUnreachableWhenConnectionIsInvalid() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(false);

        assertThat(probe(dataSource).isReachable()).isFalse();
    }

    @Test
    void closesTheConnectionEvenWhenProbeTimesOutOrFails() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection invalid = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(invalid);
        when(invalid.isValid(anyInt())).thenReturn(false);

        assertThat(probe(dataSource).isReachable()).isFalse();

        verify(invalid).close();
    }

    @Test
    void concurrentCacheMissesRunOnlyOneProbeAtATime() throws Exception {
        BlockingDataSource dataSource = new BlockingDataSource();
        DatabaseAvailabilityProbe probe = probe(dataSource);

        int callers = 12;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                Callable<Boolean> task = () -> {
                    ready.countDown();
                    go.await();
                    return probe.isReachable();
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            // 동시 cache miss여도 실제 probe는 한 번만 실행된다(single-flight).
            assertThat(dataSource.attempts()).isEqualTo(1);
            assertThat(results).doesNotContainNull();
        } finally {
            dataSource.releaseAll();
            executor.shutdownNow();
        }
    }

    @Test
    void slowDatabaseTimesOutWithoutGrowingTheQueueAndKeepsRetrying() throws Exception {
        BlockingDataSource dataSource = new BlockingDataSource();
        DatabaseAvailabilityProbe probe = probe(dataSource);

        // 첫 호출은 DB가 응답하지 않아 타임아웃된다.
        assertThat(probe.isReachable()).isFalse();
        assertThat(probe.isReachable()).isFalse();
        assertThat(probe.isReachable()).isFalse();

        // 작업자는 1개뿐이고 대기열이 없으므로 아직 끝나지 않은 probe 동안에는 새 작업이 쌓이지 않는다.
        // (시도 횟수는 1~2회를 넘지 않는다: 진행 중 probe 공유 + SynchronousQueue 거절)
        assertThat(dataSource.attempts()).isLessThanOrEqualTo(2);
        assertThat(dataSource.attempts()).isGreaterThanOrEqualTo(1);

        // DB가 복구되면 다음 호출이 새 probe를 실행해 정상으로 돌아온다(작업자 반환까지 재시도).
        dataSource.releaseAll();
        assertThat(awaitReachable(probe)).isTrue();
        assertThat(dataSource.attempts()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void recoversAfterOutageOnceDatabaseRespondsAgain() throws Exception {
        BlockingDataSource dataSource = new BlockingDataSource();
        DatabaseAvailabilityProbe probe = probe(dataSource);

        assertThat(probe.isReachable()).isFalse();
        dataSource.failNext();
        assertThat(probe.isReachable()).isFalse();

        dataSource.releaseAll();
        assertThat(awaitReachable(probe)).isTrue();
    }

    @Test
    void reusesCachedResultWithinTtl() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);

        DatabaseAvailabilityProbe probe = new DatabaseAvailabilityProbe(dataSource, fixedClock(),
                SHORT_TIMEOUT, Duration.ofSeconds(10));
        assertThat(probe.isReachable()).isTrue();
        assertThat(probe.isReachable()).isTrue();

        // 캐시가 살아 있으면 워커를 다시 호출하지 않는다.
        verify(dataSource, times(1)).getConnection();
    }

    /** 작업자가 이전 probe에서 풀려날 때까지 짧게 재시도한다. */
    private static boolean awaitReachable(DatabaseAvailabilityProbe probe) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (probe.isReachable()) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static DatabaseAvailabilityProbe probe(DataSource dataSource) {
        return new DatabaseAvailabilityProbe(dataSource, advancingClock(), SHORT_TIMEOUT, NO_CACHE);
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-11-02T01:00:00Z"), ZoneId.of("Asia/Seoul"));
    }

    private static Clock advancingClock() {
        return new Clock() {
            private final AtomicLong millis = new AtomicLong(Instant.parse("2026-11-02T01:00:00Z").toEpochMilli());

            @Override
            public ZoneId getZone() {
                return ZoneId.of("Asia/Seoul");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(millis());
            }

            @Override
            public long millis() {
                // 캐시 TTL을 0으로 두어 매 호출이 실제 probe 경로를 타도록 시각을 진행시킨다.
                return millis.addAndGet(1_000L);
            }
        };
    }

    /** JDBC 연결을 붙잡고 있다가 풀어 주는 DataSource다. 지연·복구 시나리오에 쓴다. */
    private static final class BlockingDataSource implements DataSource {

        private final AtomicInteger attempts = new AtomicInteger();
        private final CountDownLatch gate = new CountDownLatch(1);
        private volatile boolean shouldFail;

        @Override
        public Connection getConnection() throws SQLException {
            attempts.incrementAndGet();
            try {
                // 아직 풀리지 않았으면 붙잡아 DB 지연을 흉내 낸다.
                gate.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SQLException("interrupted", e);
            }
            if (shouldFail) {
                shouldFail = false;
                throw new SQLException("일시 장애");
            }
            Connection connection = mock(Connection.class);
            when(connection.isValid(anyInt())).thenReturn(true);
            return connection;
        }

        void releaseAll() {
            while (gate.getCount() > 0) {
                gate.countDown();
            }
        }

        void failNext() {
            this.shouldFail = true;
        }

        int attempts() {
            return attempts.get();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }

        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public java.util.logging.Logger getParentLogger() {
            return java.util.logging.Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
