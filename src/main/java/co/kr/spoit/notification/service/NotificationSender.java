package co.kr.spoit.notification.service;

import co.kr.spoit.notification.dto.NotificationItemDto;

/**
 * 저장된 알림 1건을 로그인 사용자에게 실시간으로 전달하는 계약이다.
 *
 * <p>구현체는 전송 실패를 예외로 올리지 않는다. 전송은 DB 저장 이후의 부가 동작이며,
 * 실패해도 알림 목록 조회로 복구할 수 있어야 한다.</p>
 */
public interface NotificationSender {

    void send(Long recipientUserId, NotificationItemDto notification);
}
