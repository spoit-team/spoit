package co.kr.spoit.member.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import co.kr.spoit.member.dto.SignupForm;
import co.kr.spoit.member.service.MemberService;
import lombok.RequiredArgsConstructor;

/**
 * 회원가입 화면과 제출을 담당한다.
 * 검증은 서비스가 최종 판단하고, 컨트롤러는 폼·지역 목록을 다시 구성해 오류를 화면에 표시한다.
 */
@Controller
@RequiredArgsConstructor
public class MemberSignupController {

    private static final String VIEW = "auth/signup";
    private static final String SUCCESS_MESSAGE = "회원가입이 완료되었습니다. 로그인해 주세요.";
    private static final String BINDING_ERROR_MESSAGE = "입력 정보를 확인해 주세요.";

    /** 폼에 없는 속성이 바인딩되지 않도록 허용 목록으로 제한한다. */
    private static final String[] ALLOWED_FIELDS = {
            "agreeService", "agreePrivacy", "marketingOptIn",
            "role", "facilityName", "adminInviteCode",
            "email", "phone", "password", "passwordConfirm", "nickname", "regionId"
    };

    private final MemberService memberService;

    @InitBinder("signupForm")
    void initSignupFormBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    /** 가입 폼을 빈 상태로 렌더링한다. 지역 선택지는 DB에서 조회한다. */
    @GetMapping("/signup")
    public String signupForm(Model model) {
        return renderForm(model, new SignupForm(), null);
    }

    /** 가입 요청을 처리한다. 성공 시 자동 로그인 없이 로그인 화면으로 이동한다. */
    @PostMapping("/signup")
    public String signup(@ModelAttribute("signupForm") SignupForm form,
            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return renderError(model, form, BINDING_ERROR_MESSAGE);
        }
        try {
            memberService.register(form);
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            return renderError(model, form,
                    message == null || message.isBlank() ? BINDING_ERROR_MESSAGE : message);
        }
        return "redirect:/login?message=" + URLEncoder.encode(SUCCESS_MESSAGE, StandardCharsets.UTF_8);
    }

    /** 오류 화면을 렌더링한다. 비밀번호·초대 코드는 다시 그리지 않는다. */
    private String renderError(Model model, SignupForm form, String error) {
        form.setPassword(null);
        form.setPasswordConfirm(null);
        form.setAdminInviteCode(null);
        return renderForm(model, form, error);
    }

    private String renderForm(Model model, SignupForm form, String error) {
        model.addAttribute("signupForm", form);
        model.addAttribute("districts", memberService.findSignupRegions());
        model.addAttribute("error", error);
        model.addAttribute("message", null);
        return VIEW;
    }
}
