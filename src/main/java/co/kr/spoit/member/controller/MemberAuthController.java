package co.kr.spoit.member.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import co.kr.spoit.member.security.MemberLoginSuccessHandler;

/**
 * 로그인 화면 진입점을 담당한다.
 * 인증은 시큐리티 폼 로그인({@code POST /login})이 처리하므로 POST 핸들러를 두지 않고
 * ({@code loginProcessingUrl}과 충돌), 폼이 필요로 하는 모델 속성만 요청 파라미터에서 복원한다.
 * 실패·로그아웃 안내는 세션 대신 URL 파라미터로만 표현한다.
 */
@Controller
public class MemberAuthController {

    /**
     * 로그인 화면을 렌더링한다.
     * 이미 인증된 사용자는 기본 화면으로 리다이렉트하되, {@code ANONYMOUS} 인증은 비로그인으로 본다.
     */
    @GetMapping("/login")
    public String login(
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "message", required = false) String message,
            @RequestParam(name = "redirect", required = false) String redirect,
            @RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "remember", required = false, defaultValue = "false") boolean remember,
            Model model,
            Authentication authentication) {

        if (isAuthenticated(authentication)) {
            return "redirect:" + MemberLoginSuccessHandler.DEFAULT_TARGET;
        }

        model.addAttribute("error", blankToNull(error));
        model.addAttribute("message", blankToNull(message));
        // 성공 핸들러와 같은 규칙으로 검증해 폼에 위험한 값이 되돌아가지 않게 한다.
        model.addAttribute("redirect", redirect == null || redirect.isBlank()
                ? null
                : MemberLoginSuccessHandler.resolveTarget(redirect));
        model.addAttribute("email", blankToNull(email));
        model.addAttribute("remember", remember);
        return "auth/login";
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
