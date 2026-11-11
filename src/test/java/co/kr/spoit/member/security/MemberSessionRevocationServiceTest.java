package co.kr.spoit.member.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 폐기는 커밋 이후에만 일어나고 롤백·잘못된 식별자에서는 일어나지 않음을 확인한다. */
class MemberSessionRevocationServiceTest {

    private final MemberSessionRegistry memberSessionRegistry = mock(MemberSessionRegistry.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    private final MemberSessionRevocationService service;

    MemberSessionRevocationServiceTest() {
        beanFactory.registerSingleton("memberSessionRegistry", memberSessionRegistry);
        service = new MemberSessionRevocationService(beanFactory.getBeanProvider(MemberSessionRegistry.class),
                eventPublisher);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋 이후에만 폐기한다")
    void revokesOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        service.revokeAfterCommit(7L);

        verify(memberSessionRegistry, never()).expireSessionsOf(any());
        verify(eventPublisher, never()).publishEvent(any());

        completeSynchronizations(TransactionSynchronization.STATUS_COMMITTED);

        verify(memberSessionRegistry).expireSessionsOf(7L);
        verify(eventPublisher).publishEvent(new MemberSessionRevokedEvent(7L));
    }

    @Test
    @DisplayName("롤백되면 세션·웹소켓을 폐기하지 않는다")
    void doesNotRevokeOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        service.revokeAfterCommit(7L);

        completeSynchronizations(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(memberSessionRegistry, eventPublisher);
    }

    @Test
    @DisplayName("잘못된 회원 번호는 폐기하지 않고, 트랜잭션이 없으면 즉시 폐기한다")
    void handlesInvalidIdAndNoTransaction() {
        service.revokeAfterCommit(null);
        service.revokeAfterCommit(0L);
        verifyNoInteractions(memberSessionRegistry, eventPublisher);

        when(memberSessionRegistry.expireSessionsOf(7L)).thenReturn(1);
        service.revokeAfterCommit(7L);

        verify(memberSessionRegistry).expireSessionsOf(7L);
        verify(eventPublisher).publishEvent(new MemberSessionRevokedEvent(7L));
    }

    private static void completeSynchronizations(int status) {
        for (TransactionSynchronization synchronization : List.copyOf(
                TransactionSynchronizationManager.getSynchronizations())) {
            synchronization.afterCompletion(status);
        }
        TransactionSynchronizationManager.clearSynchronization();
    }
}
