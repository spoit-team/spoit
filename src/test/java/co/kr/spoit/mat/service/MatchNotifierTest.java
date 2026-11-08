package co.kr.spoit.mat.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.kr.spoit.notification.domain.NotificationType;
import co.kr.spoit.notification.service.NotificationDispatcher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class MatchNotifierTest {

    private static final Long MATCH_ID = 10L;

    @Mock
    private ObjectProvider<NotificationDispatcher> provider;

    @Mock
    private NotificationDispatcher dispatcher;

    private MatchNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new MatchNotifier(provider);
    }

    @Test
    void delegatesCommitBoundaryToSharedDispatcher() {
        when(provider.getIfAvailable()).thenReturn(dispatcher);

        notifier.notifyAfterCommit(List.of(1L, 2L), MATCH_ID, NotificationType.MATCH_AUTO_ASSIGNED, "제목", "본문");

        // 알림 저장과 커밋 후 전송을 공통 dispatcher에 위임한다.
        verify(dispatcher).notifyAfterCommit(List.of(1L, 2L), "MATCH_AUTO_ASSIGNED", "제목", "본문",
                MATCH_ID, null, null);
    }

    @Test
    void passesTypeNameToSharedDispatcher() {
        when(provider.getIfAvailable()).thenReturn(dispatcher);

        notifier.notifyAfterCommit(List.of(1L), MATCH_ID, NotificationType.MATCH_RESULT_COMPLETED, "제목", "본문");

        verify(dispatcher).notifyAfterCommit(List.of(1L), "MATCH_RESULT_COMPLETED", "제목", "본문",
                MATCH_ID, null, null);
    }

    @Test
    void skipsWhenNoDispatcherExists() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThatCode(() -> notifier.notifyAfterCommit(List.of(1L), MATCH_ID, NotificationType.MATCH_AUTO_ASSIGNED,
                "제목", "본문")).doesNotThrowAnyException();

        verifyNoInteractions(dispatcher);
    }

    @Test
    void propagatesStorageFailureSoMatchWorkCanRollBack() {
        when(provider.getIfAvailable()).thenReturn(dispatcher);
        doThrow(new IllegalStateException("예약 실패")).when(dispatcher)
                .notifyAfterCommit(any(), any(), any(), any(), any(), isNull(), isNull());

        assertThatThrownBy(() -> notifier.notifyAfterCommit(List.of(1L), MATCH_ID,
                NotificationType.MATCH_RESULT_REMINDER, "제목", "본문"))
                .isInstanceOf(IllegalStateException.class);
    }
}
