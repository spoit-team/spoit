package co.kr.spoit.notification.websocket;

import java.util.Map;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * 핸드셰이크가 성공한 알림 웹소켓 세션을 회원 번호로 등록·해제한다.
 *
 * <p>{@code WebSocketConfig}가 {@code configureWebSocketTransport}에서 이 데코레이터를 씌운다. 연결이
 * 수립되면 추적 목록에 넣고, 연결이 닫히면 뺀다. 계정 상태 검사는 핸드셰이크 인터셉터가 하고, 등록과
 * 폐기 커밋의 경합은 {@link NotificationSessionRegistry#register}의 세대 비교가 처리한다.</p>
 */
public class NotificationSessionTrackingDecorator extends WebSocketHandlerDecorator {

    private final NotificationSessionRegistry registry;

    public NotificationSessionTrackingDecorator(WebSocketHandler delegate, NotificationSessionRegistry registry) {
        super(delegate);
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = userIdOf(session);
        if (userId != null) {
            this.registry.register(userId, session);
        }
        super.afterConnectionEstablished(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        Long userId = userIdOf(session);
        if (userId != null) {
            this.registry.unregister(userId, session);
        }
        super.afterConnectionClosed(session, closeStatus);
    }

    private static Long userIdOf(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object userId = attributes == null ? null
                : attributes.get(NotificationHandshakeInterceptor.USER_ID_ATTRIBUTE);
        if (userId instanceof Long identifier && identifier > 0) {
            return identifier;
        }
        return parsePrincipalName(session);
    }

    /** 세션 주체 이름(회원 번호 문자열)으로 회원 번호를 복원한다. */
    private static Long parsePrincipalName(WebSocketSession session) {
        if (session.getPrincipal() == null || session.getPrincipal().getName() == null) {
            return null;
        }
        try {
            long value = Long.parseLong(session.getPrincipal().getName());
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
