package co.kr.spoit.notification.dto;

import java.util.List;

import lombok.Getter;

/**
 * {@code GET /api/notifications} 응답.
 *
 * <p>알림 목록과 읽지 않은 수를 한 번에 돌려주어, 접속·재접속 시 클라이언트가 한 번의 조회로
 * 목록과 배지를 복구할 수 있게 한다.</p>
 */
@Getter
public final class NotificationListResponse {

    private final List<NotificationItemDto> items;
    private final int unreadCount;

    private NotificationListResponse(List<NotificationItemDto> items, int unreadCount) {
        this.items = items;
        this.unreadCount = unreadCount;
    }

    public static NotificationListResponse of(List<NotificationItemDto> items, int unreadCount) {
        return new NotificationListResponse(items == null ? List.of() : items, Math.max(unreadCount, 0));
    }
}
