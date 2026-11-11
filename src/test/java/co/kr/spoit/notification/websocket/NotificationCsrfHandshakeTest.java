package co.kr.spoit.notification.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.domain.MemberPrincipalFixture;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 실제 스프링 시큐리티 기본 구성(세션 저장소 + XOR 마스킹 핸들러) 위에서
 * 핸드셰이크 → STOMP CONNECT 경계의 CSRF 검증을 확인한다.
 *
 * <p>핵심 회귀: {@code XorCsrfTokenRequestAttributeHandler}는 HTTP 요청마다 다른 마스킹 값을 만든다.
 * 이전 HTML 페이지가 내보낸 값과 핸드셰이크 요청의 속성 값을 직접 비교하면 정상 브라우저가 실패한다.
 * 서로 다른 두 요청의 <b>서로 다른 마스킹 값</b>이 같은 원본에 대해 모두 통과해야 한다.</p>
 */
class NotificationCsrfHandshakeTest {

    private static final String HEADER = "X-CSRF-TOKEN";
    private static final String PARAMETER = "_csrf";

    private HttpSessionCsrfTokenRepository repository;
    private NotificationHandshakeInterceptor handshakeInterceptor;
    private NotificationInboundInterceptor inboundInterceptor;
    private MockHttpSession session;
    private String origin;

    /**
     * 핸드셰이크가 계정 상태를 다시 확인할 때 쓰는 사용자 조회. 인증 픽스처와 같은 활성
     * {@link MemberPrincipal}을 돌려줘 정상 연결이 계속 통과하게 한다.
     */
    private final MemberPrincipal activePrincipal = MemberPrincipalFixture.member();
    private final UserDetailsService userDetailsService = username -> activePrincipal;

    @BeforeEach
    void setUp() {
        // SecurityConfig 가 등록하는 것과 같은 저장소 구현이다.
        repository = new HttpSessionCsrfTokenRepository();
        handshakeInterceptor = new NotificationHandshakeInterceptor(repository, userDetailsService);
        inboundInterceptor = new NotificationInboundInterceptor();
        session = new MockHttpSession();
        origin = "0f8a1c3e-5b7d-4a92-9c1e-2d3f4a5b6c7d";
        saveOrigin(session, origin);
    }

    @Test
    @DisplayName("서로 다른 두 HTTP 요청의 서로 다른 XOR 마스킹 값이 같은 원본에 대해 모두 통과한다")
    void bothPerRequestMaskedValuesPassAgainstSameOrigin() {
        // 1) 이전 HTML 페이지 렌더링 요청이 만든 마스킹 값 (화면의 _csrf.token)
        String pageToken = maskedTokenOf(sessionRequest(session));

        // 2) 핸드셰이크 HTTP 요청이 만든 마스킹 값 — 같은 원본인데 문자열은 다르다
        MockHttpServletRequest handshakeRequest = sessionRequest(session);
        String handshakeToken = maskedTokenOf(handshakeRequest);

        assertThat(origin).isNotEqualTo(pageToken);
        assertThat(origin).isNotEqualTo(handshakeToken);
        // 마스킹은 요청마다 달라진다. 이 값들을 직접 비교하면 실패하는 것이 원래 결함이었다.
        assertThat(pageToken).isNotEqualTo(handshakeToken);

        // 3) 핸드셰이크 인터셉터는 요청 속성의 마스킹 값이 아니라 저장소의 원본을 넘긴다.
        Map<String, Object> attributes = handshake(handshakeRequest);
        assertThat(attributes).containsEntry(NotificationHandshakeInterceptor.CSRF_TOKEN_ATTRIBUTE, origin);

        // 4) 브라우저는 HTML 에서 받은 pageToken 을 CONNECT 에 실어 보낸다. 통과해야 한다.
        assertThat(canConnect(attributes, pageToken)).isTrue();
        // 핸드셰이크 요청 자신의 토큰, 원본 그대로도 통과한다.
        assertThat(canConnect(attributes, handshakeToken)).isTrue();
        assertThat(canConnect(attributes, origin)).isTrue();
    }

