package co.kr.spoit.notification.service;

import java.util.List;

import co.kr.spoit.notification.domain.NotificationType;

/**
 * 알림 저장 및 커밋 후 웹소켓 전송 계약.
 */
public interface NotificationService {

    void notifyMatch(Long recipientUserId, Long matchId, NotificationType type, String title, String body);

    /**
     * 호출한 트랜잭션에 알림을 저장하고, 커밋된 뒤 웹소켓으로 보낸다.
     */
    void notifyRecipients(List<Long> recipientUserIds, String type, String title, String body,
            Long matchId, Long applicationId, Long joinRequestId, Long clubId);
}
