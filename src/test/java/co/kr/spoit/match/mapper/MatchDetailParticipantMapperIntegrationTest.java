package co.kr.spoit.match.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
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

import co.kr.spoit.match.dto.ParticipantRow;

/**
 * 명시적으로 지정한 전용 MySQL DB에서만 매칭방 상세 참가자 조회를 검증한다.
 * 환경변수가 없으면 실행되지 않으며 모든 변경은 테스트 트랜잭션 롤백으로 되돌린다.
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/match/detail-participant-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class MatchDetailParticipantMapperIntegrationTest {

    private static final long MATCH_ID = 9961L;
    private static final List<String> OCCUPYING = List.of("대기", "확정");

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    @Autowired
    private MatchMapper matchMapper;

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
    @DisplayName("신청 시점 경기 수는 현재 반영 경기 수에서 참가 확정 뒤 같은 종목 이력 수를 뺀다")
    void entryRatedMatchCountSubtractsSameSportHistoryAfterJoin() {
        List<ParticipantRow> rows = matchMapper.findDetailParticipants(MATCH_ID, OCCUPYING);

        // 9961은 이력 없이 sport_rating만 있고, 9963은 sport_rating 행이 없다. 취소한 9964는 빠진다.
        assertThat(rows)
                .extracting(ParticipantRow::getUserId, ParticipantRow::getNickname, ParticipantRow::getMatchSide,
                        ParticipantRow::getEntryRatedMatchCount)
                .containsExactly(
                        tuple(9961L, "상세 참가자 9961", "A", 12),
                        tuple(9962L, "상세 참가자 9962", "B", 5),
                        tuple(9963L, "상세 참가자 9963", "B", 0));
        assertThat(rows.get(0).getEntryMmr()).isEqualByComparingTo("1150.00");
    }

    @Test
    @DisplayName("목록 카드가 쓰는 findParticipants는 그대로라 신청 시점 경기 수를 채우지 않는다")
    void listParticipantsQueryIsUnchanged() {
        List<ParticipantRow> rows = matchMapper.findParticipants(List.of(MATCH_ID), OCCUPYING);

        assertThat(rows).extracting(ParticipantRow::getUserId).containsExactly(9961L, 9962L, 9963L);
        assertThat(rows).extracting(ParticipantRow::getEntryRatedMatchCount).containsOnlyNulls();
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
