package co.kr.spoit.support;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 여러 연결의 commit/rollback을 관찰하는 동시성 통합 테스트가 공유하는 동기화 도구다.
 *
 * <p>sleep으로 순서를 맞추지 않고 {@link CountDownLatch}와 bounded wait/future timeout만 쓴다.
 * 모든 대기는 시간 상한을 가지므로 락 대기로 멈춘 스레드가 테스트를 무한정 붙잡지 않는다.</p>
 *
 * <p>스레드는 데몬으로 만든다. 락 대기 중인 스레드가 남아 있어도 JVM 종료를 막지 않게 하기 위해서다.
 * 스레드 이름은 테스트별로 다르게 받아 스레드 덤프에서 어느 테스트가 남긴 스레드인지 구분한다.</p>
 */
public final class ConcurrentTestSupport {

    /** latch 대기와 future 완료 대기에 공통으로 쓰는 상한이다. */
    public static final int WAIT_SECONDS = 30;

    /** 종료 대기는 전체 {@code @Timeout} 예산 안에서 정리 시간을 남기기 위해 더 짧게 잡는다. */
    private static final int TERMINATION_SECONDS = 10;

    private ConcurrentTestSupport() {
    }

    /** 주어진 이름의 데몬 스레드를 만드는 팩토리다. */
    public static ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** latch를 제한 시간까지 기다린다. 시간 초과와 인터럽트는 테스트 실패로 승격한다. */
    public static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
        }
    }

    /**
     * 모든 future가 제한 시간 안에 끝나기를 기다린다.
     *
     * <p>첫 번째 실패만 보고하되 나머지 future도 계속 기다려 스레드가 정리될 시간을 준다. 인터럽트는
     * 삼키지 않고 마지막에 현재 스레드의 interrupt 상태로 복원한다.</p>
     */
    public static void awaitCompletion(Future<?>... futures) throws Exception {
        Throwable failure = null;
        boolean interrupted = false;
        for (Future<?> future : futures) {
            try {
                future.get(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                if (failure == null) {
                    failure = exception.getCause();
                }
            } catch (TimeoutException exception) {
                if (failure == null) {
                    failure = new IllegalStateException(
                            "동시성 테스트 스레드가 제한 시간 안에 끝나지 않았습니다.", exception);
                }
            } catch (InterruptedException exception) {
                interrupted = true;
                if (failure == null) {
                    failure = new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
    }

    /** 락 대기로 멈춘 스레드가 있어도 데몬 스레드이므로 JVM 종료를 막지 않는다. */
    public static void shutdown(ExecutorService pool) {
        pool.shutdownNow();
        try {
            pool.awaitTermination(TERMINATION_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
