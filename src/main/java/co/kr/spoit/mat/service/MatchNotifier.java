package co.kr.spoit.mat.service;

import co.kr.spoit.notification.domain.NotificationType;
import co.kr.spoit.notification.service.NotificationService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 경기 처리 트랜잭션이 커밋된 뒤 알림을 보낸다. 롤백된 처리의 알림은 나가지 않고,
 * 알림 실패는 로그만 남겨 이미 커밋된 경기 처리에 영향을 주지 않는다.
 */
@Slf4j
@Component
public class MatchNotifier {

    private final ObjectProvider<NotificationService> notificationService;
    private final TransactionTemplate newTransaction;

    public MatchNotifier(ObjectProvider<NotificationService> notificationService,
                         PlatformTransactionManager transactionManager) {
        this.notificationService = notificationService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void notifyAfterCommit(List<Long> recipientUserIds, Long matchId, NotificationType type,
                                  String title, String body) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(recipientUserIds, matchId, type, title, body);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(recipientUserIds, matchId, type, title, body);
            }
        });
    }

    // afterCommit 시점에는 끝난 트랜잭션이 스레드에 남아 있어, 새 트랜잭션을 열지 않으면 구현체의 저장이 유실된다.
    private void send(List<Long> recipientUserIds, Long matchId, NotificationType type, String title, String body) {
        NotificationService service = notificationService.getIfAvailable();
        if (service == null) {
            return;
        }
        for (Long recipientUserId : recipientUserIds) {
            try {
                newTransaction.executeWithoutResult(status ->
                        service.notifyMatch(recipientUserId, matchId, type, title, body));
            } catch (RuntimeException e) {
                log.warn("경기 알림 실패: matchId={}, recipientUserId={}, type={}", matchId, recipientUserId, type, e);
            }
        }
    }
}
