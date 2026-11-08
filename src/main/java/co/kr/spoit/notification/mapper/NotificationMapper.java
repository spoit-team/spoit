package co.kr.spoit.notification.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.notification.domain.Notification;

/**
 * {@code notification} 저장·조회 매퍼. SQL은 {@code resources/mapper/notification/NotificationMapper.xml}에 있다.
 *
 * <p>읽음·삭제 SQL은 {@code recipient_user_id} 조건을 필수로 받아, 로그인 주체가 소유하지 않은
 * 알림에는 어떤 변경도 적용되지 않는다.</p>
 */
@Mapper
public interface NotificationMapper {

    /** 알림 1건을 저장하고 {@code useGeneratedKeys}로 {@code notificationId}를 돌려준다. */
    int insert(Notification notification);

    /** 수신자의 최근 알림과 모든 미확인 알림을 최신순으로 조회한다. */
    List<Notification> findByRecipient(@Param("recipientUserId") Long recipientUserId,
            @Param("limit") int limit);

    /** 수신자의 읽지 않은 알림 수를 센다. */
    int countUnread(@Param("recipientUserId") Long recipientUserId);

    /** 수신자 소유 알림만 읽음으로 표시한다. 이미 읽은 알림은 시각을 덮어쓰지 않는다. */
    int markRead(@Param("notificationId") Long notificationId,
            @Param("recipientUserId") Long recipientUserId,
            @Param("readAt") LocalDateTime readAt);

    /** 수신자의 읽지 않은 알림을 모두 읽음으로 표시한다. */
    int markAllRead(@Param("recipientUserId") Long recipientUserId,
            @Param("readAt") LocalDateTime readAt);

    /** 수신자 소유 알림 1건을 물리 삭제한다. */
    int delete(@Param("notificationId") Long notificationId,
            @Param("recipientUserId") Long recipientUserId);
}
