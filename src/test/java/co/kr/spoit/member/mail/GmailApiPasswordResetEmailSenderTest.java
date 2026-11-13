package co.kr.spoit.member.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GmailApiPasswordResetEmailSenderTest {

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String SEND_ENDPOINT =
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("설정 누락 시 발신자는 존재하지만 비활성 상태다")
    void missingConfigurationKeepsSenderAvailableButUnconfigured() {
        TestClient testClient = testClient(new GmailMailProperties());

        assertThat(testClient.sender().isConfigured()).isFalse();
        assertThatThrownBy(() -> testClient.sender().sendCode("member@example.com", "123456", 10))
                .isInstanceOf(PasswordResetEmailException.class)
                .extracting("code").isEqualTo("NOT_CONFIGURED");
        testClient.server().verify();
    }

    @Test
    @DisplayName("users/me 경로로 보내고 MIME From은 설정된 발신자를 유지한다")
    void sendsUtf8MimeMessageAndCachesAccessToken() throws Exception {
        TestClient testClient = testClient(configuredProperties());
        testClient.server().expect(once(), requestTo(TOKEN_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("client_id=client-id&client_secret=client-secret&refresh_token=refresh-token"
                        + "&grant_type=refresh_token"))
                .andRespond(withSuccess("{\"access_token\":\"access-token\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        testClient.server().expect(times(2), requestTo(SEND_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(mimeMessageContains("member@example.com", "123456", 10))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        testClient.sender().sendCode("member@example.com", "123456", 10);
        testClient.sender().sendCode("member@example.com", "123456", 10);

        testClient.server().verify();
    }

    @Test
    @DisplayName("OAuth 자격 증명의 예약 문자를 UTF-8 form 인코딩으로 전송한다")
    void encodesOAuthCredentialsAsUtf8FormValues() {
        String clientId = "fake+client/id=%&id";
        String clientSecret = "fake+secret/id=%&secret";
        String refreshToken = "fake+refresh/id=%&token";
        GmailMailProperties properties = new GmailMailProperties();
        properties.setClientId(clientId);
        properties.setClientSecret(clientSecret);
        properties.setRefreshToken(refreshToken);
        properties.setSender("sender@example.com");
        TestClient testClient = testClient(properties);

        testClient.server().expect(once(), requestTo(TOKEN_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> {
                    String formBody = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
                    Map<String, String> fields = new HashMap<>();
                    for (String entry : formBody.split("&", -1)) {
                        int separator = entry.indexOf('=');
                        assertThat(separator).isGreaterThan(0);
                        String name = URLDecoder.decode(entry.substring(0, separator), StandardCharsets.UTF_8);
                        String value = URLDecoder.decode(entry.substring(separator + 1), StandardCharsets.UTF_8);
                        fields.put(name, value);
                    }

                    assertThat(fields).containsExactlyInAnyOrderEntriesOf(Map.of(
                            "client_id", clientId,
                            "client_secret", clientSecret,
                            "refresh_token", refreshToken,
                            "grant_type", "refresh_token"));
                })
                .andRespond(withSuccess("{\"access_token\":\"access-token\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        testClient.server().expect(once(), requestTo(SEND_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        testClient.sender().sendCode("member@example.com", "123456", 10);

        testClient.server().verify();
    }

    @Test
    @DisplayName("Gmail 401에 한해 토큰을 다시 받고 한 번만 재전송한다")
    void refreshesAndRetriesOnceAfterUnauthorized() {
        TestClient testClient = testClient(configuredProperties());
        expectToken(testClient.server(), "first-token");
        testClient.server().expect(once(), requestTo(SEND_ENDPOINT))
                .andExpect(header("Authorization", "Bearer first-token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        expectToken(testClient.server(), "second-token");
        testClient.server().expect(once(), requestTo(SEND_ENDPOINT))
                .andExpect(header("Authorization", "Bearer second-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        testClient.sender().sendCode("member@example.com", "123456", 10);

        testClient.server().verify();
    }

    @Test
    @DisplayName("Gmail 5xx는 불명확한 전송 결과로 보고 자동 재시도하지 않는다")
    void doesNotRetryGmailServerError() {
        TestClient testClient = testClient(configuredProperties());
        expectToken(testClient.server(), "access-token");
        testClient.server().expect(once(), requestTo(SEND_ENDPOINT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        PasswordResetEmailException exception = catchThrowableOfType(
                () -> testClient.sender().sendCode("member@example.com", "123456", 10),
                PasswordResetEmailException.class);

        assertThat(exception).isNotNull();
        assertThat(exception).hasMessage("Password reset email provider request failed (HTTP 503).")
                .hasMessageNotContaining("member@example.com")
                .hasMessageNotContaining("123456")
                .hasMessageNotContaining("access-token");
        assertThat(exception.getCode()).isEqualTo("GMAIL_API");
        assertThat(exception.getHttpStatus()).isEqualTo(503);
        assertThat(exception.getProviderReason()).isNull();
        assertThat(exception.getCauseType()).isNull();

        testClient.server().verify();
    }

    @Test
    @DisplayName("캐시된 토큰이 만료 여유 시간 안으로 들어오면 다음 요청에서 다시 갱신한다")
    void refreshesAccessTokenAfterExpiryMargin() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-12T00:00:00Z"));
        TestClient testClient = testClient(configuredProperties(), clock);
        expectToken(testClient.server(), "first-token", 120);
        testClient.server().expect(once(), requestTo(SEND_ENDPOINT))
                .andExpect(header("Authorization", "Bearer first-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        expectToken(testClient.server(), "second-token", 120);
        testClient.server().expect(once(), requestTo(SEND_ENDPOINT))
                .andExpect(header("Authorization", "Bearer second-token"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        testClient.sender().sendCode("member@example.com", "123456", 10);
        clock.advance(Duration.ofSeconds(61));
        testClient.sender().sendCode("member@example.com", "123456", 10);

        testClient.server().verify();
    }

    @Test
    @DisplayName("OAuth invalid_grant 응답 본문과 비밀값은 예외에 포함되지 않는다")
    void hidesOAuthErrorBodyAndCredentials() {
        TestClient testClient = testClient(configuredProperties());
        testClient.server().expect(once(), requestTo(TOKEN_ENDPOINT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":"
                                + "\"refresh-token member@example.com 123456\"}"));

        PasswordResetEmailException exception = catchThrowableOfType(
                () -> testClient.sender().sendCode("member@example.com", "123456", 10),
                PasswordResetEmailException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage())
                .isEqualTo("Password reset email provider request failed (HTTP 400).")
                .doesNotContain("invalid_grant")
                .doesNotContain("refresh-token")
                .doesNotContain("member@example.com")
                .doesNotContain("123456");
        assertThat(exception.getCode()).isEqualTo("TOKEN_ENDPOINT");
        assertThat(exception.getHttpStatus()).isEqualTo(400);
        assertThat(exception.getProviderReason()).isEqualTo("invalid_grant");
        assertThat(exception.getCauseType()).isNull();
        assertThat(diagnosticsOf(exception))
                .doesNotContain("refresh-token", "client-secret", "member@example.com", "123456",
                        "access-token");

        testClient.server().verify();
    }

    @Test
    @DisplayName("Gmail API 오류는 errors[0].reason만 안전 진단값으로 남긴다")
    void exposesGmailApiReasonWithoutProviderMessage() {
        TestClient testClient = testClient(configuredProperties());
        expectToken(testClient.server(), "access-token");
        testClient.server().expect(once(), requestTo(SEND_ENDPOINT))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":403,\"message\":\"member@example.com 123456\","
                                + "\"errors\":[{\"reason\":\"insufficientPermissions\","
                                + "\"message\":\"member@example.com 123456\"}],"
                                + "\"status\":\"PERMISSION_DENIED\"}}"));

        PasswordResetEmailException exception = catchThrowableOfType(
                () -> testClient.sender().sendCode("member@example.com", "123456", 10),
                PasswordResetEmailException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage())
                .isEqualTo("Password reset email provider request failed (HTTP 403).")
                .doesNotContain("member@example.com")
                .doesNotContain("123456");
        assertThat(exception.getCode()).isEqualTo("GMAIL_API");
        assertThat(exception.getHttpStatus()).isEqualTo(403);
        assertThat(exception.getProviderReason()).isEqualTo("insufficientPermissions");
        assertThat(exception.getCauseType()).isNull();
        assertThat(diagnosticsOf(exception))
                .doesNotContain("member@example.com", "123456", "access-token", "client-secret");

        testClient.server().verify();
    }

    @Test
    @DisplayName("네트워크 실패는 HTTP 상태 없이 가장 깊은 원인 클래스만 진단값으로 남긴다")
    void exposesDeepestCauseClassForNetworkFailure() {
        TestClient testClient = testClient(configuredProperties());
        expectToken(testClient.server(), "access-token");
        testClient.server().expect(once(), requestTo(SEND_ENDPOINT))
                .andRespond(request -> {
                    throw new java.net.SocketTimeoutException("read timed out");
                });

        PasswordResetEmailException exception = catchThrowableOfType(
                () -> testClient.sender().sendCode("member@example.com", "123456", 10),
                PasswordResetEmailException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo("Password reset email delivery failed.");
        assertThat(exception.getCode()).isEqualTo("GMAIL_NETWORK");
        assertThat(exception.getHttpStatus()).isNull();
        assertThat(exception.getProviderReason()).isNull();
        assertThat(exception.getCauseType()).isEqualTo("SocketTimeoutException");
        assertThat(diagnosticsOf(exception))
                .doesNotContain("read timed out", "member@example.com", "123456", "access-token",
                        "client-secret", "ResourceAccessException");

        testClient.server().verify();
    }

    @Test
    @DisplayName("안전하지 않은 형태의 provider reason은 진단값에서 제외한다")
    void dropsUnsafeProviderReasonToken() {
        TestClient testClient = testClient(configuredProperties());
        testClient.server().expect(once(), requestTo(TOKEN_ENDPOINT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid grant member@example.com 123456\"}"));

        PasswordResetEmailException exception = catchThrowableOfType(
                () -> testClient.sender().sendCode("member@example.com", "123456", 10),
                PasswordResetEmailException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getProviderReason()).isNull();
        assertThat(diagnosticsOf(exception))
                .doesNotContain("member@example.com", "123456", "invalid grant");

        testClient.server().verify();
    }

    @Test
    @DisplayName("받는 주소의 헤더 주입 문자열은 네트워크 요청 전에 거부한다")
    void rejectsRecipientHeaderInjection() {
        TestClient testClient = testClient(configuredProperties());

        assertThatThrownBy(() -> testClient.sender().sendCode("member@example.com\r\nBcc: bad@example.com",
                "123456", 10))
                .isInstanceOf(PasswordResetEmailException.class)
                .extracting("code").isEqualTo("INVALID_RECIPIENT");
        testClient.server().verify();
    }

    @Test
    @DisplayName("발신자 헤더에 개행이 들어간 설정은 미구성으로 취급한다")
    void rejectsSenderHeaderInjection() {
        GmailMailProperties properties = configuredProperties();
        properties.setSender("sender@example.com\r\nBcc: bad@example.com");
        TestClient testClient = testClient(properties);

        assertThat(testClient.sender().isConfigured()).isFalse();
        assertThatThrownBy(() -> testClient.sender().sendCode("member@example.com", "123456", 10))
                .isInstanceOf(PasswordResetEmailException.class)
                .extracting("code").isEqualTo("NOT_CONFIGURED");
        testClient.server().verify();
    }

    @Test
    @DisplayName("동시 요청은 하나의 OAuth refresh 요청을 공유한다")
    void concurrentRequestsShareSingleTokenRefresh() throws Exception {
        int requestCount = 6;
        TestClient testClient = testClient(configuredProperties());
        testClient.server().expect(once(), requestTo(TOKEN_ENDPOINT))
                .andRespond(withSuccess("{\"access_token\":\"access-token\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        testClient.server().expect(times(requestCount), requestTo(SEND_ENDPOINT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?>[] futures = new Future<?>[requestCount];
            for (int index = 0; index < requestCount; index++) {
                futures[index] = executor.submit(() -> {
                    start.await();
                    testClient.sender().sendCode("member@example.com", "123456", 10);
                    return null;
                });
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        testClient.server().verify();
    }

    private static String diagnosticsOf(PasswordResetEmailException exception) {
        return exception.getMessage() + "|" + exception.getCode() + "|" + exception.getHttpStatus()
                + "|" + exception.getProviderReason() + "|" + exception.getCauseType();
    }

    private static void expectToken(MockRestServiceServer server, String accessToken) {
        expectToken(server, accessToken, 3600);
    }

    private static void expectToken(MockRestServiceServer server, String accessToken, int expiresInSeconds) {
        server.expect(once(), requestTo(TOKEN_ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"" + accessToken + "\",\"expires_in\":"
                        + expiresInSeconds + "}", MediaType.APPLICATION_JSON));
    }

    private static RequestMatcher mimeMessageContains(String email, String code, int expiresInMinutes) {
        return request -> {
            String json = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
            JsonNode body = OBJECT_MAPPER.readTree(json);
            byte[] mimeBytes = Base64.getUrlDecoder().decode(body.path("raw").asText());
            String mime = new String(mimeBytes, StandardCharsets.UTF_8);
            String decodedSubject = "Subject: =?UTF-8?B?"
                    + Base64.getEncoder().encodeToString("비밀번호 재설정 인증번호".getBytes(StandardCharsets.UTF_8))
                    + "?=";
            String decodedBody = new String(Base64.getMimeDecoder().decode(
                    mime.substring(mime.indexOf("\r\n\r\n") + 4)), StandardCharsets.UTF_8);
            assertThat(mime).contains("From: sender@example.com\r\nTo: " + email + "\r\n",
                    "Date: ", decodedSubject);
            assertThat(mime).contains("Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: base64\r\n\r\n");
            assertThat(decodedBody).contains(code, expiresInMinutes + "분간 유효합니다.");
        };
    }

    private static GmailMailProperties configuredProperties() {
        GmailMailProperties properties = new GmailMailProperties();
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setRefreshToken("refresh-token");
        properties.setSender("sender@example.com");
        return properties;
    }

    private static TestClient testClient(GmailMailProperties properties) {
        return testClient(properties, Clock.systemUTC());
    }

    private static TestClient testClient(GmailMailProperties properties, Clock clock) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GmailApiPasswordResetEmailSender sender = new GmailApiPasswordResetEmailSender(
                properties, OBJECT_MAPPER, builder.build(), clock);
        return new TestClient(sender, server);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new UnsupportedOperationException("Test clock is fixed to UTC.");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private record TestClient(GmailApiPasswordResetEmailSender sender, MockRestServiceServer server) {
    }
}
