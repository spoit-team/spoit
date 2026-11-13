package co.kr.spoit.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 실제 애플리케이션 매퍼 검색 설정에 재설정 XML이 포함되는지 확인한다. */
@SpringBootTest
class PasswordResetMapperXmlBindingTest {

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    void everyResetStatementAndMemberPasswordUpdateAreBound() {
        var configuration = sqlSessionFactory.getConfiguration();
        assertThat(configuration.hasMapper(PasswordResetMapper.class)).isTrue();
        for (var method : PasswordResetMapper.class.getDeclaredMethods()) {
            assertThat(configuration.hasStatement(PasswordResetMapper.class.getName() + "." + method.getName()))
                    .as(method.getName()).isTrue();
        }
        assertThat(configuration.hasStatement(MemberMapper.class.getName() + ".updatePasswordHash")).isTrue();
    }

    @Test
    void migrationAndFreshSchemaCreateEquivalentResetTables() throws IOException {
        String migration = Files.readString(Path.of("db/migrations/20261112-password-reset.sql"),
                StandardCharsets.UTF_8);
        String schema = Files.readString(Path.of("db/schema.sql"), StandardCharsets.UTF_8);
        for (String table : new String[] {"password_reset_challenge", "password_reset_rate_limit"}) {
            assertThat(normalizedCreateStatement(migration, table))
                    .as("기존 DB 마이그레이션과 새 DB DDL: %s", table)
                    .isEqualTo(normalizedCreateStatement(schema, table));
        }
    }

    private static String normalizedCreateStatement(String sql, String table) {
        int start = sql.indexOf("CREATE TABLE " + table + " (");
        assertThat(start).as("CREATE TABLE %s", table).isGreaterThanOrEqualTo(0);
        int engine = sql.indexOf(") ENGINE=InnoDB", start);
        assertThat(engine).as("테이블 %s의 ENGINE 절", table).isGreaterThan(start);
        int end = sql.indexOf(';', engine);
        assertThat(end).isGreaterThan(start);
        return sql.substring(start, end)
                .replaceAll("(?i)COMMENT\\s*=?\\s*'[^']*'", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
