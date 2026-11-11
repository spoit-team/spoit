package co.kr.spoit.member.security;

import java.util.function.Supplier;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

import co.kr.spoit.member.domain.MemberPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 인증 완료 시 세션을 등록하고, 곧바로 계정 상태를 <strong>한 번</strong> DB에서 다시 확인한다.
 *
 * <p>순서가 중요하다. 이 전략은 {@code CompositeSessionAuthenticationStrategy}에서
 * {@code ChangeSessionIdAuthenticationStrategy}(세션 ID 회전) <b>뒤에</b> 실행되므로, 등록은 항상
 * 회전된 새 세션 ID로 이루어진다. 등록 뒤 재확인은 로그인 처리 중 커밋된 역할 변경·탈퇴·이용정지를
 * 잡아내기 위한 것이다(세션에 저장된 주체는 로그인 시점 스냅샷이다).</p>
 *
 * <p>재확인에 실패하면 방금 등록한 항목을 지우고 세션을 무효화한 뒤
 * {@link SessionAuthenticationException}으로 인증 자체를 실패시킨다. 남겨 두면 등록만 된 반쪽 세션이
 * 다음 요청에서 통과할 수 있다.</p>
 *
 * <p>주체가 {@link MemberPrincipal}이 아니면(익명·다른 인증 방식) 아무 것도 하지 않는다.</p>
 *
 * <p>{@code userDetailsServiceSupplier}는 인증이 일어날 때 비로소 조회한다. 생성 시점에 조회하면
 * 보안 설정 빈과 회원 서비스(비밀번호 인코더를 보안 설정에서 받는다) 사이에 순환 참조가 생긴다.</p>
 */
public class MemberSessionAuthenticationStrategy implements SessionAuthenticationStrategy {

    private final MemberSessionRegistry memberSessionRegistry;
    private final Supplier<UserDetailsService> userDetailsServiceSupplier;
    private final RegisterSessionAuthenticationStrategy registerStrategy;

    public MemberSessionAuthenticationStrategy(MemberSessionRegistry memberSessionRegistry,
            Supplier<UserDetailsService> userDetailsServiceSupplier) {
        this.memberSessionRegistry = memberSessionRegistry;
        this.userDetailsServiceSupplier = userDetailsServiceSupplier;
        this.registerStrategy = new RegisterSessionAuthenticationStrategy(memberSessionRegistry);
    }

    @Override
    public void onAuthentication(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        if (authentication == null || !(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
            return;
        }
        this.registerStrategy.onAuthentication(authentication, request, response);
        HttpSession session = request.getSession();
        this.memberSessionRegistry.markTracked(session);

        if (MemberAccountStateVerifier.isStillActive(principal, this.userDetailsServiceSupplier.get())) {
            return;
        }
        this.memberSessionRegistry.removeSessionInformation(session.getId());
        invalidateQuietly(session);
        throw new SessionAuthenticationException("계정 상태가 변경되어 로그인을 완료할 수 없습니다.");
    }

    private static void invalidateQuietly(HttpSession session) {
        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // 이미 무효화된 세션이다. 실패 처리 흐름을 막지 않는다.
        }
    }
}
