package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;

/** 평가 항목 정리 마이그레이션을 실제 MySQL에서 두 번 실행해도 같은 결과인지 확인한다. */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
// 공용 fixture 행은 쓰지 않아 전용 fixture만 지정한다. 여기 @Sql이 상위 클래스의 @Sql을 대신한다.
@Sql(scripts = "/mat/evaluation-item-migration-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class EvaluationItemMigrationIntegrationTest extends AbstractMySqlMapperTest {

    private static final Path MIGRATION = Path.of("db/migrations/20261109-evaluation-items.sql");
    private static final BigDecimal ONE = new BigDecimal("1.00");

    @Test
    void secondRunChangesNothing() {
        migrate();
        List<ItemRow> afterFirstRun = items();

        migrate();

        assertThat(items()).isEqualTo(afterFirstRun);
        assertThat(items()).filteredOn(item -> "JOKGU_TOSS".equals(item.code())).hasSize(1);
    }

    @Test
    void leavesThreeEquallyWeightedItemsPerSportInAttackLinkDefenseOrder() {
        migrate();
        migrate();

        assertThat(items()).filteredOn(ItemRow::active)
                .extracting(ItemRow::sportId, ItemRow::name, ItemRow::weight, ItemRow::displayOrder)
                .containsExactly(
                        tuple(931L, "서브", ONE, 1), tuple(931L, "랠리", ONE, 2), tuple(931L, "포지셔닝", ONE, 3),
                        tuple(932L, "개인 기술", ONE, 1), tuple(932L, "패스", ONE, 2), tuple(932L, "수비 위치", ONE, 3),
                        tuple(933L, "발기술", ONE, 1), tuple(933L, "토스·연결", ONE, 2), tuple(933L, "수비", ONE, 3),
                        tuple(934L, "슈팅", ONE, 1), tuple(934L, "패스", ONE, 2), tuple(934L, "수비·리바운드", ONE, 3),
                        tuple(935L, "스매시", ONE, 1), tuple(935L, "풋워크", ONE, 2), tuple(935L, "서브", ONE, 3),
                        tuple(936L, "기타 테스트 항목", new BigDecimal("0.50"), 1));
    }

    @Test
    void turnsReplacedItemsOffWithoutDeletingOrRenamingThem() {
        migrate();

        // 저장된 평가 점수가 참조하는 행이라 지우지 않는다. 이름도 그대로여야 과거 점수의 뜻이 남는다.
        assertThat(items()).filteredOn(item -> !item.active())
                .extracting(ItemRow::itemId, ItemRow::name)
                .containsExactly(tuple(932L, "체력"), tuple(933L, "협동심"), tuple(934L, "볼 터치"),
                        tuple(947L, "협동심"));
        assertThat(items()).hasSize(20);
    }

    private void migrate() {
        assertThat(MIGRATION).as("Gradle 테스트는 저장소 루트에서 실행된다").exists();
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new FileSystemResource(MIGRATION), StandardCharsets.UTF_8));
            return null;
        });
    }

    private List<ItemRow> items() {
        return jdbcTemplate.query("""
                SELECT item_id, sport_id, item_code, item_name, weight, display_order, is_active
                FROM sport_evaluation_item
                WHERE sport_id BETWEEN 931 AND 936
                ORDER BY sport_id, is_active DESC, display_order, item_id
                """,
                (rs, rowNum) -> new ItemRow(rs.getLong("item_id"), rs.getLong("sport_id"), rs.getString("item_code"),
                        rs.getString("item_name"), rs.getBigDecimal("weight"), rs.getInt("display_order"),
                        rs.getBoolean("is_active")));
    }

    private record ItemRow(long itemId, long sportId, String code, String name, BigDecimal weight,
                           int displayOrder, boolean active) {
    }
}
