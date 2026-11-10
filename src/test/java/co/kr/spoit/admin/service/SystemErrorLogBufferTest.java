package co.kr.spoit.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * 메모리 오류 버퍼가 수집 범위·보관 상한·민감값 마스킹·길이 제한을 지키는지 검증한다.
 *
 * <p>실제 logback 루트 로거에 붙여 {@code ERROR}·{@code WARN}만 잡고 {@code INFO}·{@code DEBUG}는
 * 버리며, {@value SystemErrorLogBuffer#CAPACITY}건을 넘으면 가장 오래된 기록을 버린다.</p>
 */
class SystemErrorLogBufferTest {

    private final SystemErrorLogBuffer buffer = new SystemErrorLogBuffer();
    private final Logger logger = (Logger) LoggerFactory.getLogger("co.kr.spoit.match.AdminBufferTest");

    @BeforeEach
    void attach() {
        buffer.attach();
        buffer.clear();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(true);
    }

    @AfterEach
    void detach() {
        buffer.clear();
        buffer.detach();
    }

    @Test
    @DisplayName("ERROR·WARN만 수집하고 INFO·DEBUG는 버린다")
    void collectsOnlyWarnAndError() {
        logger.info("무시되는 정보 로그");
        logger.debug("무시되는 디버그 로그");
        logger.warn("수집되는 경고");
        logger.error("수집되는 오류");

        List<String> levels = buffer.recent().stream().map(r -> r.level()).toList();

        assertThat(levels).containsExactlyInAnyOrder("WARN", "ERROR");
        assertThat(buffer.recent()).extracting(r -> r.summary())
                .containsExactlyInAnyOrder("수집되는 경고", "수집되는 오류");
    }

    @Test
    @DisplayName("보관 상한을 넘으면 가장 오래된 기록부터 버린다")
    void keepsAtMostCapacity() {
        for (int i = 0; i < SystemErrorLogBuffer.CAPACITY + 5; i++) {
            logger.error("오류 " + i);
        }

        List<String> summaries = buffer.recent().stream().map(r -> r.summary()).toList();

        assertThat(summaries).hasSize(SystemErrorLogBuffer.CAPACITY);
        // 최신이 앞에 오고, 가장 오래된 5건은 사라진다.
        assertThat(summaries.get(0)).isEqualTo("오류 " + (SystemErrorLogBuffer.CAPACITY + 4));
        assertThat(summaries).doesNotContain("오류 0", "오류 4");
    }

    @Test
    @DisplayName("재시작(clear)하면 버퍼가 빈다")
    void clearEmptiesBuffer() {
        logger.error("오류");
        assertThat(buffer.recent()).hasSize(1);

        buffer.clear();

        assertThat(buffer.recent()).isEmpty();
    }

    @Test
    @DisplayName("이메일·전화번호·비밀번호·토큰 값은 마스킹한다")
    void masksSensitiveValues() {
        logger.error("로그인 실패 user@example.com 010-1234-5678 password=hunter2 token: abc.def Bearer eyJhbGciOi");

        String summary = buffer.recent().get(0).summary();

        assertThat(summary).doesNotContain("user@example.com")
                .doesNotContain("010-1234-5678")
                .doesNotContain("hunter2")
                .doesNotContain("abc.def")
                .doesNotContain("eyJhbGciOi")
                .contains("***");
    }

    @Test
    @DisplayName("요약은 최대 길이로 잘리고 예외 상세는 원인 사슬을 포함한다")
    void truncatesAndCapturesThrowable() {
        logger.error("가".repeat(SystemErrorLogBuffer.SUMMARY_MAX + 50));
        logger.error("실패 지점", new IllegalStateException("원인"));

        List<co.kr.spoit.admin.dto.SystemErrorRecord> records = buffer.recent();
        String longSummary = records.get(1).summary();
        var withThrowable = records.get(0);

        assertThat(longSummary.length()).isLessThanOrEqualTo(SystemErrorLogBuffer.SUMMARY_MAX);
        assertThat(longSummary).endsWith("…");
        assertThat(withThrowable.code()).isEqualTo("java.lang.IllegalStateException");
        assertThat(withThrowable.detail()).contains("IllegalStateException").contains("원인");
    }

    @Test
    @DisplayName("로거 이름으로 도메인 서비스 이름을 만든다")
    void derivesServiceName() {
        assertThat(SystemErrorLogBuffer.serviceOf("co.kr.spoit.match.RoomCommandService")).isEqualTo("match");
        assertThat(SystemErrorLogBuffer.serviceOf("org.springframework.web.Foo")).isEqualTo("app");
        assertThat(SystemErrorLogBuffer.serviceOf(null)).isEqualTo("app");
    }
}
