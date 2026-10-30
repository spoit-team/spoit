package co.kr.spoit.mat.mapper;

import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.transaction.BeforeTransaction;

import co.kr.spoit.support.MySqlTestDatabase;

/** 명시적으로 지정한 전용 MySQL DB에서만 매퍼와 롤백을 검증한다. */
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never",
        "mybatis.configuration.map-underscore-to-camel-case=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/mat/mapper-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
abstract class AbstractMySqlMapperTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.registerDataSource(registry);
    }

    @BeforeTransaction
    void verifyDatabaseBeforeFixture() {
        MySqlTestDatabase.assertDedicatedDatabase(jdbcTemplate);
    }
}
