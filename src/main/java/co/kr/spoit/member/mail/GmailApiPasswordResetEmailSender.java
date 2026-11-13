package co.kr.spoit.member.mail;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Gmail API transport for password-reset verification messages. */
@Component
public class GmailApiPasswordResetEmailSender implements PasswordResetEmailSender {

    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String SEND_ENDPOINT_TEMPLATE =
            "https://gmail.googleapis.com/gmail/v1/users/{userId}/messages/send";
    /** Gmail API alias for the account that owns the access token. */
    private static final String AUTHENTICATED_USER = "me";
    /** Bounds how much provider error body text is ever parsed. */
    private static final int MAX_ERROR_BODY_CHARS = 4096;
    /** Bounds cause-chain traversal when summarising a transport failure class. */
    private static final int MAX_CAUSE_DEPTH = 16;
    private static final Duration TOKEN_EXPIRY_MARGIN = Duration.ofSeconds(60);
    private static final String SUBJECT = "비밀번호 재설정 인증번호";
    private static final DateTimeFormatter RFC_2822_DATE = DateTimeFormatter
            .ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US)
            .withZone(ZoneOffset.UTC);

    private final GmailMailProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Clock clock;
    private final Object tokenRefreshLock = new Object();

    private volatile CachedAccessToken cachedAccessToken;

    @Autowired
    public GmailApiPasswordResetEmailSender(GmailMailProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, createRestClient(), Clock.systemUTC());
    }

    GmailApiPasswordResetEmailSender(GmailMailProperties properties, ObjectMapper objectMapper,
                                     RestClient restClient, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
        this.clock = clock;
    }

    @Override
    public boolean isConfigured() {
        return properties.isConfigured();
    }

    @Override
    public void sendCode(String email, String code, int expiresInMinutes) {
        if (!isConfigured()) {
            throw failure("NOT_CONFIGURED", "Password reset email delivery is unavailable.");
        }
        validateRequest(email, code, expiresInMinutes);

        String accessToken = getAccessToken();
        try {
            sendMessage(accessToken, email, code, expiresInMinutes);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 401) {
                throw gmailApiFailure(exception);
            }

            String refreshedToken = refreshAfterUnauthorized(accessToken);
            try {
                sendMessage(refreshedToken, email, code, expiresInMinutes);
            } catch (RestClientResponseException retryException) {
                throw gmailApiFailure(retryException);
            } catch (RestClientException retryException) {
                throw networkFailure(retryException);
            }
        } catch (RestClientException exception) {
            throw networkFailure(exception);
        }
    }

    private void sendMessage(String accessToken, String email, String code, int expiresInMinutes) {
        Map<String, String> requestBody = Map.of("raw", encodeRawMessage(email, code, expiresInMinutes));
        restClient.post()
                .uri(SEND_ENDPOINT_TEMPLATE, AUTHENTICATED_USER)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();
    }

    private String getAccessToken() {
        CachedAccessToken current = cachedAccessToken;
        if (current != null && current.isValidAt(clock.instant())) {
            return current.value();
        }

        synchronized (tokenRefreshLock) {
            current = cachedAccessToken;
            if (current != null && current.isValidAt(clock.instant())) {
                return current.value();
            }

            CachedAccessToken refreshed = requestAccessToken();
            cachedAccessToken = refreshed.isValidAt(clock.instant()) ? refreshed : null;
            return refreshed.value();
        }
    }

    private String refreshAfterUnauthorized(String rejectedToken) {
        synchronized (tokenRefreshLock) {
            CachedAccessToken current = cachedAccessToken;
            Instant now = clock.instant();
            if (current != null && current.isValidAt(now) && !current.value().equals(rejectedToken)) {
                return current.value();
            }

            cachedAccessToken = null;
            CachedAccessToken refreshed = requestAccessToken();
            cachedAccessToken = refreshed.isValidAt(clock.instant()) ? refreshed : null;
            return refreshed.value();
        }
    }

    private CachedAccessToken requestAccessToken() {
        String formBody = "client_id=" + encodeFormValue(properties.getClientId())
                + "&client_secret=" + encodeFormValue(properties.getClientSecret())
                + "&refresh_token=" + encodeFormValue(properties.getRefreshToken())
                + "&grant_type=refresh_token";
        try {
            String response = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String accessToken = root.path("access_token").asText("");
            long expiresInSeconds = root.path("expires_in").asLong(0);
            if (accessToken.isBlank() || expiresInSeconds <= 0) {
                throw failure("TOKEN_RESPONSE", "Google authorization response was invalid.");
            }

            long cacheSeconds = Math.max(0, expiresInSeconds - TOKEN_EXPIRY_MARGIN.toSeconds());
            Instant usableUntil = clock.instant().plusSeconds(cacheSeconds);
            return new CachedAccessToken(accessToken, usableUntil);
        } catch (RestClientResponseException exception) {
            throw oauthFailure(exception);
        } catch (ResourceAccessException exception) {
            throw failure("TOKEN_NETWORK", "Google authorization service is unavailable.",
                    null, null, deepestCauseType(exception));
        } catch (PasswordResetEmailException exception) {
            throw exception;
        } catch (RestClientException | java.io.IOException exception) {
            throw failure("TOKEN_RESPONSE", "Google authorization response was invalid.");
        }
    }

    private String encodeRawMessage(String email, String code, int expiresInMinutes) {
        String encodedSubject = Base64.getEncoder().encodeToString(SUBJECT.getBytes(StandardCharsets.UTF_8));
        String body = "안녕하세요.\n\n"
                + "비밀번호 재설정 인증번호는 " + code + "입니다.\n"
                + "인증번호는 " + expiresInMinutes + "분간 유효합니다.\n"
                + "본인이 요청하지 않았다면 이 이메일을 무시해 주세요.\n";
        String encodedBody = Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        String mimeMessage = "From: " + properties.getSender() + "\r\n"
                + "To: " + email + "\r\n"
                + "Date: " + RFC_2822_DATE.format(clock.instant()) + "\r\n"
                + "Subject: =?UTF-8?B?" + encodedSubject + "?=\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + encodedBody;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mimeMessage.getBytes(StandardCharsets.UTF_8));
    }

    private static void validateRequest(String email, String code, int expiresInMinutes) {
        if (!GmailMailProperties.isValidEmailAddress(email)) {
            throw failure("INVALID_RECIPIENT", "Password reset email recipient is invalid.");
        }
        if (code == null || !code.matches("[0-9]{6}")) {
            throw failure("INVALID_CODE", "Password reset verification code is invalid.");
        }
        if (expiresInMinutes < 1 || expiresInMinutes > 1440) {
            throw failure("INVALID_EXPIRY", "Password reset code expiry is invalid.");
        }
    }

    private static String encodeFormValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static RestClient createRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    private PasswordResetEmailException gmailApiFailure(RestClientResponseException exception) {
        // HTTP failures are identified by status/reason; the exception class is a response type,
        // not a transport cause, so no cause is recorded here.
        return providerFailure("GMAIL_API", exception.getStatusCode().value(),
                gmailApiReason(exception), null);
    }

    private PasswordResetEmailException oauthFailure(RestClientResponseException exception) {
        return providerFailure("TOKEN_ENDPOINT", exception.getStatusCode().value(),
                oauthErrorToken(exception), null);
    }

    private static PasswordResetEmailException networkFailure(RestClientException exception) {
        return failure("GMAIL_NETWORK", "Password reset email delivery failed.",
                null, null, deepestCauseType(exception));
    }

    private static PasswordResetEmailException providerFailure(String code, int status, String reason,
            String causeType) {
        return failure(code, "Password reset email provider request failed (HTTP " + status + ").",
                status, reason, causeType);
    }

    /**
     * Reads only the OAuth machine token ({@code error}); free-form {@code error_description}
     * is never retained.
     */
    private String oauthErrorToken(RestClientResponseException exception) {
        JsonNode root = readProviderErrorJson(exception);
        JsonNode error = root == null ? null : root.path("error");
        return error != null && error.isTextual() ? error.asText() : null;
    }

    /** Reads only {@code error.errors[0].reason}, never {@code error.message} or raw body. */
    private String gmailApiReason(RestClientResponseException exception) {
        JsonNode root = readProviderErrorJson(exception);
        JsonNode errors = root == null ? null : root.path("error").path("errors");
        if (errors == null || !errors.isArray() || errors.isEmpty()) {
            return null;
        }
        JsonNode reason = errors.get(0).path("reason");
        return reason.isTextual() ? reason.asText() : null;
    }

    private JsonNode readProviderErrorJson(RestClientResponseException exception) {
        try {
            String body = exception.getResponseBodyAsString();
            if (body == null || body.isBlank() || body.length() > MAX_ERROR_BODY_CHARS) {
                return null;
            }
            JsonNode root = this.objectMapper.readTree(body);
            return root != null && root.isObject() ? root : null;
        } catch (RuntimeException | java.io.IOException ignored) {
            return null;
        }
    }

    private static String causeType(Throwable throwable) {
        return throwable == null ? null : throwable.getClass().getSimpleName();
    }

    /**
     * Walks a bounded cause chain and reports only the deepest cause's simple class name.
     * Messages, stack traces, and the throwable itself are never retained.
     */
    private static String deepestCauseType(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable deepest = throwable;
        for (int depth = 0; depth < MAX_CAUSE_DEPTH; depth++) {
            Throwable cause = deepest.getCause();
            if (cause == null || cause == deepest) {
                break;
            }
            deepest = cause;
        }
        return causeType(deepest);
    }

    private static PasswordResetEmailException failure(String code, String message) {
        return new PasswordResetEmailException(code, message);
    }

    private static PasswordResetEmailException failure(String code, String message, Integer status,
            String providerReason, String causeType) {
        return new PasswordResetEmailException(code, message, status, providerReason, causeType);
    }

    private record CachedAccessToken(String value, Instant usableUntil) {

        private boolean isValidAt(Instant instant) {
            return instant.isBefore(usableUntil);
        }
    }
}
