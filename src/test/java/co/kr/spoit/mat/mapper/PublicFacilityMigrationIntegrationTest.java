package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

/** 공공 체육시설 등록 마이그레이션을 실제 MySQL에서 실행해 넣는 행 수와 새 계정을 확인한다. */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
// 공용 fixture 행은 쓰지 않아 전용 fixture만 지정한다. 여기 @Sql이 상위 클래스의 @Sql을 대신한다.
@Sql(scripts = "/mat/public-facility-migration-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class PublicFacilityMigrationIntegrationTest extends AbstractMySqlMapperTest {

    private static final Path MIGRATION = Path.of("db/migrations/20261110-seoul-public-facilities.sql");
    private static final List<String> TABLES =
            List.of("region", "app_user", "facility", "facility_sport", "operating_rule", "facility_manager");
    private static final RowMapper<Account> ACCOUNT =
            (rs, rowNum) -> new Account(rs.getString("password_hash"), rs.getString("role"));

    @Test
    void secondRunAddsNoRows() {
        migrate();
        Map<String, Long> afterFirstRun = rowCounts();

        migrate();

        assertThat(rowCounts()).isEqualTo(afterFirstRun);
    }

    @Test
    void addsExpectedRowsToEachTable() {
        Map<String, Long> before = rowCounts();

        migrate();

        Map<String, Long> after = rowCounts();
        Map<String, Long> added = new LinkedHashMap<>();
        TABLES.forEach(table -> added.put(table, after.get(table) - before.get(table)));
        // 구 14개, 계정 10개, 시설 113곳과 그 종목 118행, 7일치 운영 시간, 시설마다 담당자 1명
        assertThat(added).isEqualTo(Map.of(
                "region", 14L, "app_user", 10L, "facility", 113L,
                "facility_sport", 118L, "operating_rule", 791L, "facility_manager", 113L));
    }

    @Test
    void newAccountsCopyHashAndRoleOfFacilityDemoAccount() {
        migrate();

        Account demo = jdbcTemplate.queryForObject(
                "SELECT password_hash, role FROM app_user WHERE email = 'facility@spoit.test'", ACCOUNT);
        List<Account> created = jdbcTemplate.query(
                "SELECT password_hash, role FROM app_user WHERE email LIKE 'facility-__@spoit.test'", ACCOUNT);

        assertThat(created).hasSize(10).containsOnly(demo);
    }

    private void migrate() {
        assertThat(MIGRATION).as("Gradle 테스트는 저장소 루트에서 실행된다").exists();
        byte[] script = withoutTransactionControl(read(MIGRATION)).getBytes(StandardCharsets.UTF_8);
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ByteArrayResource(script), StandardCharsets.UTF_8));
            return null;
        });
    }

    // 실제 적용은 한 트랜잭션으로 묶지만, 테스트에서는 롤백되는 테스트 트랜잭션 안에서 실행해야 한다.
    private static String withoutTransactionControl(String script) {
        String stripped = script.replaceAll("(?m)^(START TRANSACTION|COMMIT);\\r?$", "");
        assertThat(stripped).as("COMMIT이 남으면 테스트 데이터가 전용 DB에 확정된다")
                .doesNotContainPattern("(?im)^\\s*(START TRANSACTION|COMMIT|ROLLBACK)\\b");
        return stripped;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Long> rowCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : TABLES) {
            counts.put(table, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
        return counts;
    }

    private record Account(String passwordHash, String role) {
    }
}
