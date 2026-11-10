package co.kr.spoit.notification.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import co.kr.spoit.member.security.MemberSessionRevokedEvent;

/**
 * 수립된 STOMP 연결은 HttpSession 무효화로 끊기지 않으므로, 커밋 이후 폐기 사건으로
 * 해당 회원의 연결만 닫히는지 확인한다.
 */
class NotificationSessionRegistryTest {

    private final NotificationSessionRegistry registry = new NotificationSessionRegistry();

    @Test
    @DisplayName("폐기 사건이 오면 해당 회원의 연결만 닫고 추적에서 제거한다")
    void closesOnlyTargetMemberConnections() throws Exception {
        WebSocketSession target = session("s1");
        WebSocketSession other = session("s2");
        registry.register(7L, target);
        registry.register(8L, other);

        registry.onMemberSessionRevoked(new MemberSessionRevokedEvent(7L));

        verify(target).close(NotificationSessionRegistry.REVOKED_STATUS);
        verify(other, never()).close(any(CloseStatus.class));
        assertThat(registry.countOf(7L)).isZero();
        assertThat(registry.countOf(8L)).isEqualTo(1);
    }

    @Test
    @DisplayName("닫힌 연결은 추적 목록에서 제거되고, 잘못된 회원 번호는 추적하지 않는다")
    void unregistersAndIgnoresInvalidUserId() {
        WebSocketSession session = session("s1");
        registry.register(null, session);
        registry.register(0L, session);
        registry.register(7L, session);

        registry.unregister(7L, session);

        assertThat(registry.countOf(7L)).isZero();
        assertThat(registry.countOf(null)).isZero();
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
