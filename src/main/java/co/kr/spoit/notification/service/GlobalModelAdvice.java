package co.kr.spoit.notification.service;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.dto.CurrentMemberDto;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 모든 컨트롤러 응답에 공통 뷰 속성을 추가한다.
 * 로그인 세션은 템플릿이 프로퍼티를 바로 참조할 수 있도록 {@link CurrentMemberDto}로 변환해
 * {@code currentMember}에 넣고, 비로그인·익명 인증은 {@code null}로 둔다.
 * 알림 조회 구현이 없으므로 {@code unreadCount}는 {@code 0}으로 유지한다.
 */
@ControllerAdvice
public class GlobalModelAdvice {

    /** 공통 모델 속성을 채운다. 핸들러가 같은 이름을 추가하면 그 값이 최종 모델을 대체한다. */
    @ModelAttribute
    public void addCommonViewAttributes(Model model, HttpServletRequest request,
            Authentication authentication) {
        model.addAttribute("currentMember", currentMember(authentication));
        // 알림 데이터가 없으므로 0으로 고정한다.
        model.addAttribute("unreadCount", 0);
        // 응답 버퍼가 커밋된 뒤에는 세션을 만들 수 없으므로 렌더링 전에 CSRF 토큰을 초기화한다.
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
    }

    /** 인증 정보에서 화면용 회원 DTO를 만든다. 비로그인·익명 인증이면 {@code null}. */
    private static CurrentMemberDto currentMember(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getPrincipal() instanceof MemberPrincipal principal
                ? CurrentMemberDto.from(principal)
                : null;
    }
}
