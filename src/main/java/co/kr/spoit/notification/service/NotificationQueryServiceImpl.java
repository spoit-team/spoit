package co.kr.spoit.notification.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.notification.dto.NotificationItemDto;
import co.kr.spoit.notification.dto.NotificationListResponse;
import co.kr.spoit.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;

/** 알림함 조회 구현. 소유권 조건은 매퍼 SQL이 강제한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryServiceImpl implements NotificationQueryService {

    /** 미확인 알림 외에 조회할 최근 알림 수의 상한이다. */
    static final int MAX_LIMIT = 50;

    private final NotificationMapper notificationMapper;

    @Override
    public List<NotificationItemDto> findForView(Long userId, int limit) {
        if (userId == null || userId <= 0 || limit <= 0) {
            return List.of();
        }
        return NotificationItemDto.fromAll(
                notificationMapper.findByRecipient(userId, Math.min(limit, MAX_LIMIT)));
    }

    @Override
    public NotificationListResponse findInbox(Long userId, int limit) {
        List<NotificationItemDto> items = findForView(userId, limit);
        int unreadCount = (int) items.stream().filter(item -> !item.isRead()).count();
        return NotificationListResponse.of(items, unreadCount);
    }

    @Override
    public int countUnread(Long userId) {
        if (userId == null || userId <= 0) {
            return 0;
        }
        return notificationMapper.countUnread(userId);
    }
}
