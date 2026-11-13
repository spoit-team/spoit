package co.kr.spoit.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import co.kr.spoit.notification.websocket.NotificationHandshakeHandler;
import co.kr.spoit.notification.websocket.NotificationHandshakeInterceptor;
import co.kr.spoit.notification.websocket.NotificationInboundInterceptor;
import co.kr.spoit.notification.websocket.NotificationSessionRegistry;
import co.kr.spoit.notification.websocket.NotificationSessionTrackingDecorator;

/**
 * 알림 웹소켓(STOMP over native WebSocket) 설정.
 *
 * <p>엔드포인트는 {@code /ws/notifications} 하나이며 SockJS 대체 전송은 열지 않는다. 핸드셰이크는
 * 로그인 주체·계정 상태·같은 출처만 허용하고, 세션 주체를 회원 번호로 고정한다. 브로커는 개인 큐
 * 프리픽스({@code /user})만 쓰고, 인바운드 인터셉터가 구독·발행을 다시 검사한다.</p>
 *
 * <p>구독 주소는 {@code /user/queue/notifications} 하나만 허용하므로 브로커 원시 주소
 * ({@code /queue/**}, {@code /topic/**})로는 구독할 수 없다.</p>
 *
 * <p>수립된 연결은 {@link NotificationSessionTrackingDecorator}가 {@link NotificationSessionRegistry}에
 * 회원 번호로 등록한다. 계정 제한·역할 변경이 커밋되면 그 레지스트리가 연결을 닫는다(HttpSession
 * 무효화만으로는 이미 열린 STOMP 연결이 끊기지 않는다).</p>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final NotificationHandshakeInterceptor handshakeInterceptor;
    private final NotificationInboundInterceptor inboundInterceptor;
    private final NotificationSessionRegistry sessionRegistry;

    public WebSocketConfig(NotificationHandshakeInterceptor handshakeInterceptor,
            NotificationInboundInterceptor inboundInterceptor,
            NotificationSessionRegistry sessionRegistry) {
        this.handshakeInterceptor = handshakeInterceptor;
        this.inboundInterceptor = inboundInterceptor;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // allowedOrigins를 비워 두면 같은 출처 요청만 통과한다(OriginHandshakeInterceptor 기본 동작).
        registry.addEndpoint("/ws/notifications")
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(new NotificationHandshakeHandler());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 서버는 /user/{userId}/queue/notifications로 보내고, 브로커는 변환된 /queue 주소만 안다.
        // 클라이언트의 직접 /queue 구독은 인바운드 인터셉터가 거부한다.
        registry.enableSimpleBroker("/queue");
        registry.setUserDestinationPrefix("/user");
        // 애플리케이션 목적지 프리픽스는 두지 않는다(기본값 없음). 클라이언트 SEND는 인바운드에서 거부된다.
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(inboundInterceptor);
    }

    /**
     * 수립·종료되는 WebSocket 세션을 회원 번호로 추적하도록 핸들러를 감싼다.
     * 커밋된 계정 폐기 사건이 오면 레지스트리가 해당 회원의 연결을 닫는다.
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(
                handler -> new NotificationSessionTrackingDecorator(handler, this.sessionRegistry));
    }
}
