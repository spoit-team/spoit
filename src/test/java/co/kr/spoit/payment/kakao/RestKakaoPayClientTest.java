package co.kr.spoit.payment.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.kr.spoit.payment.config.KakaoPayProperties;

/** 카카오페이 응답·오류 해석 */
class RestKakaoPayClientTest {

    @Test
    @DisplayName("카카오페이 시각은 한국 시간 문자열이며 밀리초가 붙어도 읽는다")
    void parsesKoreanLocalTime() {
        assertThat(RestKakaoPayClient.parseTime("2026-11-10T14:03:00"))
                .isEqualTo(LocalDateTime.of(2026, 11, 10, 14, 3));
        assertThat(RestKakaoPayClient.parseTime("2026-11-10T14:03:00.123")).isEqualTo(LocalDateTime.of(2026, 11, 10, 14, 3));
        assertThat(RestKakaoPayClient.parseTime(null)).isNull();
        assertThat(RestKakaoPayClient.parseTime("bad")).isNull();
    }

    @Test
    @DisplayName("오류 응답의 error_code·error_message를 예외로 옮긴다")
    void mapsErrorBody() {
        RestKakaoPayClient client = new RestKakaoPayClient(new KakaoPayProperties(), new ObjectMapper());
        HttpClientErrorException error = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                new HttpHeaders(), "{\"error_code\":-702,\"error_message\":\"invalid pg_token\"}"
                        .getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        KakaoPayException mapped = client.toException(error);

        assertThat(mapped.getCode()).isEqualTo("-702");
        assertThat(mapped.getMessage()).isEqualTo("invalid pg_token");
    }

    @Test
    @DisplayName("비밀 키가 없으면 호출하지 않고 NOT_CONFIGURED로 실패한다")
    void requiresSecretKey() {
        RestKakaoPayClient client = new RestKakaoPayClient(new KakaoPayProperties(), new ObjectMapper());

        assertThatThrownBy(() -> client.order("T1"))
                .isInstanceOf(KakaoPayException.class)
                .extracting("code").isEqualTo("NOT_CONFIGURED");
    }
}
