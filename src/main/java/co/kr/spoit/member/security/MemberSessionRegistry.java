package co.kr.spoit.member.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationListener;
import org.springframework.security.core.session.AbstractSessionEvent;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.stereotype.Component;

import co.kr.spoit.member.domain.MemberPrincipal;
import jakarta.servlet.http.HttpSession;

/**
 * 회원 번호 기준으로 활성 HTTP 세션을 추적한다.
 *
 * <p>스프링의 {@link SessionRegistryImpl}은 주체(principal)의 {@code equals/hashCode}로 세션을
 * 묶는다. {@link MemberPrincipal}이 회원 번호로 같음을 판정하므로, 같은 회원의 모든 세션을 한 번에
 * 찾아 만료 표시할 수 있다. 세션이 파괴되면 {@link HttpSessionEventPublisher}가 보내는 사건을 받아
 * 등록을 지운다(등록이 남으면 다음 요청이 "만료된 등록"으로 잘못 거부된다).</p>
 *
 * <p>{@link #TRACKED_ATTRIBUTE}는 이 애플리케이션의 인증 체인(폼 로그인·remember-me)을 거친 세션에만
 * 남는 표식이다. 세션 고정 방지로 세션 ID가 바뀌어도 속성은 그대로 이전되므로, 등록 여부를 세션 ID가
 * 아니라 이 표식으로도 판정할 수 있다. 표식은 있으나 등록이 없는 세션은 <b>fail closed</b>로 거부한다
 * (등록 유실 = 추적 불가).</p>
 */
@Component
public class MemberSessionRegistry implements SessionRegistry, ApplicationListener<AbstractSessionEvent> {

    /** 인증 체인을 거쳐 추적 등록된 세션에 남는 표식 속성 이름. */
    public static final String TRACKED_ATTRIBUTE = "spoit.security.sessionTracked";

    private final SessionRegistryImpl delegate = new SessionRegistryImpl();

    /** 세션 ID로 그 세션을 특정 회원의 것으로 찾을 수 있는지 확인한다. */
    public boolean isTracked(HttpSession session) {
        return session != null && Boolean.TRUE.equals(session.getAttribute(TRACKED_ATTRIBUTE));
    }

    /** 인증 체인을 거친 세션임을 표시한다. 세션 고정 방지 뒤(새 세션 ID)에 호출해야 한다. */
    public void markTracked(HttpSession session) {
        if (session != null) {
            session.setAttribute(TRACKED_ATTRIBUTE, Boolean.TRUE);
        }
    }

    /** 해당 회원의 등록된 세션 목록. 없으면 빈 목록. */
    public List<SessionInformation> sessionsOf(Long userId) {
        if (userId == null || userId <= 0) {
            return List.of();
        }
        List<SessionInformation> found = new ArrayList<>();
        for (Object principal : this.delegate.getAllPrincipals()) {
            if (principal instanceof MemberPrincipal memberPrincipal
                    && Objects.equals(userId, memberPrincipal.getUserId())) {
                found.addAll(this.delegate.getAllSessions(principal, false));
            }
        }
        return found;
    }

    /**
     * 해당 회원의 모든 세션을 만료 표시한다. 세션 객체 자체를 무효화할 수는 없으므로(다른 스레드가
     * 소유), 다음 요청에서 {@link MemberSessionRevocationFilter}가 거부하며 세션을 무효화한다.
     *
     * @return 만료 표시한 세션 수
     */
    public int expireSessionsOf(Long userId) {
        List<SessionInformation> sessions = sessionsOf(userId);
        for (SessionInformation information : sessions) {
            information.expireNow();
        }
        return sessions.size();
    }

    @Override
    public void onApplicationEvent(AbstractSessionEvent event) {
        this.delegate.onApplicationEvent(event);
    }

    @Override
    public List<Object> getAllPrincipals() {
        return this.delegate.getAllPrincipals();
    }

    @Override
    public List<SessionInformation> getAllSessions(Object principal, boolean includeExpiredSessions) {
        return this.delegate.getAllSessions(principal, includeExpiredSessions);
    }

    @Override
    public SessionInformation getSessionInformation(String sessionId) {
        return this.delegate.getSessionInformation(sessionId);
    }

    @Override
    public void refreshLastRequest(String sessionId) {
        this.delegate.refreshLastRequest(sessionId);
    }

    @Override
    public void registerNewSession(String sessionId, Object principal) {
        this.delegate.registerNewSession(sessionId, principal);
    }

    @Override
    public void removeSessionInformation(String sessionId) {
        this.delegate.removeSessionInformation(sessionId);
    }
}
