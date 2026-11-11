package co.kr.spoit.member.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 계정 제한·역할 변경이 <strong>커밋된 뒤</strong> 그 회원의 활성 인증 수단을 폐기한다.
 *
 * <p>폐기 대상:</p>
 * <ul>
 *   <li>HTTP 세션: {@link MemberSessionRegistry}에 등록된 모든 세션을 만료 표시한다. 실제 거부는 다음
 *       요청에서 {@link MemberSessionRevocationFilter}가 담당한다.</li>
 *   <li>알림 STOMP/WebSocket 세션: {@link MemberSessionRevokedEvent}를 발행해 닫는다. 이미 열린
 *       STOMP 연결은 HttpSession 무효화로 끊기지 않기 때문에 별도로 닫아야 한다.</li>
 * </ul>
 *
 * <p>호출 규약: 쓰기 트랜잭션 안에서 호출하면 커밋 이후로 미룬다. 롤백되면 아무 것도 폐기하지 않는다
 * (DB가 되돌아갔는데 세션만 끊기는 일을 막는다). 트랜잭션이 없으면 즉시 폐기한다.</p>
 *
 * <p>한계: 이 추적은 프로세스 로컬이다. 다중 인스턴스 배포에서는 다른 인스턴스가 들고 있는 세션·연결을
 * 폐기하지 못하며, 폐기 시점에 이미 필터를 지난 요청이나 전송 중인 STOMP 프레임은 완료될 수 있다.</p>
 */
@Service
public class MemberSessionRevocationService {

    private static final Logger log = LoggerFactory.getLogger(MemberSessionRevocationService.class);

    private final ObjectProvider<MemberSessionRegistry> memberSessionRegistry;
    private final ApplicationEventPublisher eventPublisher;

    public MemberSessionRevocationService(ObjectProvider<MemberSessionRegistry> memberSessionRegistry,
            ApplicationEventPublisher eventPublisher) {
        this.memberSessionRegistry = memberSessionRegistry;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 해당 회원의 활성 HTTP 세션·알림 웹소켓 연결을 폐기한다.
     *
     * <p>트랜잭션 안에서 호출하면 커밋 이후에 수행하고, 롤백이면 수행하지 않는다. 트랜잭션이 없으면
     * 즉시 수행한다. 회원 번호가 null·비양수면 아무 것도 하지 않는다.</p>
     *
     * @param userId 폐기 대상 회원 번호
     */
    public void revokeAfterCommit(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        revokeNow(userId);
                    }
                }
            });
            return;
        }
        revokeNow(userId);
    }

    /** 즉시 폐기한다. 커밋 여부를 호출자가 이미 확정한 경우에만 쓴다. */
    private void revokeNow(Long userId) {
        int expiredSessions = this.memberSessionRegistry.getObject().expireSessionsOf(userId);
        this.eventPublisher.publishEvent(new MemberSessionRevokedEvent(userId));
        log.info("회원 인증 수단 폐기: userId={}, 만료 표시한 HTTP 세션 수={}", userId, expiredSessions);
    }
}
