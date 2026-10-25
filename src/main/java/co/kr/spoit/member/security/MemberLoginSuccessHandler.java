package co.kr.spoit.member.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그인 성공 후 이동할 경로를 결정한다.
 * {@code redirect} 파라미터는 사내 상대 경로만 허용해 오픈 리다이렉트를 막고,
 * 로그인·로그아웃·가입 화면으로 되돌아가는 값은 기본 경로로 대체한다.
 */
@Component
public class MemberLoginSuccessHandler implements AuthenticationSuccessHandler {

    /** 로그인 직후 보여줄 기본 화면. */
    public static final String DEFAULT_TARGET = "/matches";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        response.sendRedirect(resolveTarget(request.getParameter("redirect")));
    }

    /** 요청된 이동 경로를 검증해 실제 이동 대상을 반환한다. */
    public static String resolveTarget(String requested) {
        if (requested == null || requested.isBlank()) {
            return DEFAULT_TARGET;
        }
        String target = requested.trim();
        // 사내 상대 경로만 허용한다. "//host"와 스킴이 있는 URL은 외부 이동이다.
        if (!target.startsWith("/") || target.startsWith("//") || target.contains("\\")) {
            return DEFAULT_TARGET;
        }
        String path = target.split("[?#]", 2)[0];
        if (path.equals("/login") || path.equals("/logout") || path.equals("/signup")) {
            return DEFAULT_TARGET;
        }
        return target;
    }
}
