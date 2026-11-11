package co.kr.spoit.notification.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

/**
 * 핸드셰이크 이후 수립·종료되는 WebSocket 세션이 회원 번호로 추적 목록에 들어가고 나오는지 확인한다.
 *
 * <p>회원 번호는 핸드셰이크가 남긴 세션 속성에서 먼저 읽고, 없으면 세션 주체 이름(회원 번호 문자열)으로
 * 복원한다. 복원할 수 없는 신원은 추적하지 않는다.</p>
 */
class NotificationSessionTrackingDecoratorTest {

    private final NotificationSessionRegistry registry = new NotificationSessionRegistry();
    private final WebSocketHandler delegate = mock(WebSocketHandler.class);
    private final NotificationSessionTrackingDecorator decorator =
            new NotificationSessionTrackingDecorator(delegate, registry);

    @Test
    @DisplayName("연결이 수립되면 핸드셰이크 속성의 회원 번호로 추적을 시작한다")
    void registersOnConnectionEstablishedFromHandshakeAttribute() throws Exception {
        WebSocketSession session = session(Map.of(NotificationHandshakeInterceptor.USER_ID_ATTRIBUTE, 7L), null);

        decorator.afterConnectionEstablished(session);

        assertThat(registry.countOf(7L)).isEqualTo(1);
        verify(delegate).afterConnectionEstablished(session);
    }

    @Test
    @DisplayName("세션 속성에 회원 번호가 없으면 주체 이름에서 복원해 추적한다")
    void registersFromPrincipalNameWhenAttributeMissing() throws Exception {
        WebSocketSession session = session(Map.of(), new NotificationPrincipal("7"));

        decorator.afterConnectionEstablished(session);

        assertThat(registry.countOf(7L)).isEqualTo(1);
    }

    @Test
    @DisplayName("연결이 닫히면 추적에서 제거한다")
    void unregistersOnConnectionClosed() throws Exception {
        WebSocketSession session = session(Map.of(NotificationHandshakeInterceptor.USER_ID_ATTRIBUTE, 7L), null);
        decorator.afterConnectionEstablished(session);

        decorator.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(registry.countOf(7L)).isZero();
        verify(delegate).afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    @Test
    @DisplayName("신원을 복원할 수 없는 세션은 추적하지 않는다")
    void ignoresSessionsWithoutResolvableIdentity() throws Exception {
        WebSocketSession noAttributes = session(null, null);
        WebSocketSession noIdentity = session(Map.of(), null);
        WebSocketSession stringAttribute =
                session(Map.of(NotificationHandshakeInterceptor.USER_ID_ATTRIBUTE, "7"), null);
        WebSocketSession nonPositiveAttribute =
                session(Map.of(NotificationHandshakeInterceptor.USER_ID_ATTRIBUTE, 0L), null);
        WebSocketSession nonNumericPrincipal = session(Map.of(), new NotificationPrincipal("회원"));
        WebSocketSession nonPositivePrincipal = session(Map.of(), new NotificationPrincipal("-5"));

        for (WebSocketSession session : new WebSocketSession[] {
                noAttributes, noIdentity, stringAttribute, nonPositiveAttribute,
                nonNumericPrincipal, nonPositivePrincipal }) {
            decorator.afterConnectionEstablished(session);
        }

        assertThat(registry.countOf(7L)).isZero();
        // 추적하지 않아도 위임 핸들러의 연결 수립 처리는 그대로 진행된다.
        verify(delegate, times(6)).afterConnectionEstablished(any(WebSocketSession.class));
    }

    private static WebSocketSession session(Map<String, Object> attributes, Principal principal) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getPrincipal()).thenReturn(principal);
        return session;
    }
}