    @Test
    @DisplayName("세 번째 요청의 마스킹 값도 같은 원본이면 통과한다(요청 수에 의존하지 않는다)")
    void thirdRequestMaskedValueAlsoPasses() {
        Map<String, Object> attributes = handshake(sessionRequest(session));

        for (int i = 0; i < 3; i++) {
            String anotherToken = maskedTokenOf(sessionRequest(session));
            assertThat(canConnect(attributes, anotherToken)).as("attempt %s", i).isTrue();
        }
    }

    @Test
    @DisplayName("다른 세션의 토큰이나 엉뚱한 값은 차단한다")
    void rejectsForeignSessionAndGarbage() {
        MockHttpSession otherSession = new MockHttpSession();
        saveOrigin(otherSession, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        String foreignToken = maskedTokenOf(sessionRequest(otherSession));
        assertThat(foreignToken).isNotEqualTo(origin);

        Map<String, Object> attributes = handshake(sessionRequest(session));

        assertThat(canConnect(attributes, foreignToken)).isFalse();
        assertThat(canConnect(attributes, "not-a-token")).isFalse();
        assertThat(canConnect(attributes, "")).isFalse();
        assertThat(canConnect(attributes, null)).isFalse();
    }

    @Test
    @DisplayName("저장소에 원본이 없으면 핸드셰이크가 CSRF 속성을 넘기지 않는다")
    void failsClosedWithoutStoredOrigin() {
        MockHttpSession emptySession = new MockHttpSession();

        Map<String, Object> attributes = handshake(sessionRequest(emptySession));

        assertThat(attributes).doesNotContainKey(NotificationHandshakeInterceptor.CSRF_TOKEN_ATTRIBUTE);
        // 속성이 없으므로 어떤 토큰을 보내도 연결되지 않는다.
        assertThat(canConnect(attributes, origin)).isFalse();
        assertThat(canConnect(attributes, "any-masked-value")).isFalse();
    }

    @Test
    @DisplayName("비로그인 핸드셰이크는 403으로 끝나고 속성을 남기지 않는다")
    void rejectsAnonymousHandshake() {
        // 인증 주체가 없는 요청. 세션 토큰이 있어도 연결을 맺지 않는다.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mypage");
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = handshakeInterceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new org.springframework.http.server.ServletServerHttpResponse(response),
                null, attributes);

        assertThat(accepted).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자의 세션 토큰으로는 연결되지 않는다")
    void rejectsTokenBoundToAnotherUsersSession() {
        MockHttpSession victimSession = new MockHttpSession();
        saveOrigin(victimSession, "11111111-2222-3333-4444-555555555555");
        String victimToken = maskedTokenOf(sessionRequest(victimSession));

        // 공격자 세션으로 핸드셰이크: 저장소 원본이 다르다.
        Map<String, Object> attackerAttributes = handshake(sessionRequest(session));

        assertThat(canConnect(attackerAttributes, victimToken)).isFalse();
    }

    @Test
    @DisplayName("로그인 상태여도 조회 결과가 이용정지 중이면 403으로 거부하고 속성을 남기지 않는다")
    void rejectsStillAuthenticatedPrincipalThatIsNowSuspended() {
        LocalDateTime futurePenalty = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(3);
        MemberPrincipal suspended = principalOf("일반 회원", futurePenalty);

        assertHandshakeRejected(username -> suspended);
    }

    @Test
    @DisplayName("로그인 상태여도 조회 결과의 역할이 달라졌으면 403으로 거부한다")
    void rejectsPrincipalWhoseRoleChanged() {
        assertHandshakeRejected(username -> MemberPrincipalFixture.admin());
    }

    @Test
    @DisplayName("계정 조회가 실패하면 핸드셰이크를 거부한다(fail closed)")
    void rejectsWhenAccountLookupFails() {
        assertHandshakeRejected(username -> {
            throw new IllegalStateException("회원 조회 실패");
        });
    }

    /* ---------------- helpers ---------------- */

    /** 커밋된 계정 상태를 다시 읽는 조회가 거부 사유를 보고할 때의 핸드셰이크 결과를 확인한다. */
    private void assertHandshakeRejected(UserDetailsService lookup) {
        NotificationHandshakeInterceptor interceptor = new NotificationHandshakeInterceptor(repository, lookup);
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                new ServletServerHttpRequest(sessionRequest(session)),
                new org.springframework.http.server.ServletServerHttpResponse(response), null, attributes);

        assertThat(accepted).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(attributes).isEmpty();
    }

