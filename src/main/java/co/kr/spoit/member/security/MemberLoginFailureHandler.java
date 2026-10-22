package co.kr.spoit.member.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 로그인 실패 시 로그인 화면으로 되돌리며 안내 문구를 URL 파라미터로 전달한다.
 * 계정 존재 여부를 감추기 위해 일반 실패는 같은 문구를 쓰되, 원인 안내가 더 유용한
 * {@link DisabledException}만 별도 문구로 처리해 계정 열거 가능성을 감수한다.
 */
@Component
public class MemberLoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    /** 아이디/비밀번호 불일치 안내 문구. */
    static final String INVALID_CREDENTIALS_MESSAGE = "이메일 또는 비밀번호를 확인해 주세요.";

    /** 계정 상태로 차단된 경우의 안내 문구. */
    static final String DISABLED_ACCOUNT_MESSAGE = "이용이 제한된 계정입니다. 고객센터에 문의해 주세요.";

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        String message = exception instanceof DisabledException
                ? DISABLED_ACCOUNT_MESSAGE
                : INVALID_CREDENTIALS_MESSAGE;

        StringBuilder target = new StringBuilder("/login")
                .append("?error=").append(encode(message));

        String email = request.getParameter("email");
        if (email != null && !email.isBlank()) {
            target.append("&email=").append(encode(email.trim()));
        }
        String redirect = request.getParameter("redirect");
        if (redirect != null && !redirect.isBlank()) {
            target.append("&redirect=").append(encode(redirect.trim()));
        }

        getRedirectStrategy().sendRedirect(request, response, target.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
