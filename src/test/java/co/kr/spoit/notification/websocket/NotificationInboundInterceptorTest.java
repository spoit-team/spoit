package co.kr.spoit.notification.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

/**
 * STOMP 인바운드 보호를 검증한다.
 *
 * <p>로그인 주체·CSRF 토큰이 없으면 CONNECT를 거부하고, 개인 큐 외의 구독과 클라이언트 발행도
 * 거부한다. 허용 목록만 통과시키는 fail-closed 동작이 핵심이다.</p>
 */
class NotificationInboundInterceptorTest {

    private static final String CSRF_TOKEN = "csrf-token-value";

    private final NotificationInboundInterceptor interceptor = new NotificationInboundInterceptor();

    @Test
    @DisplayName("로그인 주체와 올바른 CSRF 토큰이 있으면 CONNECT를 허용한다")
    void allowsConnectWithPrincipalAndCsrf() {
        Message<byte[]> message = frame(StompCommand.CONNECT, Map.of("X-CSRF-TOKEN", CSRF_TOKEN),
                new NotificationPrincipal("7"), Map.of(NotificationHandshakeInterceptor.CSRF_TOKEN_ATTRIBUTE,
                        CSRF_TOKEN));

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("로그인 주체가 없는 CONNECT는 거부한다")
    void rejectsAnonymousConnect() {
        Message<byte[]> message = frame(StompCommand.CONNECT, Map.of("X-CSRF-TOKEN", CSRF_TOKEN), null,
                Map.of(NotificationHandshakeInterceptor.CSRF_TOKEN_ATTRIBUTE, CSRF_TOKEN));

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("CSRF 토큰이 없거나 틀린 CONNECT는 거부한다")
    void rejectsConnectWithoutMatchingCsrf() {
        Message<byte[]> missingHeader = frame(StompCommand.CONNECT, Map.of(), new NotificationPrincipal("7"),
                Map.of(NotificationHandshakeInterceptor.CSRF_TOKEN_ATTRIBUTE, CSRF_TOKEN));
        Message<byte[]> wrongToken = frame(StompCommand.CONNECT, Map.of("X-CSRF-TOKEN", "other"),
                new NotificationPrincipal("7"),
                Map.of(NotificationHandshakeInterceptor.CSRF_TOKEN_ATTRIBUTE, CSRF_TOKEN));
        Message<byte[]> noSessionToken = frame(StompCommand.CONNECT, Map.of("X-CSRF-TOKEN", CSRF_TOKEN),
                new NotificationPrincipal("7"), Map.of());

        assertThatThrownBy(() -> interceptor.preSend(missingHeader, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(wrongToken, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(noSessionToken, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("개인 알림 큐 구독은 허용하고 그 밖의 주소는 거부한다")
    void allowsOnlyPersonalQueueSubscription() {
        Message<byte[]> allowed = subscribe(NotificationInboundInterceptor.ALLOWED_DESTINATION,
                new NotificationPrincipal("7"));
        Message<byte[]> brokerQueue = subscribe("/queue/notifications", new NotificationPrincipal("7"));
        Message<byte[]> otherUser = subscribe("/user/8/queue/notifications", new NotificationPrincipal("7"));
        Message<byte[]> wildcard = subscribe("/topic/**", new NotificationPrincipal("7"));

        assertThatCode(() -> interceptor.preSend(allowed, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(brokerQueue, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(otherUser, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(wildcard, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("주체가 없는 구독은 주소와 무관하게 거부한다")
    void rejectsSubscribeWithoutPrincipal() {
        Message<byte[]> message = subscribe(NotificationInboundInterceptor.ALLOWED_DESTINATION, null);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("클라이언트 SEND는 주소와 무관하게 모두 거부한다")
    void rejectsClientSend() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/user/queue/notifications");
        accessor.setUser(new NotificationPrincipal("7"));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StompCommand.class,
            names = {"MESSAGE", "ACK", "NACK", "BEGIN", "COMMIT", "ABORT", "CONNECTED", "RECEIPT", "ERROR"})
    @DisplayName("SEND 외 발행·트랜잭션·서버 전용 명령은 모두 거부한다")
    void rejectsEveryUnlistedCommand(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination("/user/queue/notifications");
        accessor.setUser(new NotificationPrincipal("7"));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // MESSAGE 는 서버→클라이언트 전용이다. 클라이언트가 보내면 브로커가 그대로 전달해
        // 타인 큐에 알림을 주입할 수 있으므로 반드시 막아야 한다.
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .as("command=%s", command)
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("MESSAGE 프레임은 타인 큐 주소여도 거부한다")
    void rejectsMessageInjection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        // 공격자가 피해자 개인 큐를 노린 경우
        accessor.setDestination("/user/8/queue/notifications");
        accessor.setSubscriptionId("forged");
        accessor.setUser(new NotificationPrincipal("7"));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage("{\"title\":\"위조\"}".getBytes(
                java.nio.charset.StandardCharsets.UTF_8), accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = StompCommand.class, names = {"UNSUBSCRIBE", "DISCONNECT"})
    @DisplayName("세션 정리용 UNSUBSCRIBE·DISCONNECT는 허용한다")
    void allowsSessionCleanupCommands(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(new NotificationPrincipal("7"));
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("명령 없는 하트비트 프레임은 그대로 통과한다")
    void allowsHeartbeat() {
        // 하트비트는 StompCommand 가 없는 빈 프레임이다. 연결 유지에 필요하므로 막지 않는다.
        StompHeaderAccessor accessor = StompHeaderAccessor.createForHeartbeat();
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThat(accessor.getCommand()).isNull();
        assertThatCode(() -> interceptor.preSend(message, null)).doesNotThrowAnyException();
    }

    /** 세션 속성까지 포함한 STOMP 프레임을 만든다. */
    private static Message<byte[]> frame(StompCommand command, Map<String, String> headers,
            NotificationPrincipal principal, Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        headers.forEach(accessor::addNativeHeader);
        accessor.setUser(principal);
        Map<String, Object> attributes = new HashMap<>(sessionAttributes);
        accessor.setSessionAttributes(attributes);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> subscribe(String destination, NotificationPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSubscriptionId("sub-0");
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
