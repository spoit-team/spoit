package co.kr.spoit.notification.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import co.kr.spoit.notification.dto.NotificationItemDto;
import co.kr.spoit.notification.service.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 저장된 알림을 사용자 개인 큐로 보낸다.
 *
 * <p>목적지는 {@code /user/{userId}/queue/notifications}이며, 세션 주체 이름을 회원 번호로
 * 고정했으므로 같은 사용자의 모든 활성 세션이 함께 받는다. 접속하지 않은 사용자에게는 아무 일도
 * 일어나지 않고, 그 알림은 다음 목록 조회에서 확인된다.</p>
 *
 * <p>전송 실패는 로그만 남긴다. 알림은 이미 DB에 커밋되어 조회로 복구할 수 있으므로
 * 업무나 저장 결과를 되돌리지 않는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompNotificationSender implements NotificationSender {

    /** 개인 큐 주소. 클라이언트가 구독하는 주소와 같아야 한다. */
    static final String USER_DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void send(Long recipientUserId, NotificationItemDto notification) {
        if (recipientUserId == null || recipientUserId <= 0 || notification == null) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(recipientUserId), USER_DESTINATION, notification);
        } catch (RuntimeException exception) {
            log.warn("알림 웹소켓 전송 실패: recipientUserId={}, type={}",
                    recipientUserId, notification.getType(), exception);
        }
    }
}
