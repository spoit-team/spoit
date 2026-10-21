package co.kr.spoit.notification.service;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 모든 컨트롤러 응답에 공통 뷰 속성을 추가한다.
 *
 * <p>헤더/내비게이션 등 공통 영역이 참조하는 값을 한 곳에서 제공하기 위한
 * 조언자이다. 현재는 인증과 알림 기능이 구현되어 있지 않으므로
 * {@code currentMember}는 {@code null}, {@code unreadCount}는 {@code 0}인
 * 빈 상태 값을 넣는다. 이는 템플릿이 속성 부재로 인한 오류 없이
 * 로그인 전/비로그인 화면을 렌더링하기 위한 계약이다.</p>
 *
 * <p>향후에는 인증 주체(principal)를 조회해 실제 회원 정보를,
 * 알림 서비스를 통해 안 읽은 알림 수를 채우는 지점이 된다. 템플릿이
 * {@code currentMember}의 {@code initial}, {@code nickname}, {@code phone}
 * 같은 프로퍼티를 참조하므로, 교체할 객체도 동일한 프로퍼티를 제공해야 한다.
 * 그때까지 템플릿은 {@code currentMember}가 {@code null}일 수 있다는 전제로
 * null-safe하게 작성되어야 한다.</p>
 */
@ControllerAdvice
public class GlobalModelAdvice {

    /**
     * 공통 모델 속성을 기본값으로 채운다.
     *
     * <p>반환값이 없는 {@code void} 메서드이므로 뷰 이름에는 영향을 주지 않고,
     * 모델만 보강한다. 이 메서드는 컨트롤러 핸들러가 실행되기 전에 호출되므로,
     * 핸들러가 같은 이름의 속성을 추가하면 나중에 추가된 그 값이 최종 모델
     * 값을 대체한다.</p>
     *
     * @param model 뷰 렌더링에 사용할 모델
     * @param request CSRF 토큰을 조회할 현재 요청
     */
    @ModelAttribute
    public void addCommonViewAttributes(Model model, HttpServletRequest request) {
        // 인증 도입 전까지는 비로그인 상태를 나타내는 null을 유지한다.
        model.addAttribute("currentMember", null);
        // 알림 데이터가 없으므로 0으로 고정해 배지 렌더링을 단순화한다.
        model.addAttribute("unreadCount", 0);
        // 응답 버퍼가 커밋된 뒤에는 세션을 새로 만들 수 없으므로, 뷰 렌더링 전에
        // 지연 로딩된 CSRF 토큰을 강제로 초기화해 세션과 토큰을 미리 준비한다.
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
    }
}
