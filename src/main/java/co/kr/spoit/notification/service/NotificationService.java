package co.kr.spoit.notification.service;

import co.kr.spoit.notification.domain.NotificationType;

/**
 * 경기 알림 발송 계약. 구현체가 없으면 호출되지 않는다.
 *
 * <p>mat의 MatchNotifier가 경기 처리 커밋 뒤에 새 트랜잭션을 열고 수신자마다 호출하므로,
 * 구현체는 트랜잭션 전파를 따로 지정하지 않아도 저장이 유실되지 않는다.</p>
 */
public interface NotificationService {

    void notifyMatch(Long recipientUserId, Long matchId, NotificationType type, String title, String body);
}
