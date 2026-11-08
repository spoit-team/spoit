package co.kr.spoit.notification.service;

import java.time.LocalDateTime;

/**
 * 알림 읽음·삭제 계약.
 *
 * <p>소유권은 로그인 주체의 회원 번호로 SQL에서 강제한다. 존재하지 않거나 다른 사용자의 알림을
 * 지정해도 예외를 던져 존재 여부를 흘리지 않고, 변경이 없었다는 사실만 돌려준다.</p>
 */
public interface NotificationCommandService {

    /** 소유한 알림 1건을 읽음으로 표시한다. 이미 읽었거나 소유하지 않았으면 false. */
    boolean markRead(Long userId, Long notificationId, LocalDateTime now);

    /** 소유한 읽지 않은 알림을 모두 읽음으로 표시하고 변경 건수를 돌려준다. */
    int markAllRead(Long userId, LocalDateTime now);

    /** 소유한 알림 1건을 물리 삭제한다. 소유하지 않았으면 false. */
    boolean delete(Long userId, Long notificationId);
}
