package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.kr.spoit.notification.domain.NotificationType;
import co.kr.spoit.notification.service.NotificationService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class MatchNotifierTest {

    private static final Long MATCH_ID = 10L;
    private static final NotificationType TYPE = NotificationType.MATCH_AUTO_ASSIGNED;

    @Mock
    private ObjectProvider<NotificationService> provider;

    @Mock
    private NotificationService notificationService;

    private final FakeTransactionManager transactionManager = new FakeTransactionManager();
    private MatchNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new MatchNotifier(provider, transactionManager);
    }

    @Test
    void sendsAfterCommitInNewTransactionForEachRecipient() {
        when(provider.getIfAvailable()).thenReturn(notificationService);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notifier.notifyAfterCommit(List.of(1L, 2L), MATCH_ID, TYPE, "제목", "본문");
            verifyNoInteractions(notificationService);
        });

        verify(notificationService).notifyMatch(1L, MATCH_ID, TYPE, "제목", "본문");
        verify(notificationService).notifyMatch(2L, MATCH_ID, TYPE, "제목", "본문");
        assertThat(transactionManager.begun).isEqualTo(3);
    }

    @Test
    void doesNotSendWhenTransactionRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            notifier.notifyAfterCommit(List.of(1L), MATCH_ID, TYPE, "제목", "본문");
            status.setRollbackOnly();
        });

        verifyNoInteractions(provider, notificationService);
    }

    @Test
    void continuesWithNextRecipientWhenOneFails() {
        when(provider.getIfAvailable()).thenReturn(notificationService);
        doThrow(new IllegalStateException("저장 실패")).when(notificationService)
                .notifyMatch(eq(1L), any(), any(), any(), any());

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                notifier.notifyAfterCommit(List.of(1L, 2L), MATCH_ID, TYPE, "제목", "본문"));

        verify(notificationService).notifyMatch(2L, MATCH_ID, TYPE, "제목", "본문");
    }

    @Test
    void skipsWhenNoImplementationExists() {
        when(provider.getIfAvailable()).thenReturn(null);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                notifier.notifyAfterCommit(List.of(1L), MATCH_ID, TYPE, "제목", "본문"));

        assertThat(transactionManager.begun).isEqualTo(1);
    }

    @Test
    void sendsImmediatelyWithoutTransaction() {
        when(provider.getIfAvailable()).thenReturn(notificationService);

        notifier.notifyAfterCommit(List.of(1L), MATCH_ID, TYPE, "제목", "본문");

        verify(notificationService).notifyMatch(1L, MATCH_ID, TYPE, "제목", "본문");
    }

    /** DataSourceTransactionManager처럼 커밋 뒤 정리 전까지 트랜잭션이 남아 있어 REQUIRED면 참여하게 된다. */
    private static final class FakeTransactionManager extends AbstractPlatformTransactionManager {

        private int begun;
        private boolean active;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begun++;
            active = true;
        }

        @Override
        protected Object doSuspend(Object transaction) {
            active = false;
            return new Object();
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            active = true;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }

        @Override
        protected void doCleanupAfterCompletion(Object transaction) {
            active = false;
        }
    }
}