    /** 이용정지 종료 시각이 미래인 회원의 주체를 만든다. */
    private static MemberPrincipal principalOf(String role, LocalDateTime penaltyTime) {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("user@example.com");
        member.setNickname("회원");
        member.setPhone("010-1234-5678");
        member.setRole(role);
        member.setPenaltyTime(penaltyTime);
        return MemberPrincipal.from(member);
    }

    private void saveOrigin(MockHttpSession target, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(target);
        repository.saveToken(new DefaultCsrfToken(HEADER, PARAMETER, token), request,
                new MockHttpServletResponse());
    }

    /** 세션에 묶인 요청. 저장소가 이 세션의 원본을 읽는다. */
    private MockHttpServletRequest sessionRequest(MockHttpSession target) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mypage");
        request.setSession(target);
        Authentication authentication = authentication();
        request.setUserPrincipal(authentication);
        return request;
    }

    /**
     * 실제 {@link XorCsrfTokenRequestAttributeHandler} 로 이 요청의 마스킹 값을 만든다.
     * 화면이 {@code _csrf.token} 으로 내보내는 값과 같은 경로다.
     */
    private String maskedTokenOf(MockHttpServletRequest request) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        XorCsrfTokenRequestAttributeHandler handler = new XorCsrfTokenRequestAttributeHandler();
        Supplier<CsrfToken> deferred = () -> repository.loadToken(request);
        handler.handle(request, response, deferred);
        Object attribute = request.getAttribute(CsrfToken.class.getName());
        assertThat(attribute).isInstanceOf(CsrfToken.class);
        return ((CsrfToken) attribute).getToken();
    }

    private Map<String, Object> handshake(HttpServletRequest request) {
        Map<String, Object> attributes = new HashMap<>();
        boolean accepted = handshakeInterceptor.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponseAdapter(new MockHttpServletResponse()), null, attributes);
        assertThat(accepted).isTrue();
        return attributes;
    }

    /** 핸드셰이크 결과 속성으로 CONNECT 프레임을 검증한다. */
    private boolean canConnect(Map<String, Object> sessionAttributes, String suppliedToken) {
        Map<String, String> headers = suppliedToken == null ? Map.of() : Map.of(HEADER, suppliedToken);
        var accessor = org.springframework.messaging.simp.stomp.StompHeaderAccessor.create(
                org.springframework.messaging.simp.stomp.StompCommand.CONNECT);
        headers.forEach(accessor::addNativeHeader);
        accessor.setUser(new NotificationPrincipal("7"));
        accessor.setSessionAttributes(new HashMap<>(sessionAttributes));
        accessor.setLeaveMutable(true);
        var message = org.springframework.messaging.support.MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());
        try {
            inboundInterceptor.preSend(message, null);
            return true;
        } catch (org.springframework.security.access.AccessDeniedException exception) {
            return false;
        }
    }

    private static Authentication authentication() {
        MemberPrincipal principal = MemberPrincipalFixture.member();
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    /** 핸드셰이크가 받는 {@code ServerHttpResponse} 어댑터. 상태 코드만 기록한다. */
    private static final class ServletServerHttpResponseAdapter
            extends org.springframework.http.server.ServletServerHttpResponse {

        private ServletServerHttpResponseAdapter(HttpServletResponse response) {
            super(response);
        }
    }
}
