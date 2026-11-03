package co.kr.spoit.admin.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * DB 연결 가능 여부를 확인한다.
 *
 * <p>연결 실패는 예외로 던져 요청을 깨뜨리지 않고 {@code false}로 알린다. 화면은 이 값을
 * "비정상" 상태로 표현한다. DB가 응답하지 않을 때 관리자 화면이 커넥션 풀 타임아웃만큼 멈추지
 * 않도록 확인 작업에 제한 시간을 두고, 최근 결과를 짧게 재사용한다.</p>
 *
 * <p>요청 폭주나 DB 지연이 작업 대기열을 무한히 키우지 않도록 두 겹으로 막는다.</p>
 * <ul>
 *   <li><b>single-flight</b>: 진행 중인 probe가 있으면 동시 요청은 새 작업을 만들지 않고 그
 *       결과를 함께 기다린다. 대기 중인 요청 수와 무관하게 실제 probe는 한 번만 실행된다.</li>
 *   <li><b>bounded worker</b>: 작업자는 1스레드 + {@link SynchronousQueue}(대기열 없음)다. 앞선
 *       probe가 아직 JDBC 호출에 묶여 있으면 새 작업은 즉시 거절되므로 대기열이 쌓이지 않는다.</li>
 * </ul>
 *
 * <p>타임아웃되면 단일 flight 참조를 비워 다음 요청이 다시 시도할 수 있게 한다(복구 감지). 실행
 * 중이던 작업은 취소하지 않고 그대로 두는데, 작업자는 검증 후 {@code try-with-resources}로
 * 커넥션을 반드시 닫기 때문이다.</p>
 */
@Component
public class DatabaseAvailabilityProbe {

    /** {@link Connection#isValid(int)} 검증 제한 시간(초)이다. */
    private static final int VALIDATION_TIMEOUT_SECONDS = 1;

    /** 연결 획득을 기다리는 기본 최대 시간이다. */
    private static final Duration DEFAULT_PROBE_TIMEOUT = Duration.ofSeconds(2);

    /** 같은 요청 폭주에서 반복 probe를 피하기 위한 기본 결과 재사용 시간이다. */
    private static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(10);

    private static final String WORKER_NAME = "system-monitor-db-probe";

    private final DataSource dataSource;
    private final Clock clock;
    private final long probeTimeoutMillis;
    private final long cacheTtlMillis;
    private final ThreadPoolExecutor probeExecutor;

    /** 단일 flight 참조와 캐시 갱신을 한 임계 구역에서 다룬다. */
    private final Object probeLock = new Object();

    /** 진행 중인 probe 작업이다. 없으면 {@code null}이다. */
    private CompletableFuture<Boolean> inFlight;

    private volatile CachedResult cached = new CachedResult(0L, false);

    @Autowired
    public DatabaseAvailabilityProbe(DataSource dataSource) {
        this(dataSource, Clock.systemUTC(), DEFAULT_PROBE_TIMEOUT, DEFAULT_CACHE_TTL);
    }

    /** 테스트에서 시각과 제한 시간을 고정하기 위한 생성자다. */
    DatabaseAvailabilityProbe(DataSource dataSource, Clock clock, Duration probeTimeout, Duration cacheTtl) {
        this.dataSource = dataSource;
        this.clock = clock;
        this.probeTimeoutMillis = probeTimeout.toMillis();
        this.cacheTtlMillis = cacheTtl.toMillis();
        // 대기열이 없는 워커다. 앞선 probe가 끝나지 않았으면 다음 작업은 거절되어 큐가 늘지 않는다.
        this.probeExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, WORKER_NAME);
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 연결을 얻어 유효성을 확인한다. 어떤 이유로든 실패하면 {@code false}다. */
    public boolean isReachable() {
        CachedResult snapshot = cached;
        if (clock.millis() - snapshot.checkedAt() < cacheTtlMillis) {
            return snapshot.reachable();
        }

        CompletableFuture<Boolean> probe = acquireProbe();
        if (probe == null) {
            // 워커도 대기열도 없다. 새 작업을 쌓지 않고 미확인으로 즉시 응답한다.
            return false;
        }

        try {
            boolean reachable = probe.get(probeTimeoutMillis, TimeUnit.MILLISECONDS);
            cached = new CachedResult(clock.millis(), reachable);
            return reachable;
        } catch (TimeoutException e) {
            // 다음 요청이 다시 시도할 수 있도록 참조만 비운다. 실행 중인 작업은 커넥션을 닫고 끝난다.
            releaseIfCurrent(probe);
            cached = new CachedResult(clock.millis(), false);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            releaseIfCurrent(probe);
            return false;
        } catch (ExecutionException e) {
            releaseIfCurrent(probe);
            cached = new CachedResult(clock.millis(), false);
            return false;
        }
    }

    /**
     * 진행 중인 probe를 공유하거나 새로 하나만 제출한다.
     *
     * @return 기다릴 probe. 워커가 포화 상태라 제출할 수 없으면 {@code null}
     */
    private CompletableFuture<Boolean> acquireProbe() {
        synchronized (probeLock) {
            if (inFlight != null && !inFlight.isDone()) {
                return inFlight;
            }
            CompletableFuture<Boolean> created = new CompletableFuture<>();
            try {
                probeExecutor.execute(() -> created.complete(runProbe()));
            } catch (RejectedExecutionException e) {
                // 대기열이 없으므로 이전 작업이 끝날 때까지는 새 probe를 만들지 않는다.
                return null;
            }
            inFlight = created;
            return created;
        }
    }

    private boolean runProbe() {
        try {
            return validateConnection();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 작업자는 예외와 무관하게 커넥션을 닫는다. */
    private boolean validateConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    private void releaseIfCurrent(CompletableFuture<Boolean> probe) {
        synchronized (probeLock) {
            if (inFlight == probe) {
                inFlight = null;
            }
        }
    }

    private record CachedResult(long checkedAt, boolean reachable) {
    }

    /** 애플리케이션 종료 시 probe 워커를 정리한다. */
    @PreDestroy
    void shutdown() {
        probeExecutor.shutdownNow();
    }
}
