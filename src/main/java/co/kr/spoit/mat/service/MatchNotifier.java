package co.kr.spoit.mat.service;

import co.kr.spoit.notification.domain.NotificationType;
import co.kr.spoit.notification.service.NotificationDispatcher;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 경기 알림을 현재 트랜잭션에 저장하고 커밋 뒤 전송하도록 위임한다.
 */
@Component
public class MatchNotifier {

    private final ObjectProvider<NotificationDispatcher> dispatcher;

    public MatchNotifier(ObjectProvider<NotificationDispatcher> dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void notifyAfterCommit(List<Long> recipientUserIds, Long matchId, NotificationType type,
                                  String title, String body) {
        NotificationDispatcher available = dispatcher.getIfAvailable();
        if (available == null) {
            return;
        }
        available.notifyAfterCommit(recipientUserIds, type.name(), title, body, matchId, null, null);
    }
}
