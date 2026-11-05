package co.kr.spoit.notification.service;

import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 알림을 업무와 같은 트랜잭션에 저장한다. 웹소켓 전송은 저장이 커밋된 뒤에 실행한다.
 */
@Component
public class NotificationDispatcher {

    private final NotificationService notificationService;

    public NotificationDispatcher(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 수신자 목록을 정리해 알림을 저장하고, 커밋 이후 전송을 예약한다.
     */
    public void notifyAfterCommit(List<Long> recipients, String type, String title, String body,
            Long matchId, Long applicationId, Long joinRequestId) {
        notifyAfterCommit(recipients, type, title, body, matchId, applicationId, joinRequestId, null);
    }

    public void notifyAfterCommit(List<Long> recipients, String type, String title, String body,
            Long matchId, Long applicationId, Long joinRequestId, Long clubId) {
        List<Long> targets = normalize(recipients);
        if (targets.isEmpty() || type == null || type.isBlank()) {
            return;
        }
        notificationService.notifyRecipients(targets, type, title, body, matchId, applicationId, joinRequestId, clubId);
    }

    /** 호출 측 목록을 건드리지 않도록 복사한 뒤 순서를 유지하며 중복을 제거한다. */
    private static List<Long> normalize(List<Long> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long recipient : recipients) {
            if (recipient != null && recipient > 0) {
                unique.add(recipient);
            }
        }
        return List.copyOf(unique);
    }
}
