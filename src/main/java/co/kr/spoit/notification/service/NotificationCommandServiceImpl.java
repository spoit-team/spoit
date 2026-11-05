package co.kr.spoit.notification.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;

/**
 * 알림 읽음·삭제 명령 구현.
 *
 * <p>모든 변경 SQL이 {@code recipient_user_id} 조건을 필수로 받으므로, 로그인 주체가 소유하지 않은
 * 알림은 변경되지 않는다. 변경 행 수를 돌려주어 호출 측이 성공·무시를 구분할 수 있게 한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationCommandServiceImpl implements NotificationCommandService {

    private final NotificationMapper notificationMapper;

    @Override
    public boolean markRead(Long userId, Long notificationId, LocalDateTime now) {
        if (userId == null || userId <= 0 || notificationId == null || notificationId <= 0) {
            return false;
        }
        return notificationMapper.markRead(notificationId, userId, now) > 0;
    }

    @Override
    public int markAllRead(Long userId, LocalDateTime now) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        return notificationMapper.markAllRead(userId, now);
    }

    @Override
    public boolean delete(Long userId, Long notificationId) {
        if (userId == null || userId <= 0 || notificationId == null || notificationId <= 0) {
            return false;
        }
        return notificationMapper.delete(notificationId, userId) > 0;
    }
}
