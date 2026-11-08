package co.kr.spoit.notification.websocket;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * STOMP CONNECT 프레임에 실린 CSRF 토큰을 서버 저장소의 <em>원본</em> 토큰과 비교한다.
 *
 * <p>스프링 시큐리티 기본 {@code XorCsrfTokenRequestAttributeHandler}는 HTTP 요청마다 다른 XOR
 * 마스킹 값을 만든다. 그래서 이전 HTML 페이지가 내보낸 {@code _csrf.token}과 핸드셰이크 요청의
 * 속성 토큰은 <b>같은 원본이어도 문자열이 다르다</b>. 두 마스킹 값을 직접 비교하면 정상 브라우저의
 * CONNECT 가 항상 실패한다.</p>
 *
 * <p>그래서 비교 기준을 {@link CsrfTokenRepository}에 저장된 원본 하나로 고정한다. 마스킹 값은
 * {@code base64url(난수 || (난수 XOR 원본))} 형태로 난수를 함께 담으므로, <b>어느 요청이 만든
 * 값이든</b> 원본으로 복원된다. 복원값이 원본과 같을 때만 통과한다.</p>
 *
 * <p>원본을 읽을 수 없거나 형식이 맞지 않으면 통과시키지 않는다(fail closed). 토큰 값은 로그에
 * 남기지 않는다.</p>
 */
final class StompCsrfTokens {

    private StompCsrfTokens() {
    }

    /**
     * 저장소에서 원본 토큰을 읽는다. 없으면 {@code null}이며 호출 측은 CONNECT 를 거부한다.
     *
     * <p>요청 속성({@code CsrfToken} attribute)은 그 요청 전용 마스킹 값이므로 쓰지 않는다.
     * 필터 체인과 같은 저장소를 읽어야 이전 요청의 마스킹 값도 올바르게 검증된다.</p>
     */
    static String originOf(HttpServletRequest request, CsrfTokenRepository repository) {
        if (request == null || repository == null) {
            return null;
        }
        CsrfToken token;
        try {
            token = repository.loadToken(request);
        } catch (RuntimeException exception) {
            // 저장소 조회 실패. 원본을 모르면 검증할 수 없으므로 거부한다.
            return null;
        }
        return tokenValue(token);
    }

    /** 저장소 토큰의 원본 문자열. 읽을 수 없으면 {@code null}. */
    static String tokenValue(CsrfToken token) {
        if (token == null) {
            return null;
        }
        try {
            String value = token.getToken();
            return value == null || value.isBlank() ? null : value;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 제출된 값이 원본과 같은지(또는 같은 원본을 마스킹한 것인지) 확인한다.
     *
     * <p>원본을 그대로 보낸 경우를 먼저 보고, 아니면 마스킹을 풀어 비교한다. 비교는 상수 시간으로 한다.</p>
     */
    static boolean matchesValue(String supplied, String origin) {
        if (supplied == null || origin == null || supplied.isBlank() || origin.isBlank()) {
            return false;
        }
        if (constantTimeEquals(supplied, origin)) {
            return true;
        }
        String restored = unmask(supplied, origin);
        return restored != null && constantTimeEquals(restored, origin);
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * XOR 마스킹 값을 원본으로 복원한다. 형식이 맞지 않으면 {@code null}을 돌려주어 호출 측이
     * 거부하게 한다.
     *
     * <p>스프링 {@code XorCsrfTokenRequestAttributeHandler.createXoredCsrfToken} 과 같은 형식이다:
     * {@code base64url(난수 || (난수 XOR 원본))}, 길이는 원본 바이트의 두 배. 난수가 값 안에 함께
     * 실려 있으므로 어느 요청이 만든 마스킹 값이든 복원된다.</p>
     */
    private static String unmask(String masked, String origin) {
        byte[] combined;
        try {
            combined = Base64.getUrlDecoder().decode(masked);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        byte[] originBytes = origin.getBytes(StandardCharsets.UTF_8);
        if (combined.length != originBytes.length * 2) {
            return null;
        }
        byte[] restored = new byte[originBytes.length];
        for (int i = 0; i < originBytes.length; i++) {
            restored[i] = (byte) (combined[i] ^ combined[originBytes.length + i]);
        }
        return new String(restored, StandardCharsets.UTF_8);
    }
}
