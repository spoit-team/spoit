package co.kr.spoit.notification.websocket;

import java.security.Principal;
import java.util.Map;
import java.util.Set;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * STOMP 인바운드 프레임을 검사한다. {@code spring-security-messaging}을 추가하지 않고도 같은
 * 보호를 명시적으로 구현한다(fail closed).
 *
 * <p>허용 목록 방식이다. {@link #ALLOWED_COMMANDS}에 없는 명령은 모두 거부하므로, 나중에 프로토콜이
 * 명령을 추가해도 자동으로 열리지 않는다.</p>
 *
 * <ul>
 *   <li>{@code CONNECT}·{@code STOMP}: 로그인 주체가 있어야 하고, 서버 저장소의 CSRF 원본과
 *       일치해야 한다.</li>
 *   <li>{@code SUBSCRIBE}: 개인 알림 큐({@link #ALLOWED_DESTINATION})만 허용한다. 브로커 원시
 *       주소, 다른 사용자 주소, 와일드카드 구독은 모두 거부한다.</li>
 *   <li>{@code UNSUBSCRIBE}·{@code DISCONNECT}: 세션 정리 동작이므로 허용한다.</li>
 *   <li>{@code SEND}: 서버에서만 발행하는 알림이므로 클라이언트 발행을 거부한다.</li>
 *   <li>{@code MESSAGE}: 서버→클라이언트 전용 프레임이다. 클라이언트가 보내면 브로커가 그대로
 *       전달해 <b>타인 큐에 알림을 주입</b>할 수 있으므로 반드시 거부한다.</li>
 *   <li>그 밖의 {@code ACK}·{@code NACK}·{@code BEGIN}·{@code COMMIT}·{@code ABORT}와
 *       서버 전용 {@code CONNECTED}·{@code RECEIPT}·{@code ERROR}는 이 애플리케이션에 필요 없으므로
 *       거부한다.</li>
 * </ul>
 *
 * <p>하트비트는 {@code StompCommand}가 없는 빈 프레임이라 명령 검사에 걸리지 않고 그대로 통과한다.</p>
 *
 * <p>거부는 {@link AccessDeniedException}으로 처리해 STOMP ERROR 프레임으로 전달되고, 세션은
 * 닫힌다. 허용된 프레임은 그대로 통과시킨다.</p>
 */
@Slf4j
@Component
public class NotificationInboundInterceptor implements ChannelInterceptor {

    /** 로그인 사용자에게만 열린 개인 알림 구독 주소. */
    public static final String ALLOWED_DESTINATION = "/user/queue/notifications";

    /**
     * 클라이언트가 보낼 수 있는 명령의 최소 집합.
     *
     * <p>{@code SEND}는 여기에 없으므로 거부된다. {@code MESSAGE}·{@code CONNECTED}·{@code ERROR}·
     * {@code RECEIPT}는 서버 전용이라 역시 없다.</p>
     */
    static final Set<StompCommand> ALLOWED_COMMANDS = Set.of(
            StompCommand.CONNECT,
            StompCommand.STOMP,
            StompCommand.SUBSCRIBE,
            StompCommand.UNSUBSCRIBE,
            StompCommand.DISCONNECT);

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        // 하트비트(명령 없는 빈 프레임)는 그대로 통과시킨다. 연결 유지에 필요하다.
        if (command == null) {
            return message;
        }
        if (!ALLOWED_COMMANDS.contains(command)) {
            // 허용 목록 밖이면 어떤 명령도 통과시키지 않는다. MESSAGE 는 타인 큐 주입 경로다.
            log.debug("허용되지 않은 STOMP 명령 거부: command={}", command);
            throw new AccessDeniedException("허용되지 않은 STOMP 명령입니다.");
        }
        switch (command) {
            case CONNECT, STOMP -> verifyConnect(accessor);
            case SUBSCRIBE -> verifySubscribe(accessor);
            default -> {
                // UNSUBSCRIBE·DISCONNECT는 세션 정리 동작이므로 추가 권한이 필요 없다.
            }
        }
        return message;
    }

    private void verifyConnect(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            throw new AccessDeniedException("로그인 후 알림 채널에 연결할 수 있습니다.");
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object expected = attributes == null
                ? null : attributes.get(NotificationHandshakeInterceptor.CSRF_TOKEN_ATTRIBUTE);
        if (!(expected instanceof String expectedToken)) {
            throw new AccessDeniedException("알림 채널의 CSRF 토큰이 없습니다.");
        }
        String supplied = firstNativeHeader(accessor, "X-CSRF-TOKEN");
        if (supplied == null) {
            // 헤더 이름은 서버 설정에 따라 달라질 수 있어 파라미터 이름도 함께 본다.
            supplied = firstNativeHeader(accessor, "_csrf");
        }
        // expectedToken 은 핸드셰이크가 저장소에서 읽은 원본이다. 제출값이 다른 요청에서
        // 마스킹된 값이어도 원본으로 복원해 비교하므로 정상 브라우저가 통과한다.
        if (!StompCsrfTokens.matchesValue(supplied, expectedToken)) {
            throw new AccessDeniedException("알림 채널의 CSRF 토큰이 올바르지 않습니다.");
        }
    }

    private void verifySubscribe(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null || user.getName() == null || user.getName().isBlank()) {
            throw new AccessDeniedException("로그인 후 알림을 구독할 수 있습니다.");
        }
        String destination = accessor.getDestination();
        if (!ALLOWED_DESTINATION.equals(destination)) {
            log.debug("허용되지 않은 알림 구독 거부: destination={}", destination);
            throw new AccessDeniedException("허용되지 않은 알림 구독 주소입니다.");
        }
    }

    private static String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        if (accessor.getNativeHeader(name) == null || accessor.getNativeHeader(name).isEmpty()) {
            return null;
        }
        String value = accessor.getNativeHeader(name).get(0);
        return value == null || value.isBlank() ? null : value;
    }
}
