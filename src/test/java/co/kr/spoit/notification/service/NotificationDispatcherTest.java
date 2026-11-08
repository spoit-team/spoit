package co.kr.spoit.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.kr.spoit.notification.mapper.NotificationMapper;

class NotificationDispatcherTest {

    private static final String TYPE = "CLUB_JOIN_REQUESTED";

    private final DataSource dataSource = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);
    private final NotificationMapper mapper = mock(NotificationMapper.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final NotificationDispatcher dispatcher = new NotificationDispatcher(
            new NotificationServiceImpl(mapper, sender));

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection)
                .thenThrow(new SQLException("추가 커넥션을 사용할 수 없습니다."));
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    @DisplayName("수신자 여러 명의 알림을 업무 커넥션으로 저장하고 커밋한 뒤 전송한다")
    void storesWithOneConnectionAndSendsAfterCommit() throws SQLException {
        transaction.executeWithoutResult(status -> {
            dispatcher.notifyAfterCommit(Arrays.asList(7L, 7L, null, 0L, -1L, 8L), TYPE,
                    "제목", "본문", null, null, 3L);
            verifyNoInteractions(sender);
        });

        var order = inOrder(mapper, connection, sender);
        order.verify(mapper).insert(argThat(n -> n.getRecipientUserId().equals(7L)));
        order.verify(mapper).insert(argThat(n -> n.getRecipientUserId().equals(8L)));
        order.verify(connection).commit();
        order.verify(sender).send(eq(7L), any());
        order.verify(sender).send(eq(8L), any());
        verify(dataSource).getConnection();
    }

    @Test
    @DisplayName("업무를 롤백하면 알림을 전송하지 않는다")
    void skipsSendingOnRollback() throws SQLException {
        transaction.executeWithoutResult(status -> {
            dispatcher.notifyAfterCommit(List.of(7L), TYPE, "제목", "본문", null, null, 3L);
            status.setRollbackOnly();
        });

        var order = inOrder(mapper, connection);
        order.verify(mapper).insert(any());
        order.verify(connection).rollback();
        verifyNoInteractions(sender);
        verify(dataSource).getConnection();
    }

    @Test
    @DisplayName("유효한 수신자나 종류가 없으면 알림을 저장하지 않는다")
    void skipsWithoutRecipientOrType() {
        dispatcher.notifyAfterCommit(List.of(), TYPE, "제목", "본문", null, null, 3L);
        dispatcher.notifyAfterCommit(null, TYPE, "제목", "본문", null, null, 3L);
        dispatcher.notifyAfterCommit(List.of(7L), " ", "제목", "본문", null, null, 3L);

        verifyNoInteractions(mapper, sender);
    }
}
