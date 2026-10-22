package co.kr.spoit.member.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** remember-me 키에 소스 기본값이 남아 있지 않은지 확인한다. */
@SpringBootTest
class RememberMeKeyTest {

    @Autowired
    private String rememberMeKey;

    @Test
    @DisplayName("키가 주입되지 않으면 하드코딩된 기본값 대신 임의 키를 만든다")
    void doesNotFallBackToHardcodedKey() {
        assertThat(rememberMeKey)
                .isNotBlank()
                .isNotEqualTo("spoit-remember-me-dev-key");
    }
}
