package co.kr.spoit.member.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 폐기(만료 표시)된 세션의 다음 요청을 <strong>CSRF 처리 이전에</strong> 거부한다.
 *
 * <p>스프링 기본 {@code ConcurrentSessionFilter}는 최대 세션 수 제한을 켜야만 등록되고, 등록되더라도
 * {@code CsrfFilter} 뒤에 놓여 만료 세션의 POST가 CSRF 검사까지 진행된다. 폐기는 "다음 요청부터 즉시"
 * 차단이 목적이므로 이 필터를 {@code CsrfFilter} 앞에 둔다.</p>
 *
 * <p>판정 순서:</p>
 * <ol>
 *   <li>세션이 없으면 통과. 익명·비세션 인증(테스트의 요청 단위 인증 포함)을 건드리지 않는다.</li>
 *   <li>세션 ID가 등록돼 있으면 그 등록의 만료 여부로만 판정한다. 만료면 거부, 아니면 통과.</li>
 *   <li>등록이 없는데 {@link MemberSessionRegistry#TRACKED_ATTRIBUTE} 표식이 있고 인증 주체가 있으면
 *       <b>fail closed</b>로 거부한다. 추적 표식만 남고 등록이 사라진 상태는 폐기 여부를 판단할 수
 *       없으므로 통과시키지 않는다.</li>
 *   <li>그 밖(표식 없는 세션)은 통과한다.</li>
 * </ol>
 *
 * <p>거부 시 인증 컨텍스트를 비우고 세션을 무효화하며 {@code JSESSIONID}·{@code remember-me} 쿠키를
 * 만료시킨 뒤 로그인 화면으로 보낸다. 필터 체인을 계속 진행하지 않으므로 CSRF 검사·컨트롤러는 실행되지
 * 않는다.</p>
 */
public class MemberSessionRevocationFilter extends OncePerRequestFilter {

    /** 폐기 안내 문구. */
    static final String REVOKED_MESSAGE = "계정 상태가 변경되어 로그아웃되었습니다. 다시 로그인해 주세요.";

    private static final String SESSION_COOKIE = "JSESSIONID";
    private static final String REMEMBER_ME_COOKIE = "remember-me";

    private final MemberSessionRegistry memberSessionRegistry;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    public MemberSessionRevocationFilter(MemberSessionRegistry memberSessionRegistry) {
        this.memberSessionRegistry = memberSessionRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            filterChain.doFilter(request, response);
            return;
        }
        SessionInformation information = this.memberSessionRegistry.getSessionInformation(session.getId());
        if (information != null) {
            if (information.isExpired()) {
                reject(request, response, session);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }
        if (this.memberSessionRegistry.isTracked(session) && isAuthenticated()) {
            // 추적 대상 세션인데 등록이 없다. 폐기 여부를 알 수 없으므로 통과시키지 않는다.
            reject(request, response, session);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !this.trustResolver.isAnonymous(authentication);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws IOException {
        SecurityContextHolder.clearContext();
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // 이미 무효화된 세션이다.
        }
        expireCookie(request, response, SESSION_COOKIE);
        expireCookie(request, response, REMEMBER_ME_COOKIE);
        String target = request.getContextPath() + "/login?error="
                + URLEncoder.encode(REVOKED_MESSAGE, StandardCharsets.UTF_8);
        response.sendRedirect(target);
    }

    private static void expireCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath(contextPath(request));
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private static String contextPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return contextPath == null || contextPath.isBlank() ? "/" : contextPath;
    }
}
