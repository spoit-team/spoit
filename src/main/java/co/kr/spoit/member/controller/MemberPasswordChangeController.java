package co.kr.spoit.member.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.service.MemberPasswordChangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MemberPasswordChangeController {

    private final MemberPasswordChangeService passwordChangeService;

    @PostMapping("/mypage/password")
    public String changePassword(@RequestParam(name = "currentPassword", required = false) String currentPassword,
            @RequestParam(name = "newPassword", required = false) String newPassword,
            @RequestParam(name = "newPasswordConfirm", required = false) String newPasswordConfirm,
            Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
            return "redirect:/login";
        }
        try {
            passwordChangeService.changePassword(principal.getUserId(), currentPassword, newPassword, newPasswordConfirm);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "비밀번호를 변경하지 못했습니다." : exception.getMessage();
            return "redirect:/mypage/profile?passwordError=" + URLEncoder.encode(message, StandardCharsets.UTF_8)
                    + "#password-change";
        }
        new CookieClearingLogoutHandler("JSESSIONID", "remember-me").logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?message=" + URLEncoder.encode(
                "비밀번호가 변경되었습니다. 다시 로그인해 주세요.", StandardCharsets.UTF_8);
    }
}
