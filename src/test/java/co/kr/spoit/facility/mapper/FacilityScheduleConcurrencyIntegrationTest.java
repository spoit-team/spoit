package co.kr.spoit.facility.mapper;

import static co.kr.spoit.support.ConcurrentTestSupport.await;
import static co.kr.spoit.support.ConcurrentTestSupport.awaitCompletion;
import static co.kr.spoit.support.ConcurrentTestSupport.shutdown;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import co.kr.spoit.facility.domain.FacilityClosure;
import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.facility.service.FacilityScheduleUnavailableException;
import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.ApplicationType;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.support.ConcurrentTestSupport;
import co.kr.spoit.support.MySqlTestDatabase;

/**
 * 기존 매칭방의 신규 참가 신청이 시설 운영 일정의 최신 값을 잠금 읽기로 확인하는지, 그리고 응용
 * 신청과 관리자 운영 일정 저장이 같은 시설 행 잠금으로 직렬화되는지 격리 MySQL에서 검증한다.
 *
 * <p>확정 정책: 기존 방이라도 시설 운영 시간이 미설정·휴관·운영 시간 밖이면 신규 신청을 거부하고,
 * 이미 접수된 신청은 그대로 둔다. 잠금 순서는 시설 → 매칭방이며, 신청 트랜잭션이 먼저 만든
 * REPEATABLE READ 스냅샷과 무관하게 시설 서비스는 잠금 읽기로 최신 일정을 다시 읽어야 한다.</p>
 *
 * <p>검증 대상은 실제 빈이다. {@link MatchApplicationService#applyIndividual(long, long)}와
 * {@link FacilityReservationService}를 애플리케이션 컨텍스트에서 주입받아
 * {@code lockActiveFacility(long)} · {@code requireAvailable(long, LocalDateTime, LocalDateTime)}
 * 계약을 그대로 호출한다. 그래서 {@code @MybatisTest} 대신 전체 컨텍스트를 띄운다.</p>
 *
 * <p>{@code MAT_TEST_DB_URL} 등 환경변수가 없으면 실행되지 않는다. 여러 연결의 commit을 실제로
 * 관찰해야 하므로 test transaction으로 감싸지 않는다({@code @Transactional(NOT_SUPPORTED)}).
 * 대신 {@link #prepareCommittedFixture()}가 이 테스트 전용 행만 커밋하고
 * {@link #deleteCommittedFixture()}가 같은 식별자만 FK 역순으로 지운다.</p>
 *
 * <p><strong>시뮬레이션:</strong> 관리자 운영 일정 저장 경로는 별도 구현이므로 이 테스트는 그 내부
 * 구현에 의존하지 않는다. 대신 저장 트랜잭션이 지켜야 할 저수준 계약, 즉 "대상 시설 행을 먼저
 * 잠그고 그 뒤에 일정 데이터를 쓴다"만 직접 JDBC로 재현한다. 실제 저장 서비스는 잠금 후 활성
 * 매칭방과의 충돌을 검사해 거부할 수 있지만, 이 테스트가 고정하는 것은 저장 API의 정책이 아니라
 * 잠금 순서(시설 → 일정 데이터)와 신청 경로의 최신 잠금 읽기 계약이다.</p>
 *
 * <p>동기화는 {@link CountDownLatch}와 bounded wait/future timeout만 사용하고 sleep을 쓰지 않는다.
 * 락 대기 중 남은 스레드가 JVM 종료를 막지 않도록 데몬 스레드로 만든다.</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Timeout(120)
class FacilityScheduleConcurrencyIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 하드코딩된 과거 시각을 피하려고 실행 시점 기준 미래 날짜를 쓴다. fixture와 단언이 같은 값을
     * 공유하므로 요일과 무관하게 결정적이다(운영 규칙을 7요일 모두 넣는다).
     */
    private static final LocalDate TARGET_DATE = LocalDate.now(ZONE).plusDays(7);
    private static final LocalDateTime MATCH_START = TARGET_DATE.atTime(10, 0);
    private static final LocalDateTime MATCH_END = TARGET_DATE.atTime(12, 0);
    /** 운영 종료(18:00)를 넘어가는 구간이라 "운영 시간 밖" 판정 대상이다. */
    private static final LocalDateTime STRADDLING_START = TARGET_DATE.atTime(17, 0);
    private static final LocalDateTime STRADDLING_END = TARGET_DATE.atTime(19, 0);
    /** 두 자연일에 걸친 구간이다. 일정 계산이 자정에서 나누어 판정해야 허용된다. */
    private static final LocalDateTime CROSS_MIDNIGHT_START = TARGET_DATE.atTime(23, 0);
    private static final LocalDateTime CROSS_MIDNIGHT_END = TARGET_DATE.plusDays(1).atTime(1, 0);

    /** 다른 fixture·테스트와 겹치지 않는 이 테스트 전용 식별자 대역이다. */
    private static final long APPLICANT_USER_ID = 980001L;
    private static final long EXISTING_APPLICANT_USER_ID = 980002L;
    private static final long MANAGER_USER_ID = 980003L;
    private static final long FRESH_APPLICANT_USER_ID = 980004L;

    private static final long REGION_ID = 980001L;
    private static final long SPORT_ID = 980001L;

    private static final long OPEN_FACILITY_ID = 980001L;
    private static final long UNCONFIGURED_FACILITY_ID = 980002L;
    private static final long CLOSURE_FACILITY_ID = 980003L;
    private static final long OVERNIGHT_FACILITY_ID = 980004L;
    private static final long RESERVATION_FACILITY_ID = 980005L;
    /** 동시성 테스트가 일정을 휴무로 바꾸므로, 다른 시나리오와 상태를 공유하지 않도록 따로 둔다. */
    private static final long STRADDLING_FACILITY_ID = 980006L;

    private static final long CONCURRENT_MATCH_ID = 980001L;
    private static final long STRADDLING_MATCH_ID = 980002L;
    private static final long UNCONFIGURED_MATCH_ID = 980003L;
    private static final long CLOSURE_MATCH_ID = 980004L;
    private static final long APPLICATION_LOCK_MATCH_ID = 980005L;
    private static final long CROSS_MIDNIGHT_MATCH_ID = 980006L;

    private static final long MATCH_ID_FIRST = 980001L;
    private static final long MATCH_ID_LAST = 980006L;
    private static final long FACILITY_ID_FIRST = 980001L;
    private static final long FACILITY_ID_LAST = 980006L;
    private static final long USER_ID_FIRST = 980001L;
    private static final long USER_ID_LAST = 980004L;

    private static final String PASSWORD_HASH = "TEST_ONLY_NOT_A_PASSWORD_HASH";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 20, 9, 0);

    /**
     * 락을 기다려야 하는 쪽이 "제한 시간 안에 잠금을 얻지 못했음"을 관찰하는 짧은 상한이다.
     * 상대가 이미 락을 보유한 뒤에만 쓰므로 sleep으로 순서를 맞추지 않는다.
     */
    private static final int BLOCKED_CONFIRM_SECONDS = 2;

    /** MySQL ER_LOCK_NOWAIT: {@code FOR UPDATE NOWAIT}가 잠금 대기 없이 즉시 실패할 때의 오류 코드다. */
    private static final int MYSQL_LOCK_NOWAIT = 3572;

    private static final ThreadFactory DAEMON_THREADS =
            ConcurrentTestSupport.daemonThreads("facility-schedule-concurrency-test");

    @Autowired
    private MatchApplicationService matchApplicationService;

    @Autowired
    private FacilityReservationService facilityReservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** fixture 커밋이 완전히 끝난 뒤에만 true가 된다. 실패 시 정리 단계에서 아무것도 지우지 않는다. */
    private boolean fixtureReady;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.registerDataSource(registry);
    }

    /** 테스트 데이터를 만들기 전에 전용 DB·격리를 먼저 확인한다. */
    @BeforeEach
    void prepareCommittedFixture() {
        MySqlTestDatabase.assertDedicatedDatabaseBeforeFixtureCreation(jdbcTemplate);
        // stale snapshot 시나리오는 MySQL 기본 REPEATABLE READ 전제 위에서만 의미가 있다.
        MySqlTestDatabase.assertRepeatableReadIsolation(jdbcTemplate);

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
            jdbcTemplate.update("DELETE FROM match_participant WHERE match_id BETWEEN ? AND ?",
                    MATCH_ID_FIRST, MATCH_ID_LAST);
            jdbcTemplate.update("DELETE FROM match_application WHERE match_id BETWEEN ? AND ?",
                    MATCH_ID_FIRST, MATCH_ID_LAST);
            jdbcTemplate.update("DELETE FROM social_match WHERE match_id BETWEEN ? AND ?",
                    MATCH_ID_FIRST, MATCH_ID_LAST);
            jdbcTemplate.update("DELETE FROM schedule_change WHERE facility_id BETWEEN ? AND ?",
                    FACILITY_ID_FIRST, FACILITY_ID_LAST);
            jdbcTemplate.update("DELETE FROM operating_rule WHERE facility_id BETWEEN ? AND ?",
                    FACILITY_ID_FIRST, FACILITY_ID_LAST);
            jdbcTemplate.update("DELETE FROM facility_sport WHERE facility_id BETWEEN ? AND ?",
                    FACILITY_ID_FIRST, FACILITY_ID_LAST);
            jdbcTemplate.update("DELETE FROM facility_manager WHERE facility_id BETWEEN ? AND ?",
                    FACILITY_ID_FIRST, FACILITY_ID_LAST);
            jdbcTemplate.update("DELETE FROM facility WHERE facility_id BETWEEN ? AND ?",
                    FACILITY_ID_FIRST, FACILITY_ID_LAST);
            jdbcTemplate.update("DELETE FROM sport WHERE sport_id = ?", SPORT_ID);
            jdbcTemplate.update("DELETE FROM region WHERE region_id = ?", REGION_ID);
            jdbcTemplate.update("DELETE FROM app_user WHERE user_id BETWEEN ? AND ?",
                    USER_ID_FIRST, USER_ID_LAST);
        });
        fixtureReady = false;
    }

    @Test
    @DisplayName("동시 신청: T2가 오래된 RR 스냅샷을 잡아도 시설 락 뒤 최신 휴무 일정을 읽어 신청을 만들지 않는다")
    void rejectsApplicationUsingLatestScheduleInsteadOfStaleSnapshot() throws Exception {
        CountDownLatch scheduleLocked = new CountDownLatch(1);
        CountDownLatch staleSnapshotTaken = new CountDownLatch(1);
        AtomicReference<Long> scheduleConnectionId = new AtomicReference<>();
        AtomicReference<Long> applicationConnectionId = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2, DAEMON_THREADS);

        try {
            Future<?> scheduleChange = pool.submit(() -> {
                try {
                    txTemplate().executeWithoutResult(status -> {
                        scheduleConnectionId.set(currentConnectionId());
                        // 저장 API 계약 재현: 시설 행을 먼저 잠그고 일정 데이터를 바꾼다.
                        facilityReservationService.lockActiveFacility(OPEN_FACILITY_ID);
                        jdbcTemplate.update(
                                "UPDATE operating_rule SET is_open = FALSE WHERE facility_id = ?",
                                OPEN_FACILITY_ID);
                        scheduleLocked.countDown();
                        // 신청 트랜잭션이 스냅샷을 확립할 때까지 시설 행 잠금을 유지한 채 커밋을 미룬다.
                        await(staleSnapshotTaken);
                    });
                } catch (RuntimeException | Error failure) {
                    // 상대가 대기에서 풀려나 실패를 관찰하도록 latch를 반드시 해제한다.
                    scheduleLocked.countDown();
                    throw failure;
                }
            });

            Future<?> application = pool.submit(() -> {
                try {
                    await(scheduleLocked);
                    txTemplate().executeWithoutResult(status -> {
                        applicationConnectionId.set(currentConnectionId());
                        // T1 커밋 전 일반 SELECT로 오래된 InnoDB 스냅샷을 확립한다.
                        assertThat(openRuleCount(OPEN_FACILITY_ID))
                                .as("T1 커밋 전 스냅샷은 7요일 모두 운영 중이어야 한다")
                                .isEqualTo(7);
                        staleSnapshotTaken.countDown();
                        // 시설 락을 기다린 뒤 잠금 읽기로 최신 일정을 다시 읽어야 이 신청이 거부된다.
                        matchApplicationService.applyIndividual(CONCURRENT_MATCH_ID, APPLICANT_USER_ID);
                    });
                } catch (RuntimeException | Error failure) {
                    staleSnapshotTaken.countDown();
                    throw failure;
                }
            });

            // 일정 저장 쪽은 신청 결과와 무관하게 커밋까지 끝난다. 먼저 확인해 인프라 실패를
            // "예외가 발생하지 않음" 같은 모호한 실패로 감추지 않는다.
            awaitCompletion(scheduleChange);
            assertThatThrownBy(() -> awaitCompletion(application))
                    .as("최신 일정이 휴무이므로 신청은 시설 안내 예외로 실패해야 한다")
                    .isInstanceOf(MatchApplyException.class)
                    .hasMessageContaining("시설");
        } finally {
            // 어떤 경로로 끝나도 대기 중인 스레드를 풀어준다.
            scheduleLocked.countDown();
            staleSnapshotTaken.countDown();
            shutdown(pool);
        }

        assertThat(scheduleConnectionId.get()).isNotNull();
        assertThat(applicationConnectionId.get()).isNotNull();
        assertThat(applicationConnectionId.get())
                .as("일정 저장과 신청은 서로 다른 연결이어야 한다")
                .isNotEqualTo(scheduleConnectionId.get());
        // 커밋된 최신 일정 자체가 불가 상태임을 별도로 확인해 실패 사유를 고정한다.
        assertThat(openRuleCount(OPEN_FACILITY_ID)).isZero();
        assertThatThrownBy(() -> facilityReservationService.requireAvailable(
                OPEN_FACILITY_ID, MATCH_START, MATCH_END))
                .isInstanceOf(FacilityScheduleUnavailableException.class);
        assertRejectedWithoutNewApplication(CONCURRENT_MATCH_ID);
    }

    @Test
    @DisplayName("역순: 응용 신청이 시설 락을 쥐고 있는 동안 일정 저장 트랜잭션은 락을 기다렸다가 끝난다")
    void scheduleChangeWaitsWhileApplicationHoldsFacilityLock() throws Exception {
        CountDownLatch applicationLocked = new CountDownLatch(1);
        CountDownLatch releaseApplication = new CountDownLatch(1);
        CountDownLatch scheduleSaveStarted = new CountDownLatch(1);
        CountDownLatch scheduleLockAcquired = new CountDownLatch(1);
        AtomicReference<Long> applicationConnectionId = new AtomicReference<>();
        AtomicReference<Long> scheduleConnectionId = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2, DAEMON_THREADS);

        try {
            Future<?> application = pool.submit(() -> {
                try {
                    txTemplate().executeWithoutResult(status -> {
                        applicationConnectionId.set(currentConnectionId());
                        // 신청이 성공해 시설 행 잠금을 보유한 상태로 커밋을 미룬다.
                        matchApplicationService.applyIndividual(
                                APPLICATION_LOCK_MATCH_ID, APPLICANT_USER_ID);
                        applicationLocked.countDown();
                        await(releaseApplication);
                    });
                } catch (RuntimeException | Error failure) {
                    applicationLocked.countDown();
                    throw failure;
                }
            });
            await(applicationLocked);

            // 같은 시설 행을 NOWAIT로 잠그려 하면 즉시 실패해야 한다. 신청 트랜잭션이 실제로
            // 그 행 잠금을 보유하고 있다는 결정적 증거이며, 시간 추정에 기대지 않는다.
            assertFacilityRowLockedByAnotherTransaction();

            Future<?> scheduleChange = pool.submit(() -> txTemplate().executeWithoutResult(status -> {
                scheduleConnectionId.set(currentConnectionId());
                // 저장 API 계약 재현: 대상 시설 행을 먼저 잠근다. 신청이 락을 쥐고 있으면 여기서 기다린다.
                scheduleSaveStarted.countDown();
                assertThat(jdbcTemplate.queryForList("""
                        SELECT facility_id FROM facility
                         WHERE facility_id = ? AND deleted_at IS NULL
                           FOR UPDATE
                        """, Long.class, RESERVATION_FACILITY_ID))
                        .as("저장 트랜잭션이 시설 행을 잠가야 한다")
                        .containsExactly(RESERVATION_FACILITY_ID);
                scheduleLockAcquired.countDown();
                jdbcTemplate.update(
                        "UPDATE operating_rule SET is_open = FALSE WHERE facility_id = ?",
                        RESERVATION_FACILITY_ID);
            }));
            await(scheduleSaveStarted);
            // 신청이 시설 락을 쥐고 있는 동안 저장 트랜잭션은 절대 그 락을 얻을 수 없다.
            // 따라서 잠금 획득 신호는 제한 시간 안에 오지 않아야 한다(시간 추정이 아닌 필연).
            assertThat(scheduleLockAcquired.await(BLOCKED_CONFIRM_SECONDS, TimeUnit.SECONDS))
                    .as("신청이 시설 락을 보유한 동안 일정 저장은 락을 얻지 못해야 한다")
                    .isFalse();
            assertFacilityRowLockedByAnotherTransaction();
            assertThat(openRuleCount(RESERVATION_FACILITY_ID))
                    .as("락을 기다리는 저장 트랜잭션의 변경은 아직 커밋되지 않아야 한다")
                    .isEqualTo(7);

            releaseApplication.countDown();
            awaitCompletion(application);
            awaitCompletion(scheduleChange);
        } finally {
            applicationLocked.countDown();
            releaseApplication.countDown();
            shutdown(pool);
        }

        assertThat(applicationConnectionId.get()).isNotNull();
        assertThat(scheduleConnectionId.get()).isNotNull();
        assertThat(scheduleConnectionId.get())
                .as("신청과 일정 저장은 서로 다른 연결이어야 한다")
                .isNotEqualTo(applicationConnectionId.get());
        assertThat(scheduleLockAcquired.getCount())
                .as("저장 트랜잭션은 신청이 끝난 뒤 시설 락을 얻고 완료됐어야 한다")
                .isZero();
        // 신청이 끝난 뒤에는 같은 시설 행을 다시 잠글 수 있다.
        assertThat(tryLockFacilityRowNowait())
                .as("신청 트랜잭션이 끝나면 시설 행 잠금을 다시 얻을 수 있어야 한다")
                .isTrue();
        // 앞서 접수된 신청은 일정 저장 이후에도 유지된다.
        assertThat(applicationCount(APPLICATION_LOCK_MATCH_ID, APPLICANT_USER_ID)).isEqualTo(1);
        // 저장이 끝나 운영이 닫힌 뒤 같은 시간대 신규 신청은 일정 사유로 거부되고 행을 남기지 않는다.
        assertThat(openRuleCount(RESERVATION_FACILITY_ID)).isZero();
        assertRejectedByFacilitySchedule(APPLICATION_LOCK_MATCH_ID, FRESH_APPLICANT_USER_ID);
        assertThat(applicationCount(APPLICATION_LOCK_MATCH_ID, FRESH_APPLICANT_USER_ID))
                .as("거부된 신규 신청은 행을 만들지 않아야 한다")
                .isZero();
        assertThat(applicationCount(APPLICATION_LOCK_MATCH_ID, EXISTING_APPLICANT_USER_ID))
                .as("기존 신청은 유지되어야 한다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("운영 일정이 미설정인 시설의 기존 매칭방은 신규 신청을 거부하고 기존 신청은 유지한다")
    void rejectsApplicationWhenFacilityScheduleIsUnconfigured() {
        assertThat(ruleRowCount(UNCONFIGURED_FACILITY_ID))
                .as("미설정 시설 fixture에는 운영 규칙 행이 없어야 한다")
                .isZero();
        assertThatThrownBy(() -> facilityReservationService.requireAvailable(
                UNCONFIGURED_FACILITY_ID, MATCH_START, MATCH_END))
                .isInstanceOf(FacilityScheduleUnavailableException.class);
        assertRejectedByFacilitySchedule(UNCONFIGURED_MATCH_ID, APPLICANT_USER_ID);

        assertRejectedWithoutNewApplication(UNCONFIGURED_MATCH_ID);
    }

    @Test
    @DisplayName("휴관 일정이 잡힌 시설의 기존 매칭방은 신규 신청을 거부하고 기존 신청은 유지한다")
    void rejectsApplicationWhenFacilityIsClosedForMatchPeriod() {
        // 신청 경로만 검증하려고 휴관 행을 직접 커밋한다. 실제 휴관 등록 API는 겹치는 모집 중
        // 매칭방이 있으면 저장을 거부하므로, 이 상태는 "이미 저장된 휴관이 있는 기존 방"을 뜻한다.
        insertClosure(CLOSURE_FACILITY_ID, MATCH_START.minusHours(1), MATCH_END.plusHours(1));

        assertThat(openRuleCount(CLOSURE_FACILITY_ID))
                .as("휴관 시나리오라도 주간 규칙 자체는 운영 중이어야 한다")
                .isEqualTo(7);
        assertThatThrownBy(() -> facilityReservationService.requireAvailable(
                CLOSURE_FACILITY_ID, MATCH_START, MATCH_END))
                .isInstanceOf(FacilityScheduleUnavailableException.class);
        assertRejectedByFacilitySchedule(CLOSURE_MATCH_ID, APPLICANT_USER_ID);

        assertRejectedWithoutNewApplication(CLOSURE_MATCH_ID);
    }

    @Test
    @DisplayName("운영 종료 시각을 넘는 구간의 기존 매칭방은 신규 신청을 거부하고 기존 신청은 유지한다")
    void rejectsApplicationWhenMatchExceedsOperatingHours() {
        assertThat(openRuleCount(STRADDLING_FACILITY_ID)).isEqualTo(7);
        assertThatThrownBy(() -> facilityReservationService.requireAvailable(
                STRADDLING_FACILITY_ID, STRADDLING_START, STRADDLING_END))
                .isInstanceOf(FacilityScheduleUnavailableException.class);
        assertRejectedByFacilitySchedule(STRADDLING_MATCH_ID, APPLICANT_USER_ID);

        assertRejectedWithoutNewApplication(STRADDLING_MATCH_ID);
    }

    @Test
    @DisplayName("자정을 넘는 구간도 자연일로 나누어 판정해 야간 운영 시설의 기존 매칭방 신청을 허용한다")
    void acceptsCrossMidnightMatchWithinOvernightOperatingHours() {
        assertThat(openRuleCount(OVERNIGHT_FACILITY_ID)).isEqualTo(7);

        String receiptNo = matchApplicationService.applyIndividual(
                CROSS_MIDNIGHT_MATCH_ID, FRESH_APPLICANT_USER_ID);

        assertThat(receiptNo).startsWith("RCPT-");
        assertThat(applicationCount(CROSS_MIDNIGHT_MATCH_ID, FRESH_APPLICANT_USER_ID)).isEqualTo(1);
        assertThat(applicationCount(CROSS_MIDNIGHT_MATCH_ID, EXISTING_APPLICANT_USER_ID))
                .as("기존 신청은 그대로 남아야 한다")
                .isEqualTo(1);
    }

    /**
     * 전용 fixture를 한 트랜잭션으로 저장한다.
     *
     * <p>운영 시설(OPEN_FACILITY_ID)·휴관 시나리오 시설(CLOSURE_FACILITY_ID)·역순 시나리오
     * 시설(RESERVATION_FACILITY_ID)·운영 시간 밖 시나리오 시설(STRADDLING_FACILITY_ID)은 7요일
     * 09:00~18:00, 미설정 시설(UNCONFIGURED_FACILITY_ID)은 운영 규칙이 없고, 야간 시설
     * (OVERNIGHT_FACILITY_ID)은 22:00~다음 날 06:00이다. 시나리오마다 시설을 분리해 한 테스트가
     * 일정을 바꿔도 다른 테스트의 전제가 깨지지 않게 한다. 모든 매칭방에는 기존 신청 한 건을 미리
     * 넣어 거부 시 보존 여부를 확인한다.</p>
     */
    private void insertFixture() {
        jdbcTemplate.update("""
                INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?),
                       (?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?)
                """,
                APPLICANT_USER_ID, "facility-schedule-applicant@example.invalid", PASSWORD_HASH,
                "일정 동시성 신청자", "010-9800-0001", "일반 회원", CREATED_AT,
                EXISTING_APPLICANT_USER_ID, "facility-schedule-existing@example.invalid", PASSWORD_HASH,
                "일정 동시성 기존 신청자", "010-9800-0002", "일반 회원", CREATED_AT,
                MANAGER_USER_ID, "facility-schedule-manager@example.invalid", PASSWORD_HASH,
                "일정 동시성 시설 관리자", "010-9800-0003", "시설 관리자", CREATED_AT,
                FRESH_APPLICANT_USER_ID, "facility-schedule-fresh@example.invalid", PASSWORD_HASH,
                "일정 동시성 신규 신청자", "010-9800-0004", "일반 회원", CREATED_AT);

        jdbcTemplate.update("INSERT INTO region (region_id, parent_region_id, name) VALUES (?, NULL, ?)",
                REGION_ID, "일정 동시성 테스트 지역");
        jdbcTemplate.update("INSERT INTO sport (sport_id, name) VALUES (?, ?)",
                SPORT_ID, "일정 동시성 테스트 종목");

        for (long facilityId = FACILITY_ID_FIRST; facilityId <= FACILITY_ID_LAST; facilityId++) {
            jdbcTemplate.update("""
                    INSERT INTO facility (
                        facility_id, region_id, name, address, latitude, longitude,
                        concurrent_match_limit, status, deleted_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
                    """,
                    facilityId, REGION_ID, "일정 동시성 시설 " + facilityId, "일정 동시성 주소 " + facilityId,
                    new BigDecimal("37.5000000"), new BigDecimal("127.0000000"), 3, "운영 중");
            jdbcTemplate.update("""
                    INSERT INTO facility_manager (facility_id, user_id, assigned_at) VALUES (?, ?, ?)
                    """, facilityId, MANAGER_USER_ID, CREATED_AT);
            jdbcTemplate.update("""
                    INSERT INTO facility_sport (
                        facility_id, sport_id, default_min, default_max, default_fee, default_skill
                    ) VALUES (?, ?, 1, 10, 0.00, '무관')
                    """, facilityId, SPORT_ID);
        }

        insertOpenRules(OPEN_FACILITY_ID);
        insertOpenRules(CLOSURE_FACILITY_ID);
        insertOpenRules(RESERVATION_FACILITY_ID);
        insertOpenRules(STRADDLING_FACILITY_ID);
        insertOvernightRules(OVERNIGHT_FACILITY_ID);

        insertMatch(CONCURRENT_MATCH_ID, OPEN_FACILITY_ID, MATCH_START, MATCH_END);
        insertMatch(STRADDLING_MATCH_ID, STRADDLING_FACILITY_ID, STRADDLING_START, STRADDLING_END);
        insertMatch(UNCONFIGURED_MATCH_ID, UNCONFIGURED_FACILITY_ID, MATCH_START, MATCH_END);
        insertMatch(CLOSURE_MATCH_ID, CLOSURE_FACILITY_ID, MATCH_START, MATCH_END);
        insertMatch(APPLICATION_LOCK_MATCH_ID, RESERVATION_FACILITY_ID, MATCH_START, MATCH_END);
        insertMatch(CROSS_MIDNIGHT_MATCH_ID, OVERNIGHT_FACILITY_ID,
                CROSS_MIDNIGHT_START, CROSS_MIDNIGHT_END);

        for (long matchId = MATCH_ID_FIRST; matchId <= MATCH_ID_LAST; matchId++) {
            insertExistingApplication(matchId);
        }
    }

    /** 7요일 모두 09:00~18:00 운영. 날짜별 예외가 없으므로 어느 요일의 매칭방도 이 시간 안에 든다. */
    private void insertOpenRules(long facilityId) {
        for (int weekday = 1; weekday <= 7; weekday++) {
            jdbcTemplate.update("""
                    INSERT INTO operating_rule (
                        facility_id, weekday, open_time, close_time, end_next_day, is_open
                    ) VALUES (?, ?, '09:00:00', '18:00:00', FALSE, TRUE)
                    """, facilityId, weekday);
        }
    }

    /** 7요일 모두 22:00~다음 날 06:00 운영이다. 자정 분할 계산을 확인하는 데 쓴다. */
    private void insertOvernightRules(long facilityId) {
        for (int weekday = 1; weekday <= 7; weekday++) {
            jdbcTemplate.update("""
                    INSERT INTO operating_rule (
                        facility_id, weekday, open_time, close_time, end_next_day, is_open
                    ) VALUES (?, ?, '22:00:00', '06:00:00', TRUE, TRUE)
                    """, facilityId, weekday);
        }
    }

    private void insertMatch(long matchId, long facilityId, LocalDateTime startAt, LocalDateTime endAt) {
        jdbcTemplate.update("""
                INSERT INTO social_match (
                    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                    min_participants, max_participants, team_a_capacity, team_b_capacity,
                    fee_per_person, required_skill, status, assignment_method, rating_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                matchId, facilityId, SPORT_ID, MANAGER_USER_ID, "일정 동시성 매칭방 " + matchId,
                startAt, endAt, 1, 10, 5, 5, new BigDecimal("0.00"), "무관",
                MatchStatus.RECRUITING.dbValue(), "MANUAL", "WAITING");
    }

    private void insertExistingApplication(long matchId) {
        jdbcTemplate.update("""
                INSERT INTO match_application (
                    match_id, applicant_user_id, club_id, application_type, status, receipt_no, created_at
                ) VALUES (?, ?, NULL, ?, ?, ?, ?)
                """,
                matchId, EXISTING_APPLICANT_USER_ID, ApplicationType.INDIVIDUAL.dbValue(),
                ApplicationStatus.WAITING.dbValue(), "RCPT-98-" + matchId, CREATED_AT);
    }

    /** 휴관 한 건을 바로 커밋한다. 정리 단계가 이 시설의 schedule_change를 함께 지운다. */
    private void insertClosure(long facilityId, LocalDateTime startAt, LocalDateTime endAt) {
        jdbcTemplate.update("""
                INSERT INTO schedule_change (
                    facility_id, created_by, start_at, end_at, change_type, reason, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                facilityId, MANAGER_USER_ID, startAt, endAt,
                FacilityClosure.TYPE, "일정 동시성 테스트 휴관", FacilityClosure.STATUS_ACTIVE);
    }

    /**
     * 커밋된 최종 상태에 신규 신청 행이 없고 기존 신청만 남았는지 확인한다.
     *
     * <p>신청은 자체 트랜잭션에서 실패 시 롤백되므로, 이 단언은 "거부된 요청이 최종 데이터를
     * 바꾸지 않았다"는 관찰 가능한 계약을 고정한다.</p>
     */
    private void assertRejectedWithoutNewApplication(long matchId) {
        assertThat(applicationCount(matchId, APPLICANT_USER_ID))
                .as("거부된 신청은 match_application 행을 만들지 않아야 한다")
                .isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM match_application
                 WHERE match_id = ? AND applicant_user_id = ? AND status = ? AND cancelled_at IS NULL
                """, Integer.class, matchId, EXISTING_APPLICANT_USER_ID,
                ApplicationStatus.WAITING.dbValue()))
                .as("기존 신청은 유지되어야 한다")
                .isEqualTo(1);
    }

    /**
     * 신청이 시설 일정 사유로 거부됐는지 확인한다.
     *
     * <p>정원 초과·중복 신청 같은 다른 사유로 실패해도 테스트가 통과하는 것을 막기 위해, 시설 서비스가
     * 넘긴 안내 문구(모두 "시설"을 포함한다)가 그대로 노출되는지 함께 본다.</p>
     */
    private void assertRejectedByFacilitySchedule(long matchId, long userId) {
        assertThatThrownBy(() -> matchApplicationService.applyIndividual(matchId, userId))
                .isInstanceOf(MatchApplyException.class)
                .hasMessageContaining("시설");
    }

    /** 신청이 시설 행 잠금을 실제로 보유 중인지 NOWAIT로 확인한다. */
    private void assertFacilityRowLockedByAnotherTransaction() {
        assertThat(tryLockFacilityRowNowait())
                .as("신청 트랜잭션이 시설 행 잠금을 보유하고 있어 NOWAIT 잠금은 실패해야 한다")
                .isFalse();
    }

    /**
     * 같은 시설 행을 {@code FOR UPDATE NOWAIT}로 잠글 수 있는지 시도한다.
     *
     * <p>다른 연결이 잠금을 보유 중이면 MySQL이 3572(ER_LOCK_NOWAIT)로 즉시 실패하므로 대기 없이
     * 락 보유 여부를 결정적으로 확인할 수 있다. 잠금을 얻은 경우 그 트랜잭션은 즉시 끝나 잠금을
     * 반납한다.</p>
     *
     * <p>락 충돌이 아닌 다른 SQL 오류는 락 보유로 오인하지 않도록 그대로 다시 던진다.</p>
     */
    private boolean tryLockFacilityRowNowait() {
        try {
            Boolean locked = txTemplate().execute(status -> !jdbcTemplate.queryForList("""
                    SELECT facility_id FROM facility
                     WHERE facility_id = ? AND deleted_at IS NULL
                       FOR UPDATE NOWAIT
                    """, Long.class, RESERVATION_FACILITY_ID).isEmpty());
            return Boolean.TRUE.equals(locked);
        } catch (DataAccessException exception) {
            if (!isLockNotAvailable(exception)) {
                throw exception;
            }
            return false;
        }
    }

    /** MySQL ER_LOCK_NOWAIT(3572)로 인한 실패인지 원인 체인에서 확인한다. */
    private static boolean isLockNotAvailable(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlException.getErrorCode() == MYSQL_LOCK_NOWAIT) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private int openRuleCount(long facilityId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operating_rule WHERE facility_id = ? AND is_open = TRUE",
                Integer.class, facilityId);
        return count == null ? 0 : count;
    }

    private int ruleRowCount(long facilityId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operating_rule WHERE facility_id = ?", Integer.class, facilityId);
        return count == null ? 0 : count;
    }

    private int applicationCount(long matchId, long userId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM match_application WHERE match_id = ? AND applicant_user_id = ?
                """, Integer.class, matchId, userId);
        return count == null ? 0 : count;
    }

    private TransactionTemplate txTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    private Long currentConnectionId() {
        return jdbcTemplate.queryForObject("SELECT CONNECTION_ID()", Long.class);
    }
}
