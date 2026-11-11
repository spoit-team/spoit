package co.kr.spoit.member.security;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import co.kr.spoit.member.domain.MemberPrincipal;
import jakarta.servlet.FilterChain;

/**
 * 폐기된 세션의 다음 요청이 CSRF 처리 이전에 거부되는지, 익명·미추적 세션은 통과하는지 확인한다.
 */
class MemberSessionRevocationFilterTest {

    private final MemberSessionRegistry registry = new MemberSessionRegistry();
    private final MemberSessionRevocationFilter filter = new MemberSessionRevocationFilter(registry);
    private final RecordingChain chain = new RecordingChain();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("만료 표시된 세션은 필터 체인을 진행하지 않고 인증·세션·쿠키를 정리한 뒤 로그인 화면으로 돌린다")
    void rejectsExpiredSession() throws Exception {
        MemberPrincipal principal = member();
        authenticate(principal);
        MockHttpSession session = trackedSession(principal);
        registry.expireSessionsOf(principal.getUserId());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(session), response, chain);

        assertThat(chain.invoked).isFalse();
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getRedirectedUrl()).startsWith("/login?error=");
        assertThat(response.getCookies()).extracting(cookie -> cookie.getName())
                .contains("JSESSIONID", "remember-me");
    }

    @Test
    @DisplayName("추적 표식은 있지만 등록이 없는 인증 세션은 fail closed로 거부한다")
    void rejectsTrackedButUnregisteredSession() throws Exception {
        MemberPrincipal principal = member();
        authenticate(principal);
        MockHttpSession session = trackedSession(principal);
        registry.removeSessionInformation(session.getId());

        filter.doFilter(request(session), new MockHttpServletResponse(), chain);

        assertThat(chain.invoked).isFalse();
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("세션이 없거나 추적 표식이 없는 세션은 통과시킨다")
    void passesSessionlessAndUntrackedRequests() throws Exception {
        authenticate(member());

        filter.doFilter(new MockHttpServletRequest("GET", "/mypage"), new MockHttpServletResponse(), chain);
        assertThat(chain.invoked).isTrue();

        MockHttpSession untracked = new MockHttpSession();
        chain.invoked = false;
        filter.doFilter(request(untracked), new MockHttpServletResponse(), chain);
        assertThat(chain.invoked).isTrue();
        assertThat(untracked.isInvalid()).isFalse();
    }

    private MockHttpSession trackedSession(MemberPrincipal principal) {
        MockHttpSession session = new MockHttpSession();
        registry.registerNewSession(session.getId(), principal);
        registry.markTracked(session);
        return session;
    }

    private static MockHttpServletRequest request(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mypage/profile");
        request.setSession(session);
        return request;
    }

    private static void authenticate(MemberPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static final class RecordingChain implements FilterChain {

        private boolean invoked;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
            this.invoked = true;
        }
    }
}
