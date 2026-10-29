package co.kr.spoit.facility.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.service.FacilityServiceImpl;

/**
 * 시설 수정·삭제가 동시 트랜잭션에서 stale snapshot을 쓰지 않는지 격리 MySQL에서 검증한다.
 *
 * <p>{@code MAT_TEST_DB_URL} 등 환경변수가 없으면 실행되지 않는다. 여러 연결의 commit/rollback을
 * 실제로 관찰해야 하므로 테스트 전체를 감싸는 test transaction을 쓰지 않는다
 * ({@code @Transactional(propagation = NOT_SUPPORTED)}). 대신 {@link #prepareCommittedFixture()}가
 * 이 테스트 전용 행만 커밋하고 {@link #deleteCommittedFixture()}가 그 식별자만 FK 역순으로 지운다.</p>
 *
 * <p>동기화는 {@link CountDownLatch}와 bounded wait/future timeout만 사용하며 sleep을 쓰지 않는다.
 * 락 대기 중 스레드가 남더라도 데몬 스레드로 만들어 JVM 종료를 막지 않는다.</p>
 *
 * <p><strong>한계:</strong> 매칭방 생성·상태 변경 코드가 아직 없으므로 이 테스트는 시설 수정·삭제
 * 경로의 locking read 계약만 검증한다. 생성 측이 같은 시설 행을 공통 락으로 직렬화하는지는
 * 증명하지 않는다. 매칭방은 테스트가 직접 커밋해 삭제 측의 최신 잠금 읽기만 확인한다.</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FacilityServiceImpl.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Timeout(90)
class FacilityConcurrentUpdateIntegrationTest {

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    /** 다른 fixture·테스트와 겹치지 않는 이 테스트 전용 식별자 대역이다. */
    private static final long USER_ID = 970001L;
    private static final long REGION_ID = 970001L;
    private static final long SPORT_A = 970001L;
    private static final long SPORT_B = 970002L;
    private static final long SPORT_C = 970003L;
    private static final long UPDATE_FACILITY_ID = 970001L;
    private static final long DELETE_FACILITY_ID = 970002L;
    private static final long MATCH_ID = 970001L;

    private static final String SPORT_A_NAME = "동시성 테스트 종목 A";
    private static final String SPORT_B_NAME = "동시성 테스트 종목 B";
    private static final String SPORT_C_NAME = "동시성 테스트 종목 C";

    private static final int WAIT_SECONDS = 30;
    /** 종료 대기는 전체 @Timeout 예산 안에서 정리 시간을 남기기 위해 더 짧게 잡는다. */
    private static final int TERMINATION_SECONDS = 10;

    private static final ThreadFactory DAEMON_THREADS = runnable -> {
        Thread thread = new Thread(runnable, "facility-concurrency-test");
        thread.setDaemon(true);
        return thread;
    };

    @Autowired
    private FacilityServiceImpl facilityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** fixture 커밋이 완전히 끝난 뒤에만 true가 된다. 실패 시 정리 단계에서 아무것도 지우지 않는다. */
    private boolean fixtureReady;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        String url = validateUrl(requiredEnvironmentVariable("MAT_TEST_DB_URL"));
        String username = requiredEnvironmentVariable("MAT_TEST_DB_USERNAME");
        String password = requiredEnvironmentVariable("MAT_TEST_DB_PASSWORD");

        registry.add("spring.datasource.url", () -> url + JDBC_OPTIONS);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    /** 테스트 데이터를 만들기 전에 전용 DB인지 먼저 확인한다. */
    @BeforeEach
    void prepareCommittedFixture() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"spoit_mat_test".equals(database)) {
            throw new IllegalStateException("전용 테스트 DB가 아니므로 fixture 생성을 중단합니다.");
        }
        // 이 테스트의 stale snapshot 시나리오는 MySQL 기본 REPEATABLE READ 전제 위에서만 의미가 있다.
        String isolation = jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class);
        if (!"REPEATABLE-READ".equals(isolation)) {
            throw new IllegalStateException("동시성 회귀 테스트는 REPEATABLE READ 격리를 전제로 합니다: " + isolation);
        }

        // 한 트랜잭션으로 커밋한다. 중간에 실패하면 아무 행도 남지 않으므로 cleanup 대상도 없다.
        txTemplate().executeWithoutResult(status -> insertFixture());

        fixtureReady = true;
    }

    /** 이 테스트가 만든 식별자만 FK 역순으로 삭제한다. */
    @AfterEach
    void deleteCommittedFixture() {
        if (!fixtureReady) {
            return;
        }
        txTemplate().executeWithoutResult(status -> {
            jdbcTemplate.update("DELETE FROM social_match WHERE facility_id IN (?, ?)",
                    UPDATE_FACILITY_ID, DELETE_FACILITY_ID);
            jdbcTemplate.update("DELETE FROM facility_sport WHERE facility_id IN (?, ?)",
                    UPDATE_FACILITY_ID, DELETE_FACILITY_ID);
            jdbcTemplate.update("DELETE FROM facility_manager WHERE facility_id IN (?, ?)",
                    UPDATE_FACILITY_ID, DELETE_FACILITY_ID);
            jdbcTemplate.update("DELETE FROM facility WHERE facility_id IN (?, ?)",
                    UPDATE_FACILITY_ID, DELETE_FACILITY_ID);
            jdbcTemplate.update("DELETE FROM sport WHERE sport_id IN (?, ?, ?)", SPORT_A, SPORT_B, SPORT_C);
            jdbcTemplate.update("DELETE FROM region WHERE region_id = ?", REGION_ID);
            jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ?", USER_ID);
        });
        fixtureReady = false;
    }

    @Test
    @DisplayName("동시 관리자 수정: T2가 RR 스냅샷을 먼저 잡아도 커밋된 최신 종목을 읽어 최종 종목은 {A}다")
    void concurrentUpdateUsesCurrentFacilitySportsInsteadOfStaleSnapshot() throws Exception {
        CountDownLatch t1Updated = new CountDownLatch(1);
        CountDownLatch t2SnapshotEstablished = new CountDownLatch(1);
        AtomicReference<Long> t1ConnectionId = new AtomicReference<>();
        AtomicReference<Long> t2ConnectionId = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2, DAEMON_THREADS);

        try {
            Future<?> t1 = pool.submit(() -> {
                try {
                    txTemplate().executeWithoutResult(status -> {
                        t1ConnectionId.set(currentConnectionId());
                        // {A,B} -> {A,C}
                        facilityService.updateFacility(USER_ID, UPDATE_FACILITY_ID,
                                form("동시성 수정 T1", "동시성 주소 T1", SPORT_A_NAME, SPORT_C_NAME));
                        t1Updated.countDown();
                        // T2가 스냅샷을 확립할 때까지 시설 행 잠금을 유지한 채 커밋을 미룬다.
                        await(t2SnapshotEstablished);
                    });
                } catch (RuntimeException | Error failure) {
                    // T2가 대기에서 풀려나 실패를 관찰하도록 latch를 반드시 해제한다.
                    t1Updated.countDown();
                    throw failure;
                }
            });

            Future<?> t2 = pool.submit(() -> {
                try {
                    await(t1Updated);
                    txTemplate().executeWithoutResult(status -> {
                        t2ConnectionId.set(currentConnectionId());
                        // T1 커밋 전 일반 SELECT로 오래된 InnoDB snapshot을 확립한다.
                        assertThat(sportIdsOf(UPDATE_FACILITY_ID))
                                .as("T1 커밋 전 스냅샷은 {A,B}여야 한다")
                                .containsExactly(SPORT_A, SPORT_B);
                        t2SnapshotEstablished.countDown();
                        // 잠금 읽기가 아니면 위 스냅샷으로 diff를 계산해 C를 놓친다.
                        facilityService.updateFacility(USER_ID, UPDATE_FACILITY_ID,
                                form("동시성 수정 T2", "동시성 주소 T2", SPORT_A_NAME));
                    });
                } catch (RuntimeException | Error failure) {
                    t2SnapshotEstablished.countDown();
                    throw failure;
                }
            });

            awaitCompletion(t1, t2);
        } finally {
            // 어떤 경로로 끝나도 대기 중인 스레드를 풀어준다.
            t1Updated.countDown();
            t2SnapshotEstablished.countDown();
            shutdown(pool);
        }

        assertThat(t1ConnectionId.get()).isNotNull();
        assertThat(t2ConnectionId.get()).isNotNull();
        assertThat(t2ConnectionId.get()).as("두 트랜잭션은 서로 다른 연결이어야 한다")
                .isNotEqualTo(t1ConnectionId.get());
        assertThat(sportIdsOf(UPDATE_FACILITY_ID))
                .as("T2는 T1이 커밋한 {A,C}를 읽어 C만 제거해야 한다")
                .containsExactly(SPORT_A);
    }

    @Test
    @DisplayName("삭제: 다른 연결이 커밋한 모집 중 매칭방을 최신 잠금 읽기로 찾아 거부하고 deleted_at은 NULL로 남는다")
    void deleteRejectsRecruitingMatchCommittedByAnotherConnection() throws Exception {
        AtomicReference<Long> deleteConnectionId = new AtomicReference<>();
        AtomicLong inserterConnectionId = new AtomicLong(-1);
        ExecutorService pool = Executors.newSingleThreadExecutor(DAEMON_THREADS);

        try {
            assertThatThrownBy(() -> txTemplate().executeWithoutResult(status -> {
                deleteConnectionId.set(currentConnectionId());
                // 아직 매칭방이 없는 시점에 일반 SELECT로 snapshot을 확립한다.
                assertThat(matchIdsOf(DELETE_FACILITY_ID))
                        .as("스냅샷 확립 시점에는 모집 중 매칭방이 없어야 한다")
                        .isEmpty();

                // 다른 연결에서 모집 중 매칭방을 만들고 커밋한다.
                Future<Long> committed = pool.submit(() -> {
                    AtomicLong connectionId = new AtomicLong();
                    txTemplate().executeWithoutResult(inner -> {
                        connectionId.set(currentConnectionId());
                        insertRecruitingMatch();
                    });
                    return connectionId.get();
                });
                try {
                    inserterConnectionId.set(committed.get(WAIT_SECONDS, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("다른 연결의 매칭방 커밋 대기가 중단되었습니다.", exception);
                } catch (ExecutionException | TimeoutException failure) {
                    throw new IllegalStateException("다른 연결의 매칭방 커밋을 확인하지 못했습니다.", failure);
                }

                // 스냅샷에는 보이지 않지만 잠금 읽기는 커밋된 최신 행을 봐야 한다.
                facilityService.deleteFacility(USER_ID, DELETE_FACILITY_ID);
            })).isInstanceOf(IllegalArgumentException.class);
        } finally {
            shutdown(pool);
        }

        assertThat(deleteConnectionId.get()).isNotNull();
        assertThat(inserterConnectionId.get())
                .as("매칭방을 커밋한 연결은 삭제 트랜잭션과 달라야 한다")
                .isPositive()
                .isNotEqualTo(deleteConnectionId.get().longValue());

        // 다른 연결의 INSERT는 실제로 커밋됐다.
        assertThat(matchIdsOf(DELETE_FACILITY_ID)).containsExactly(MATCH_ID);
        // 실패한 삭제 트랜잭션은 롤백되어 deleted_at이 남지 않는다.
        assertThat(isFacilityDeleted(DELETE_FACILITY_ID)).isFalse();
    }

    /**
     * 전용 fixture를 한 트랜잭션으로 저장한다.
     *
     * <p>수정 시나리오용 시설은 {A,B}, 삭제 시나리오용 시설은 {A}를 제공한다. 매칭방은 테스트가
     * 직접 만들므로 여기서는 만들지 않는다.</p>
     */
    private void insertFixture() {
        jdbcTemplate.update("""
                INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                USER_ID, "facility-concurrency@example.invalid", "TEST_ONLY_NOT_A_PASSWORD_HASH",
                "동시성 시설 관리자", "010-9700-0001", "시설 관리자",
                LocalDateTime.of(2026, 10, 20, 9, 0));

        jdbcTemplate.update("INSERT INTO region (region_id, parent_region_id, name) VALUES (?, NULL, ?)",
                REGION_ID, "동시성 테스트 지역");

        jdbcTemplate.update("INSERT INTO sport (sport_id, name) VALUES (?, ?), (?, ?), (?, ?)",
                SPORT_A, SPORT_A_NAME, SPORT_B, SPORT_B_NAME, SPORT_C, SPORT_C_NAME);

        jdbcTemplate.update("""
                INSERT INTO facility (
                    facility_id, region_id, name, address, latitude, longitude,
                    concurrent_match_limit, status, deleted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
                """,
                UPDATE_FACILITY_ID, REGION_ID, "동시성 수정 시설", "동시성 수정 주소",
                new BigDecimal("37.5000000"), new BigDecimal("127.0000000"), 3, "운영 중");

        jdbcTemplate.update("""
                INSERT INTO facility (
                    facility_id, region_id, name, address, latitude, longitude,
                    concurrent_match_limit, status, deleted_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
                """,
                DELETE_FACILITY_ID, REGION_ID, "동시성 삭제 시설", "동시성 삭제 주소",
                new BigDecimal("37.5001000"), new BigDecimal("127.0001000"), 2, "운영 중");

        jdbcTemplate.update("""
                INSERT INTO facility_manager (facility_id, user_id, assigned_at)
                VALUES (?, ?, ?), (?, ?, ?)
                """,
                UPDATE_FACILITY_ID, USER_ID, LocalDateTime.of(2026, 10, 20, 9, 0),
                DELETE_FACILITY_ID, USER_ID, LocalDateTime.of(2026, 10, 20, 9, 0));

        jdbcTemplate.update("""
                INSERT INTO facility_sport (
                    facility_id, sport_id, default_min, default_max, default_fee, default_skill
                ) VALUES (?, ?, 1, 2, 0.00, '무관'), (?, ?, 1, 2, 0.00, '무관'), (?, ?, 1, 2, 0.00, '무관')
                """,
                UPDATE_FACILITY_ID, SPORT_A, UPDATE_FACILITY_ID, SPORT_B, DELETE_FACILITY_ID, SPORT_A);
    }

    /** 삭제 시나리오의 모집 중 매칭방이다. schema.sql의 CHECK·FK 규칙을 만족한다. */
    private void insertRecruitingMatch() {
        jdbcTemplate.update("""
                INSERT INTO social_match (
                    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                    min_participants, max_participants, team_a_capacity, team_b_capacity,
                    fee_per_person, required_skill, status, assignment_method, rating_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                MATCH_ID, DELETE_FACILITY_ID, SPORT_A, USER_ID, "동시성 테스트 모집 중 매칭방",
                LocalDateTime.of(2026, 10, 22, 10, 0), LocalDateTime.of(2026, 10, 22, 12, 0),
                1, 2, 1, 1, new BigDecimal("0.00"), "무관", "모집 중", "MANUAL", "WAITING");
    }

    private TransactionTemplate txTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private Long currentConnectionId() {
        return jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class);
    }

    private List<Long> sportIdsOf(long facilityId) {
        return jdbcTemplate.queryForList(
                "SELECT sport_id FROM facility_sport WHERE facility_id = ? ORDER BY sport_id ASC",
                Long.class, facilityId);
    }

    private List<Long> matchIdsOf(long facilityId) {
        return jdbcTemplate.queryForList(
                "SELECT match_id FROM social_match WHERE facility_id = ? ORDER BY match_id ASC",
                Long.class, facilityId);
    }

    private boolean isFacilityDeleted(long facilityId) {
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM facility WHERE facility_id = ? AND deleted_at IS NOT NULL",
                Integer.class, facilityId);
        return deleted != null && deleted > 0;
    }

    private static FacilityForm form(String name, String address, String... sports) {
        FacilityForm form = new FacilityForm();
        form.setName(name);
        form.setAddress(address);
        form.setSports(List.of(sports));
        form.setConcurrentMatchLimit(3);
        form.setStatus("운영 중");
        return form;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
        }
    }

    private static void awaitCompletion(Future<?> first, Future<?> second) throws Exception {
        Throwable failure = null;
        boolean interrupted = false;
        for (Future<?> future : List.of(first, second)) {
            try {
                future.get(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                if (failure == null) {
                    failure = exception.getCause();
                }
            } catch (TimeoutException exception) {
                if (failure == null) {
                    failure = new IllegalStateException("동시성 테스트 스레드가 제한 시간 안에 끝나지 않았습니다.", exception);
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
    private static void shutdown(ExecutorService pool) {
        pool.shutdownNow();
        try {
            pool.awaitTermination(TERMINATION_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    static String validateUrl(String url) {
        var matcher = TEST_DATABASE_URL.matcher(url == null ? "" : url);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "MAT_TEST_DB_URL은 localhost 또는 127.0.0.1의 명시적 포트와 spoit_mat_test DB를 지정해야 합니다.");
        }
        int port = Integer.parseInt(matcher.group(1));
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("MAT_TEST_DB_URL의 포트는 1~65535 범위여야 합니다.");
        }
        return url;
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다. 개발 DB 설정으로 대체하지 않습니다.");
        }
        return value;
    }
}
