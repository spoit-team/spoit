package co.kr.spoit.support;

import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * 전용 MySQL 통합 테스트가 공통으로 지켜야 하는 DB 안전장치를 모은 헬퍼다.
 *
 * <p>테스트 클래스는 {@code @DynamicPropertySource}·{@code @Sql}·{@code @BeforeTransaction} 같은
 * 테스트 컨텍스트 애너테이션을 각자 유지하고, 값 검증과 데이터소스 속성 등록만 여기에 위임한다.
 * 그래야 어느 테스트가 어떤 fixture를 언제 실행하는지는 각 테스트 파일에서 그대로 읽힌다.</p>
 *
 * <p>여기서 강제하는 규칙은 세 가지다. (1) 접속 URL은 localhost 또는 127.0.0.1의 명시적 포트와
 * {@code spoit_mat_test} DB만 허용한다. (2) 필수 환경변수가 없으면 개발 DB 설정으로 대체하지 않고
 * 즉시 실패한다. (3) fixture를 쓰기 전에 현재 세션의 DB가 전용 테스트 DB인지 다시 확인한다.</p>
 */
public final class MySqlTestDatabase {

    /** 통합 테스트가 사용해야 하는 전용 DB 이름이다. */
    private static final String DATABASE_NAME = "spoit_mat_test";

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    private MySqlTestDatabase() {
    }

    /** 검증된 테스트 DB URL·계정·드라이버를 테스트 컨텍스트 속성으로 등록한다. */
    public static void registerDataSource(DynamicPropertyRegistry registry) {
        String url = validateUrl(requiredEnvironmentVariable("MAT_TEST_DB_URL"));
        String username = requiredEnvironmentVariable("MAT_TEST_DB_USERNAME");
        String password = requiredEnvironmentVariable("MAT_TEST_DB_PASSWORD");

        registry.add("spring.datasource.url", () -> url + JDBC_OPTIONS);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    /**
     * localhost 또는 127.0.0.1의 명시적 포트와 {@code spoit_mat_test} DB만 통과시킨다.
     *
     * <p>거부 메시지에는 입력값을 담지 않는다. URL에 자격 증명이 섞여 들어와도 로그로 새지 않게 한다.</p>
     */
    public static String validateUrl(String url) {
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

    /** fixture 실행 직전에 현재 세션이 전용 테스트 DB를 보고 있는지 확인한다. */
    public static void assertDedicatedDatabase(JdbcTemplate jdbcTemplate) {
        requireDatabase(jdbcTemplate, "fixture 실행을 중단합니다.");
    }

    /** 테스트가 커밋하는 fixture를 만들기 직전에 현재 세션이 전용 테스트 DB인지 확인한다. */
    public static void assertDedicatedDatabaseBeforeFixtureCreation(JdbcTemplate jdbcTemplate) {
        requireDatabase(jdbcTemplate, "fixture 생성을 중단합니다.");
    }

    private static void requireDatabase(JdbcTemplate jdbcTemplate, String abortMessage) {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!DATABASE_NAME.equals(database)) {
            throw new IllegalStateException("전용 테스트 DB가 아니므로 " + abortMessage);
        }
    }

    /**
     * stale snapshot 회귀 시나리오가 성립하려면 MySQL 기본 격리 수준이어야 한다.
     *
     * <p>격리 수준을 테스트가 바꾸지 않고 전제만 확인한다. 다르면 시나리오 자체가 의미를 잃으므로
     * 조용히 통과시키지 않고 실패한다.</p>
     */
    public static void assertRepeatableReadIsolation(JdbcTemplate jdbcTemplate) {
        String isolation = jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class);
        if (!"REPEATABLE-READ".equals(isolation)) {
            throw new IllegalStateException("동시성 회귀 테스트는 REPEATABLE READ 격리를 전제로 합니다: " + isolation);
        }
    }
}
