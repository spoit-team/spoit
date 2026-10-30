package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import co.kr.spoit.support.MySqlTestDatabase;

class MySqlTestConnectionValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://localhost:3306/spoit_mat_test",
            "jdbc:mysql://127.0.0.1:3307/spoit_mat_test",
            "jdbc:mysql://127.0.0.1:1/spoit_mat_test",
            "jdbc:mysql://localhost:65535/spoit_mat_test"
    })
    void allowsOnlyExplicitLocalTestDatabaseUrls(String url) {
        assertThat(MySqlTestDatabase.validateUrl(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "jdbc:mysql://localhost:3306/spoit",
            "jdbc:mysql://localhost:3306/spoit_mat_test_extra",
            "jdbc:mysql://localhost/spoit_mat_test",
            "jdbc:mysql://localhost:0/spoit_mat_test",
            "jdbc:mysql://localhost:65536/spoit_mat_test",
            "jdbc:mysql://localhost:9999999999/spoit_mat_test",
            "jdbc:mysql://localhost:-1/spoit_mat_test",
            "jdbc:mysql://localhost:3306/spoit_mat_test?useSSL=false",
            "jdbc:mysql://localhost:3306/spoit_mat_test#fragment",
            "jdbc:mysql://localhost:3306/spoit_mat_test/",
            "jdbc:mysql://localhost:3306/spoit_mat_test\n",
            "jdbc:mysql://localhost:3306/spoit_mat_test ",
            "jdbc:mysql://localhost:3306,127.0.0.1:3307/spoit_mat_test",
            "jdbc:mysql://127.0.0.2:3306/spoit_mat_test",
            "jdbc:mysql://database.example:3306/spoit_mat_test",
            "jdbc:mysql://user:password@localhost:3306/spoit_mat_test",
            "jdbc:mysql:loadbalance://localhost:3306/spoit_mat_test",
            "jdbc:h2:mem:spoit_mat_test"
    })
    void rejectsOtherTargetsWithoutEchoingTheSuppliedValue(String url) {
        var error = assertThatIllegalArgumentException()
                .isThrownBy(() -> MySqlTestDatabase.validateUrl(url));
        if (url != null && !url.isEmpty()) {
            error.withMessageNotContaining(url);
        }
    }
}