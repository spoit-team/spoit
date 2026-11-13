package co.kr.spoit.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;

/**
 * 인증 완료 시 세션 등록과 계정 상태 재확인의 결과(통과·실패)를 확인한다.
 * 실제 세션 ID 회전 순서는 {@code SecurityConfig}가 만든 합성 전략이 보장한다.
 */
class MemberSessionAuthenticationStrategyTest {

    private final MemberSessionRegistry registry = new MemberSessionRegistry();

    @Test
    @DisplayName("활성 회원이면 세션을 등록하고, 같은 회원 번호의 세션은 함께 묶인다")
    void registersActiveMemberSessions() {
        MemberPrincipal principal = principal("일반 회원");
        MemberSessionAuthenticationStrategy strategy = strategy(username -> principal);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            strategy.onAuthentication(authentication(principal), request, new MockHttpServletResponse());

            MockHttpSession session = (MockHttpSession) request.getSession(false);
            assertThat(registry.getSessionInformation(session.getId())).isNotNull();
            assertThat(registry.isTracked(session)).isTrue();
        }

        List<SessionInformation> sessions = registry.sessionsOf(principal.getUserId());
        assertThat(sessions).hasSize(2);
        assertThat(registry.expireSessionsOf(principal.getUserId())).isEqualTo(2);
    }

    @Test
    @DisplayName("인증 직후 계정 상태가 바뀌었으면(역할·이용정지·조회 실패) 인증을 실패시키고 등록을 남기지 않는다")
    void failsWhenAccountStateChanged() {
        MemberPrincipal authenticated = principal("일반 회원");

        for (UserDetailsService changed : List.<UserDetailsService>of(
                username -> principal("시설 관리자"),
                username -> restrictedPrincipal(),
                username -> {
                    throw new UsernameNotFoundException("없음");
                })) {
            assertThatThrownBy(() -> strategy(changed).onAuthentication(authentication(authenticated),
                    new MockHttpServletRequest(), new MockHttpServletResponse()))
                    .isInstanceOf(SessionAuthenticationException.class);
        }
        assertThat(registry.getAllPrincipals()).isEmpty();
    }

    @Test
    @DisplayName("MemberPrincipal이 아닌 주체는 등록·검사 없이 그대로 둔다")
    void ignoresNonMemberPrincipal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Authentication anonymous = new UsernamePasswordAuthenticationToken("anonymous", null, List.of());

        strategy(username -> principal("일반 회원")).onAuthentication(anonymous, request,
                new MockHttpServletResponse());

        assertThat(request.getSession(false)).isNull();
        assertThat(registry.getAllPrincipals()).isEmpty();
    }

    private MemberSessionAuthenticationStrategy strategy(UserDetailsService userDetailsService) {
        return new MemberSessionAuthenticationStrategy(registry, () -> userDetailsService);
    }

    private static Authentication authentication(MemberPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static MemberPrincipal principal(String role) {
        return MemberPrincipal.from(member(role, null));
    }

    /** 이용정지 종료 시각이 미래인 주체를 만든다. */
    private static MemberPrincipal restrictedPrincipal() {
        LocalDateTime future = LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(3);
        return MemberPrincipal.from(member("일반 회원", future));
    }

    private static Member member(String role, LocalDateTime penaltyTime) {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("user@example.com");
        member.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        member.setNickname("회원");
        member.setPhone("010-1234-5678");
        member.setRole(role);
        member.setPenaltyTime(penaltyTime);
        return member;
    }
}
