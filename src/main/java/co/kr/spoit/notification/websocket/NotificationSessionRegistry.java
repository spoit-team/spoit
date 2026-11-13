package co.kr.spoit.notification.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import co.kr.spoit.member.security.MemberSessionRevokedEvent;

/**
 * 열려 있는 알림 WebSocket/STOMP 세션을 회원 번호로 추적하고, 계정 폐기 사건이 오면 닫는다.
 *
 * <p>HTTP 세션을 무효화해도 <em>이미 수립된 STOMP 연결</em>은 끊기지 않는다. 그래서 계정 제한·역할
 * 변경이 커밋되면 이 레지스트리가 해당 회원의 연결을 직접 닫는다.</p>
 *
 * <p>등록·폐기 경합: 연결 등록과 폐기 사건이 동시에 진행되면, 사건이 먼저 처리된 뒤 등록이 끼어들어
 * 살아남는 연결이 생길 수 있다. 그래서 회원별 {@code generation}을 두고 등록 전후로 값을 비교한다.
 * 등록 도중 세대가 바뀌었으면 그 연결은 폐기 대상이므로 즉시 닫는다.</p>
 *
 * <p>한계: 이 레지스트리는 프로세스 로컬이다. 다중 인스턴스 배포에서는 다른 인스턴스의 연결을 닫지
 * 못하며, 사건 처리 시점에 전송 중인 프레임은 완료될 수 있다.</p>
 */
@Component
public class NotificationSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(NotificationSessionRegistry.class);

    /** 계정 폐기로 닫는 연결의 종료 코드. */
    static final CloseStatus REVOKED_STATUS = CloseStatus.POLICY_VIOLATION;

    private final Map<Long, List<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    /** 회원별 폐기 세대. 값이 바뀌면 그 이전에 시작된 등록은 폐기 대상이다. */
    private final Map<Long, AtomicLong> generationByUser = new ConcurrentHashMap<>();

    /** 해당 회원의 연결을 추적 목록에 추가한다. 등록 도중 폐기가 일어났으면 즉시 닫는다. */
    public void register(Long userId, WebSocketSession session) {
        if (userId == null || userId <= 0 || session == null) {
            return;
        }
        long stamp = generationOf(userId).get();
        this.sessionsByUser.compute(userId, (key, existing) -> {
            List<WebSocketSession> sessions = existing == null ? new ArrayList<>() : existing;
            sessions.add(session);
            return sessions;
        });
        if (generationOf(userId).get() != stamp) {
            // 등록 전에 폐기가 커밋됐다. 폐기 목록에 포함되지 않았을 수 있으므로 직접 닫는다.
            removeFrom(userId, session);
            closeQuietly(session);
        }
    }

    /** 닫힌 연결을 추적 목록에서 제거한다. */
    public void unregister(Long userId, WebSocketSession session) {
        if (userId == null || userId <= 0 || session == null) {
            return;
        }
        this.sessionsByUser.computeIfPresent(userId, (key, existing) -> {
            existing.remove(session);
            return existing.isEmpty() ? null : existing;
        });
    }

    /** 해당 회원의 열린 연결 수. 추적 정보가 없으면 0. */
    public int countOf(Long userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        List<WebSocketSession> sessions = this.sessionsByUser.get(userId);
        return sessions == null ? 0 : sessions.size();
    }

    /** 해당 회원의 모든 연결을 닫고 추적에서 제거한다. */
    public void closeAll(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        generationOf(userId).incrementAndGet();
        List<WebSocketSession> sessions = this.sessionsByUser.remove(userId);
        if (sessions == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            closeQuietly(session);
        }
    }

    /**
     * 계정 제한·역할 변경 커밋 이후 발행되는 사건을 받아 해당 회원의 연결을 닫는다.
     *
     * <p>{@link MemberSessionRevokedEvent}는 커밋 이후에만 발행되므로 롤백된 변경으로 연결이 끊기지
     * 않는다.</p>
     */
    @EventListener
    public void onMemberSessionRevoked(MemberSessionRevokedEvent event) {
        if (event == null) {
            return;
        }
        closeAll(event.userId());
    }

    private AtomicLong generationOf(Long userId) {
        return this.generationByUser.computeIfAbsent(userId, key -> new AtomicLong());
    }

    private void removeFrom(Long userId, WebSocketSession session) {
        List<WebSocketSession> current = this.sessionsByUser.get(userId);
        if (current == null) {
            return;
        }
        List<WebSocketSession> remaining = new ArrayList<>(current);
        remaining.remove(session);
        if (remaining.isEmpty()) {
            this.sessionsByUser.remove(userId, current);
        } else {
            this.sessionsByUser.replace(userId, current, remaining);
        }
    }

    /** 연결을 닫는다. 이미 닫혔거나 IO 오류가 나도 예외를 전파하지 않는다. */
    static void closeQuietly(WebSocketSession session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close(REVOKED_STATUS);
        } catch (IOException | RuntimeException exception) {
            log.debug("알림 웹소켓 세션 종료 실패: sessionId={}", session.getId(), exception);
        }
    }
}
