package co.kr.spoit.match.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** 매칭방 무한 스크롤 커서의 인코딩 규칙을 검증한다. */
class MatchCursorTest {

    @Test
    @DisplayName("인코딩한 커서를 그대로 되돌린다 (초가 0이어도)")
    void roundTrip() {
        MatchCursor cursor = new MatchCursor(LocalDateTime.of(2026, 11, 12, 18, 0), 123L);

        assertThat(MatchCursor.decode(cursor.encode())).contains(cursor);
    }

    @Test
    @DisplayName("URL에 그대로 넣을 수 있는 문자만 쓴다")
    void urlSafe() {
        String encoded = new MatchCursor(LocalDateTime.of(2026, 11, 12, 18, 30, 15), 9_999_999L).encode();

        assertThat(encoded).matches("[A-Za-z0-9_-]+");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "%%%", "not-a-cursor"})
    @DisplayName("형식이 틀린 커서는 empty (첫 페이지로 처리)")
    void invalidIsEmpty(String value) {
        assertThat(MatchCursor.decode(value)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-40T99:00_5", "2026-11-12T18:00_abc", "2026-11-12T18:00", "_5"})
    @DisplayName("디코딩은 되지만 내용이 틀린 커서도 empty")
    void invalidContent(String raw) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        assertThat(MatchCursor.decode(encoded)).isEmpty();
    }
}
