package co.kr.spoit.member.controller;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import co.kr.spoit.member.domain.PasswordResetSessionState;
import co.kr.spoit.member.dto.PasswordResetChallengeStatus;
import co.kr.spoit.member.dto.PasswordResetRequestResult;
import co.kr.spoit.member.dto.PasswordResetVerificationResult;
import co.kr.spoit.member.service.PasswordResetService;
import lombok.RequiredArgsConstructor;

/** 비밀번호 재설정 폼과 서버 세션의 challenge/grant만 연결한다. */
@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    static final String VIEW = "auth/find-password";
    static final String SESSION_STATE = PasswordResetSessionState.class.getName();
    static final String STEP_REQUEST = "REQUEST";
    static final String STEP_VERIFY = "VERIFY";
    static final String STEP_RESET = "RESET";
    static final String SENT_MESSAGE = "요청을 처리했습니다. 가입된 이메일이라면 인증번호를 보냈습니다.";
    static final String UNAVAILABLE_MESSAGE = "비밀번호 재설정은 현재 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.";

    private final PasswordResetService passwordResetService;
    private final SecurityContextLogoutHandler securityContextLogoutHandler = new SecurityContextLogoutHandler();
    private final CookieClearingLogoutHandler cookieClearingLogoutHandler =
            new CookieClearingLogoutHandler("JSESSIONID", "remember-me");

    @GetMapping("/login/find-password")
    public String page(@RequestParam(name = "email", required = false) String email,
            HttpSession session, Model model) {
        synchronized (session) {
            return pageLocked(email, session, model);
        }
    }

    private String pageLocked(String email, HttpSession session, Model model) {
        String step = STEP_REQUEST;
        int expiresInSeconds = 0;
        int resendInSeconds = 0;
        PasswordResetSessionState state = sessionState(session);
        String displayEmail = state == null ? email : state.email();
        if (state != null) {
            PasswordResetChallengeStatus status = this.passwordResetService.status(
                    state.challengeId(), state.rawGrant());
            if (status.active()) {
                step = status.verified() ? STEP_RESET : STEP_VERIFY;
                expiresInSeconds = status.expiresInSeconds();
                resendInSeconds = status.resendInSeconds();
            } else {
                session.removeAttribute(SESSION_STATE);
            }
        }
        String error = STEP_REQUEST.equals(step) && !this.passwordResetService.isConfigured()
                ? UNAVAILABLE_MESSAGE : null;
        addViewModel(model, step, displayEmail, error, null, expiresInSeconds, resendInSeconds);
        return VIEW;
    }

    @PostMapping("/login/find-password/send-code")
    public String sendCode(@RequestParam(name = "email", required = false) String email,
            HttpServletRequest request, HttpSession session, Model model) {
        synchronized (session) {
            return sendCodeLocked(email, request, session, model);
        }
    }

    private String sendCodeLocked(String email, HttpServletRequest request, HttpSession session, Model model) {
        // A resend starts a new flow and must discard any previous grant in this browser session.
        session.removeAttribute(SESSION_STATE);
        try {
            PasswordResetRequestResult result = this.passwordResetService.requestCode(
                    email, request.getRemoteAddr());
            if (!result.available()) {
                addViewModel(model, STEP_REQUEST, email, UNAVAILABLE_MESSAGE, null, 0, 0);
                return VIEW;
            }
            if (!result.accepted() || result.challengeId() == null) {
                addViewModel(model, STEP_REQUEST, email, "요청이 많습니다. 잠시 후 다시 요청해 주세요.",
                        null, 0, result.resendInSeconds());
                return VIEW;
            }
            String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
            session.setAttribute(SESSION_STATE,
                    new PasswordResetSessionState(result.challengeId(), null, normalizedEmail));
            addViewModel(model, STEP_VERIFY, normalizedEmail, null, SENT_MESSAGE,
                    result.expiresInSeconds(), result.resendInSeconds());
        } catch (IllegalArgumentException exception) {
            addViewModel(model, STEP_REQUEST, email, exception.getMessage(), null, 0, 0);
        }
        return VIEW;
    }

    @PostMapping("/login/find-password/verify-code")
    public String verifyCode(@RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "code", required = false) String code,
            HttpServletRequest request, HttpSession session, Model model) {
        synchronized (session) {
            return verifyCodeLocked(code, request, session, model);
        }
    }

    private String verifyCodeLocked(String code, HttpServletRequest request, HttpSession session, Model model) {
        PasswordResetSessionState state = sessionState(session);
        if (state == null) {
            addViewModel(model, STEP_REQUEST, null, "인증번호를 먼저 요청해 주세요.", null, 0, 0);
            return VIEW;
        }
        if (state.rawGrant() != null) {
            PasswordResetChallengeStatus alreadyVerified = this.passwordResetService.status(
                    state.challengeId(), state.rawGrant());
            if (alreadyVerified.active() && alreadyVerified.verified()) {
                addViewModel(model, STEP_RESET, state.email(), null, "이메일 인증이 완료되었습니다.",
                        alreadyVerified.expiresInSeconds(), alreadyVerified.resendInSeconds());
                return VIEW;
            }
            session.removeAttribute(SESSION_STATE);
            addViewModel(model, STEP_REQUEST, state.email(), "인증이 만료되었습니다. 다시 요청해 주세요.", null, 0, 0);
            return VIEW;
        }
        try {
            PasswordResetVerificationResult result = this.passwordResetService.verifyCode(
                    state.challengeId(), state.email(), code, request.getRemoteAddr());
            if (result.verified() && result.rawGrant() != null) {
                session.setAttribute(SESSION_STATE,
                        new PasswordResetSessionState(state.challengeId(), result.rawGrant(), state.email()));
                addViewModel(model, STEP_RESET, state.email(), null, "이메일 인증이 완료되었습니다.",
                        result.expiresInSeconds(), result.resendInSeconds());
                return VIEW;
            }
            PasswordResetChallengeStatus status = this.passwordResetService.status(state.challengeId(), null);
            if (!status.active()) {
                session.removeAttribute(SESSION_STATE);
                addViewModel(model, STEP_REQUEST, state.email(), "인증번호가 만료되었거나 사용할 수 없습니다. 다시 요청해 주세요.",
                        null, 0, status.resendInSeconds());
                return VIEW;
            }
            addViewModel(model, STEP_VERIFY, state.email(), "인증번호를 확인해 주세요.", null,
                    result.expiresInSeconds(), result.resendInSeconds());
        } catch (IllegalArgumentException exception) {
            addViewModel(model, STEP_VERIFY, state.email(), exception.getMessage(), null, 0, 0);
        }
        return VIEW;
    }

    @PostMapping("/login/find-password/reset")
    public String reset(@RequestParam(name = "email", required = false) String email,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam(name = "passwordConfirm", required = false) String passwordConfirm,
            HttpServletRequest request, HttpServletResponse response, HttpSession session,
            Authentication authentication, Model model) {
        synchronized (session) {
            return resetLocked(password, passwordConfirm, request, response, session, authentication, model);
        }
    }

    private String resetLocked(String password, String passwordConfirm, HttpServletRequest request,
            HttpServletResponse response, HttpSession session, Authentication authentication, Model model) {
        PasswordResetSessionState state = sessionState(session);
        if (state == null || state.rawGrant() == null) {
            session.removeAttribute(SESSION_STATE);
            addViewModel(model, STEP_REQUEST, null, "이메일 인증을 다시 진행해 주세요.", null, 0, 0);
            return VIEW;
        }
        try {
            if (this.passwordResetService.resetPassword(
                    state.challengeId(), state.rawGrant(), password, passwordConfirm)) {
                session.removeAttribute(SESSION_STATE);
                if (authentication != null && authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken)) {
                    this.securityContextLogoutHandler.logout(request, response, authentication);
                    this.cookieClearingLogoutHandler.logout(request, response, authentication);
                }
                String message = URLEncoder.encode("비밀번호가 변경되었습니다. 다시 로그인해 주세요.", StandardCharsets.UTF_8);
                return "redirect:/login?message=" + message;
            }
            session.removeAttribute(SESSION_STATE);
            addViewModel(model, STEP_REQUEST, state.email(), "인증이 만료되었거나 사용할 수 없습니다. 다시 요청해 주세요.", null, 0, 0);
        } catch (IllegalArgumentException exception) {
            PasswordResetChallengeStatus status = this.passwordResetService.status(
                    state.challengeId(), state.rawGrant());
            if (!status.active() || !status.verified()) {
                session.removeAttribute(SESSION_STATE);
                addViewModel(model, STEP_REQUEST, state.email(), "이메일 인증을 다시 진행해 주세요.", null, 0, 0);
            } else {
                addViewModel(model, STEP_RESET, state.email(), exception.getMessage(), null,
                        status.expiresInSeconds(), status.resendInSeconds());
            }
        }
        return VIEW;
    }

    private static PasswordResetSessionState sessionState(HttpSession session) {
        Object value = session.getAttribute(SESSION_STATE);
        return value instanceof PasswordResetSessionState state ? state : null;
    }

    private static void addViewModel(Model model, String step, String email, String error, String message,
            int expiresInSeconds, int resendInSeconds) {
        model.addAttribute("step", step);
        model.addAttribute("email", email == null || email.isBlank() ? null : email.trim());
        model.addAttribute("error", error);
        model.addAttribute("message", message);
        model.addAttribute("expiresInSeconds", Math.max(0, expiresInSeconds));
        model.addAttribute("resendInSeconds", Math.max(0, resendInSeconds));
    }
}
