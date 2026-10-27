package co.kr.spoit.facility.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.transaction.BeforeTransaction;

import co.kr.spoit.facility.domain.FacilitySummary;

/**
 * 명시적으로 지정한 전용 MySQL DB에서만 담당 시설 조회 SQL을 검증한다.
 * 환경변수가 없으면 실행되지 않으며 모든 변경은 테스트 트랜잭션 롤백으로 되돌린다.
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/facility/mapper-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class FacilityMapperIntegrationTest {

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    @Autowired
    private FacilityMapper facilityMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @BeforeTransaction
    void verifyDatabaseBeforeFixture() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"spoit_mat_test".equals(database)) {
            throw new IllegalStateException("전용 테스트 DB가 아니므로 fixture 실행을 중단합니다.");
        }
    }

    @Test
    @DisplayName("담당자 ID로 삭제되지 않은 시설만 조회하고 다중 종목은 한 행으로 집계한다")
    void selectsOnlyManagedActiveFacilitiesWithoutDuplicates() {
        var facilities = facilityMapper.selectManagedFacilities(9001L);

        assertThat(facilities)
                .extracting(FacilitySummary::getFacilityId, FacilitySummary::getName,
                        FacilitySummary::getConcurrentMatchLimit, FacilitySummary::getStatus)
                .containsExactly(
                        tuple(9101L, "A 관리 시설 1", 3, "운영 중"),
                        tuple(9102L, "A 관리 시설 2", 1, "특별 운영"));
        assertThat(facilities.get(0).getSportsLabel()).isEqualTo("시설 테스트 농구, 시설 테스트 축구");
        assertThat(facilities.get(1).getSportsLabel()).isNull();
    }

    @Test
    @DisplayName("다른 담당 관리자의 시설은 조회되지 않는다")
    void doesNotSelectFacilitiesManagedByAnotherUser() {
        var facilities = facilityMapper.selectManagedFacilities(9002L);

        assertThat(facilities)
                .extracting(FacilitySummary::getFacilityId, FacilitySummary::getName)
                .containsExactly(tuple(9103L, "B 관리 시설"));
    }

    @Test
    @DisplayName("담당 시설이 없는 회원은 빈 목록을 받는다")
    void returnsEmptyListWhenUserHasNoFacility() {
        assertThat(facilityMapper.selectManagedFacilities(9999L)).isEmpty();
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
