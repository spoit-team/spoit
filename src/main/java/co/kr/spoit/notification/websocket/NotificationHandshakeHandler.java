package co.kr.spoit.notification.websocket;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * 인증된 회원 번호를 STOMP 세션 주체로 고정한다.
 *
 * <p>기본 핸드셰이크 핸들러는 HTTP 인증 주체를 그대로 쓰므로 이름이 이메일이 된다. 사용자별
 * 큐 주소({@code /user/{userId}/queue/notifications})와 구독 세션을 회원 번호로 맞추기 위해
 * 핸드셰이크 인터셉터가 넘긴 회원 번호만 사용한다. 회원 번호가 없으면 인증하지 않은 것으로 보고
 * 핸드셰이크를 실패시킨다.</p>
 */
public class NotificationHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        Object userId = attributes.get(NotificationHandshakeInterceptor.USER_ID_ATTRIBUTE);
        if (!(userId instanceof Long identifier) || identifier <= 0) {
            // 인증 주체가 없으면 세션 주체도 만들지 않는다.
            return null;
        }
        return new NotificationPrincipal(String.valueOf(identifier));
    }
}
