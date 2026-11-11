package co.kr.spoit.notification.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
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

    @Test
    @DisplayName("등록 도중 폐기 세대가 바뀌면 그 연결은 추적하지 않고 즉시 닫는다")
    void closesConnectionWhoseRegistrationCrossedARevocation() throws Exception {
        // register() 는 세대를 읽어 두고(stamp) 목록에 넣은 뒤 세대를 다시 읽는다. 폐기 커밋이 그
        // 사이에 끼어든 상황을 결정적으로 만들기 위해, 목록에 추가되는 순간 세대가 오르도록 심는다.
        recordRevocationInFlight(registry, 7L);
        WebSocketSession session = session("s1");

        registry.register(7L, session);

        verify(session).close(NotificationSessionRegistry.REVOKED_STATUS);
        assertThat(registry.countOf(7L)).isZero();
    }

    @Test
    @DisplayName("폐기는 해당 회원의 모든 연결을 닫고 추적을 비운다")
    void closeAllClosesEveryConnectionOfTheMember() throws Exception {
        WebSocketSession first = session("s1");
        WebSocketSession second = session("s2");
        WebSocketSession other = session("s3");
        registry.register(7L, first);
        registry.register(7L, second);
        registry.register(8L, other);

        registry.closeAll(7L);

        verify(first).close(NotificationSessionRegistry.REVOKED_STATUS);
        verify(second).close(NotificationSessionRegistry.REVOKED_STATUS);
        verify(other, never()).close(any(CloseStatus.class));
        assertThat(registry.countOf(7L)).isZero();
        assertThat(registry.countOf(8L)).isEqualTo(1);
    }

    @Test
    @DisplayName("폐기 이후 새로 맺은 연결은 세대가 이미 올라가 있어 닫지 않는다")
    void keepsConnectionEstablishedAfterRevocation() throws Exception {
        registry.register(7L, session("old"));
        registry.closeAll(7L);

        WebSocketSession reconnected = session("new");
        registry.register(7L, reconnected);

        verify(reconnected, never()).close(any(CloseStatus.class));
        assertThat(registry.countOf(7L)).isEqualTo(1);
    }

    /**
     * 등록이 추적 목록에 세션을 추가하는 순간 회원의 폐기 세대를 올린다.
     *
     * <p>{@code register}는 세대를 읽어 두고 목록에 넣은 뒤 세대를 다시 읽는다. 폐기 커밋이 그 사이에
     * 끼어든 순간을 스레드 경합 없이 재현하기 위한 테스트 장치로, 목록과 세대를 미리 심어 둔다.</p>
     */
    @SuppressWarnings("unchecked")
    private static void recordRevocationInFlight(NotificationSessionRegistry registry, Long userId) {
        Map<Long, AtomicLong> generations =
                (Map<Long, AtomicLong>) ReflectionTestUtils.getField(registry, "generationByUser");
        Map<Long, List<WebSocketSession>> sessionsByUser =
                (Map<Long, List<WebSocketSession>>) ReflectionTestUtils.getField(registry, "sessionsByUser");
        assertThat(generations).isNotNull();
        assertThat(sessionsByUser).isNotNull();

        AtomicLong generation = new AtomicLong();
        generations.put(userId, generation);
        sessionsByUser.put(userId, new ArrayList<>() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean add(WebSocketSession session) {
                boolean added = super.add(session);
                // 등록 도중 폐기 커밋이 처리된 것과 같은 효과를 낸다.
                generation.incrementAndGet();
                return added;
            }
        });
    }

    private static WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
