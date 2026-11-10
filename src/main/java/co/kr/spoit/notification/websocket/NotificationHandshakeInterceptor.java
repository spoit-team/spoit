package co.kr.spoit.notification.websocket;

import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.security.MemberAccountStateVerifier;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 웹소켓 핸드셰이크를 로그인 사용자로 제한하고, CONNECT 프레임 검사에 필요한 값을 넘긴다.
 *
 * <p>인증 주체가 {@link MemberPrincipal}인 로그인 사용자가 아니면 핸드셰이크를 403으로 끝낸다.
 * 로그인 사용자라도 계정 상태를 DB에서 한 번 더 확인해, 탈퇴·이용정지 중이거나 역할이 바뀐 계정의
 * <b>재연결</b>을 거부한다. 이 확인은 연결을 새로 여는 시점에만 수행한다.</p>
 *
 * <p>같은 출처 검사는 이 클래스가 아니라, {@code WebSocketConfig}가 등록할 때 Spring이 기본으로
 * 추가하는 {@code OriginHandshakeInterceptor}가 전담한다.</p>
 */
@Slf4j
@Component
public class NotificationHandshakeInterceptor implements HandshakeInterceptor {

    /** 인증된 회원 번호를 핸드셰이크 속성으로 넘길 때 쓰는 키. */
    static final String USER_ID_ATTRIBUTE = "spoit.notification.userId";

    /** CONNECT 프레임의 CSRF 검사에 쓸 원본 토큰 속성 키. */
    static final String CSRF_TOKEN_ATTRIBUTE = "spoit.notification.csrfToken";

    /**
     * 필터 체인이 실제로 쓰는 CSRF 저장소. 요청 속성의 {@code CsrfToken}은 이 요청 전용 마스킹 값이라
     * 이전 HTML 요청이 내보낸 값과 비교할 수 없으므로, 저장소의 원본을 직접 읽는다.
     */
    private final CsrfTokenRepository csrfTokenRepository;

    /** 계정 상태(탈퇴·이용정지·역할)를 커밋된 DB에서 다시 확인할 때 쓴다. */
    private final UserDetailsService userDetailsService;

    public NotificationHandshakeInterceptor(CsrfTokenRepository csrfTokenRepository,
            @Lazy UserDetailsService userDetailsService) {
        this.csrfTokenRepository = csrfTokenRepository;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        MemberPrincipal principal = authenticatedPrincipal(request);
        if (principal == null || !MemberAccountStateVerifier.isStillActive(principal, this.userDetailsService)) {
            // 로그인하지 않았거나, 로그인 스냅샷 이후 계정이 제한·탈퇴·역할 변경된 상태다.
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(USER_ID_ATTRIBUTE, principal.getUserId());
        // 원본을 못 읽으면 CSRF 토큰 속성을 넣지 않는다. CONNECT 는 fail closed 로 거부된다.
        String csrfToken = csrfOrigin(request);
        if (csrfToken != null) {
            attributes.put(CSRF_TOKEN_ATTRIBUTE, csrfToken);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // 추가 작업 없음.
    }

    /** 핸드셰이크 요청의 인증 주체에서 로그인 회원 주체를 꺼낸다. 익명·비로그인은 null. */
    private static MemberPrincipal authenticatedPrincipal(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        if (httpRequest.getUserPrincipal() instanceof Authentication authentication) {
            return principalOf(authentication);
        }
        return null;
    }

    /** 인증 주체가 로그인 회원일 때만 그 주체를 돌려준다. */
    static MemberPrincipal principalOf(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getPrincipal() instanceof MemberPrincipal principal ? principal : null;
    }

    /** 인증 주체가 로그인 회원일 때만 회원 번호를 돌려준다. */
    static Long userIdOf(Authentication authentication) {
        MemberPrincipal principal = principalOf(authentication);
        return principal == null ? null : principal.getUserId();
    }

    /**
     * 저장소의 CSRF 원본 토큰을 읽는다. 없으면 null이며, 이 경우 CONNECT는 거부된다(fail closed).
     *
     * <p>요청 속성의 {@code CsrfToken}은 이 요청 전용 XOR 마스킹 값이므로 쓰지 않는다. 그것을
     * 원본으로 넘기면 이전 HTML 페이지가 내보낸 마스킹 값과 비교하게 되어 정상 CONNECT가 실패한다.</p>
     */
    private String csrfOrigin(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }
        return StompCsrfTokens.originOf(servletRequest.getServletRequest(), csrfTokenRepository);
    }
}
