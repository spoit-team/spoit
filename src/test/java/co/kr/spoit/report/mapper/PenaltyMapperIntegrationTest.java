package co.kr.spoit.report.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 조기 해제의 "남은 이용 제한" 재조회가 MySQL REPEATABLE READ에서도 최신 커밋을 보는지 검증한다.
 *
 * <p>단위 목과 XML 문자열 검사는 SQL 모양만 고정한다. 이 테스트는 실제 MySQL에서 두 트랜잭션을
 * 다음 순서로 엮어 <strong>스냅샷 읽기와 잠금 읽기의 차이</strong>를 확인한다.</p>
 *
 * <ol>
 *   <li>해제 측 트랜잭션(A, REPEATABLE READ)이 먼저 일반 조회로 스냅샷을 확립한다.</li>
 *   <li>A가 열린 동안 다른 커넥션(B, 자동 커밋)이 새 '적용 중' 이용 제한을 삽입·커밋한다.</li>
 *   <li>A의 일반 스냅샷 조회는 B의 행을 보지 못하고(이전 {@code MAX(end_at)} 구현이면 남은 제재를
 *       놓쳐 정지가 풀린다), 같은 A 트랜잭션의 {@code findLatestActiveRestrictionEndAtForUpdate}
 *       잠금 읽기(현재 읽기)는 B의 종료 시각을 본다.</li>
 * </ol>
 *
 * <p>{@code MAT_TEST_DB_URL} 등 환경변수가 없으면 실행되지 않으며, DB 이름이 {@code spoit_mat_test}가
 * 아니면 fixture 실행을 거부한다. 재현에는 A가 열린 동안 B의 커밋이 필요하므로 테스트 트랜잭션
 * 롤백에 기대지 않고({@link Propagation#NOT_SUPPORTED}), fixture도 별도 자동 커밋 커넥션으로 만들고
 * 정리해 두 커넥션이 서로의 커밋을 볼 수 있게 한다.</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PenaltyMapperIntegrationTest {

    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    /** 제재 대상 회원(고유 fixture). */
    private static final long TARGET_USER = 992001L;
    /** 제재를 부여하는 관리자(고유 fixture). */
    private static final long ISSUER = 992002L;

    /** 조기 해제 대상이 되는 기존 '적용 중' 이용 제한. */
    private static final long RELEASED_PENALTY = 992001L;
    /** 다른 트랜잭션이 커밋하는 새 '적용 중' 이용 제한. */
    private static final long NEW_PENALTY = 992002L;

    private static final String PASSWORD_HASH = "TEST_ONLY_NOT_A_PASSWORD_HASH";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Autowired
    private PenaltyMapper penaltyMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        String url = ReportMapperIntegrationTest.validateUrl(requiredEnvironmentVariable("MAT_TEST_DB_URL"));
        String username = requiredEnvironmentVariable("MAT_TEST_DB_USERNAME");
        String password = requiredEnvironmentVariable("MAT_TEST_DB_PASSWORD");

        registry.add("spring.datasource.url", () -> url + JDBC_OPTIONS);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeEach
    void insertFixture() {
        assertIsolatedTestDatabase();
        deleteFixture();
        // fixture는 자동 커밋 커넥션으로 넣어, 이 테스트의 해제 측 트랜잭션이 볼 수 있게 한다.
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (PreparedStatement users = connection.prepareStatement("""
                    INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)
                    VALUES
                        (?, 'penalty-target@example.invalid', ?, '제재 대상자', '010-9920-0001', '일반 회원', ?),
                        (?, 'penalty-issuer@example.invalid', ?, '제재 관리자', '010-9920-0002', '시스템 관리자', ?)
                    """)) {
                users.setLong(1, TARGET_USER);
                users.setString(2, PASSWORD_HASH);
                users.setObject(3, CREATED_AT);
                users.setLong(4, ISSUER);
                users.setString(5, PASSWORD_HASH);
                users.setObject(6, CREATED_AT);
                assertThat(users.executeUpdate()).isEqualTo(2);
            }
            // 조기 해제 대상: 지금부터 3일간 적용 중인 이용 제한.
            try (PreparedStatement penalty = connection.prepareStatement("""
                    INSERT INTO penalty (
                        penalty_id, target_user_id, report_id, issued_by, penalty_type,
                        start_at, end_at, status, reason, released_by, released_at
                    ) VALUES (
                        ?, ?, NULL, ?, '이용 제한',
                        CURRENT_TIMESTAMP, TIMESTAMPADD(DAY, 3, CURRENT_TIMESTAMP),
                        '적용 중', '기존 이용 제한', NULL, NULL
                    )
                    """)) {
                penalty.setLong(1, RELEASED_PENALTY);
                penalty.setLong(2, TARGET_USER);
                penalty.setLong(3, ISSUER);
                assertThat(penalty.executeUpdate()).isEqualTo(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("제재 통합 테스트 fixture 저장에 실패했습니다.", exception);
        }
    }

    @AfterEach
    void cleanUpFixture() {
        deleteFixture();
    }

    @Test
    @DisplayName("해제 측 스냅샷이 오래되어도 잠금 읽기는 다른 트랜잭션이 커밋한 새 이용 제한을 본다")
    void currentReadSeesNewlyCommittedRestrictionWhileSnapshotReadDoesNot() {
        AtomicReference<LocalDateTime> committedEndAt = new AtomicReference<>();

        TransactionTemplate releaseSide = new TransactionTemplate(transactionManager);
        // 서버 기본값에 의존하지 않고 문제의 격리 수준과 독립 트랜잭션을 명시한다.
        releaseSide.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        releaseSide.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        LocalDateTime remaining = releaseSide.execute(status -> {
            // 이 트랜잭션이 실제로 REPEATABLE READ로 열렸는지 확인한다(테스트 전제 고정).
            assertThat(jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class))
                    .isEqualTo("REPEATABLE-READ");

            // 1) 스냅샷 확립: 이 일반 조회가 해제 측 REPEATABLE READ 스냅샷을 만든다.
            assertThat(penaltyMapper.findPenalty(RELEASED_PENALTY)).isNotNull();

            // 2) A가 열린 동안 다른 커넥션이 새 '적용 중' 이용 제한을 커밋한다.
            committedEndAt.set(commitNewRestrictionFromOtherConnection());
            assertThat(committedEndAt.get()).isNotNull();

            // 3) 같은 A 트랜잭션의 일반 스냅샷 조회는 방금 커밋된 행을 보지 못한다.
            //    이전 MAX(end_at) 구현이었다면 남은 제재를 놓쳐 정지가 풀렸을 지점이다.
            assertThat(penaltyMapper.findPenalty(NEW_PENALTY)).isNull();

            // 4) 조기 해제(운영 경로와 같은 조건부 갱신) 뒤 남은 제한을 현재 읽기로 다시 조회한다.
            assertThat(penaltyMapper.releasePenalty(RELEASED_PENALTY, ISSUER, LocalDateTime.now()))
                    .isEqualTo(1);
            return penaltyMapper.findLatestActiveRestrictionEndAtForUpdate(TARGET_USER,
                    LocalDateTime.now());
        });

        // 5) 스냅샷이 아니라 현재 읽기이므로 새로 커밋된 제한의 종료 시각이 그대로 보인다.
        assertThat(remaining).isNotNull().isEqualTo(committedEndAt.get());
    }

    /**
     * 해제 측 트랜잭션과 별개의 커넥션(자동 커밋)으로 새 '적용 중' 이용 제한을 삽입·커밋하고
     * 저장된 종료 시각을 그대로 돌려준다. 시각 비교는 DATETIME 값을 그대로 읽어 시간대 변환에
     * 의존하지 않는다.
     */
    private LocalDateTime commitNewRestrictionFromOtherConnection() {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO penalty (
                        penalty_id, target_user_id, report_id, issued_by, penalty_type,
                        start_at, end_at, status, reason, released_by, released_at
                    ) VALUES (
                        ?, ?, NULL, ?, '이용 제한',
                        CURRENT_TIMESTAMP, TIMESTAMPADD(DAY, 10, CURRENT_TIMESTAMP),
                        '적용 중', '새 이용 제한', NULL, NULL
                    )
                    """)) {
                insert.setLong(1, NEW_PENALTY);
                insert.setLong(2, TARGET_USER);
                insert.setLong(3, ISSUER);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT end_at FROM penalty WHERE penalty_id = ?")) {
                select.setLong(1, NEW_PENALTY);
                try (ResultSet result = select.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    return result.getObject(1, LocalDateTime.class);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("다른 커넥션의 이용 제한 커밋에 실패했습니다.", exception);
        }
    }

    private void assertIsolatedTestDatabase() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"spoit_mat_test".equals(database)) {
            throw new IllegalStateException("전용 테스트 DB가 아니므로 fixture 실행을 중단합니다.");
        }
    }

    private void deleteFixture() {
        jdbcTemplate.update("DELETE FROM penalty WHERE penalty_id IN (?, ?)",
                RELEASED_PENALTY, NEW_PENALTY);
        jdbcTemplate.update("DELETE FROM app_user WHERE user_id IN (?, ?)", TARGET_USER, ISSUER);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다. 개발 DB 설정으로 대체하지 않습니다.");
        }
        return value;
    }
}
