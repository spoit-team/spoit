package co.kr.spoit.mat.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import co.kr.spoit.notification.domain.Notification;
import co.kr.spoit.notification.dto.NotificationItemDto;
import co.kr.spoit.notification.mapper.NotificationMapper;
import co.kr.spoit.notification.service.NotificationQueryServiceImpl;

/**
 * 전용 MySQL에서만 알림 저장·개인별 목록·읽음·삭제의 수신자 격리와 가입 신청 clubId 조인을 검증한다.
 * fixture가 만든 회원 1·2를 사용하며, 필요한 동호회·가입 신청 행은 각 테스트가 직접 INSERT한다.
 * 모든 변경은 테스트 트랜잭션 롤백으로 되돌아간다.
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
class NotificationMapperIntegrationTest extends AbstractMySqlMapperTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 5, 10, 0);

    @Autowired
    private NotificationMapper notificationMapper;

    @Test
    @DisplayName("알림은 수신자별로 저장되고 목록·미읽음 수가 서로 섞이지 않는다")
    void separatesNotificationsByRecipient() {
        Long firstForUser1 = insertNotification(1L, CREATED_AT, null);
        Long secondForUser1 = insertNotification(1L, CREATED_AT.plusMinutes(1), null);
        Long onlyForUser2 = insertNotification(2L, CREATED_AT.plusMinutes(2), null);

        List<Notification> user1Notifications = notificationMapper.findByRecipient(1L, 10);
        assertThat(user1Notifications)
                .extracting(Notification::getNotificationId)
                .containsExactly(secondForUser1, firstForUser1);
        assertThat(user1Notifications).allSatisfy(notification -> {
            assertThat(notification.getRecipientUserId()).isEqualTo(1L);
            assertThat(notification.getType()).isEqualTo("MATCH_APPLICATION");
            assertThat(notification.getTitle()).isEqualTo("알림 제목");
            assertThat(notification.getBody()).isEqualTo("알림 본문");
            assertThat(notification.getReadAt()).isNull();
        });

        assertThat(notificationMapper.findByRecipient(2L, 10))
                .extracting(Notification::getNotificationId)
                .containsExactly(onlyForUser2);
        assertThat(notificationMapper.findByRecipient(1L, 1))
                .extracting(Notification::getNotificationId)
                .containsExactly(secondForUser1, firstForUser1);

        assertThat(notificationMapper.countUnread(1L)).isEqualTo(2);
        assertThat(notificationMapper.countUnread(2L)).isEqualTo(1);
    }

    @Test
    @DisplayName("최근 목록보다 오래된 미확인 알림도 소유자에게 표시한다")
    void includesOldUnreadNotifications() {
        Long oldUnread = insertNotification(1L, CREATED_AT, null);
        Long otherUnread = insertNotification(2L, CREATED_AT, null);
        for (int minute = 1; minute <= 30; minute++) {
            Long readId = insertNotification(1L, CREATED_AT.plusMinutes(minute), null);
            notificationMapper.markRead(readId, 1L, CREATED_AT.plusHours(1));
        }

        var inbox = new NotificationQueryServiceImpl(notificationMapper).findInbox(1L, 30);
        assertThat(inbox.getItems())
                .hasSize(31)
                .extracting(NotificationItemDto::getNotificationId)
                .contains(oldUnread)
                .doesNotContain(otherUnread);
        assertThat(inbox.getUnreadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("타인의 알림 ID로는 읽음 처리할 수 없고 이미 읽은 시각은 유지된다")
    void markReadStaysWithinRecipient() {
        Long mine = insertNotification(1L, CREATED_AT, null);
        Long others = insertNotification(2L, CREATED_AT, null);
        LocalDateTime readAt = CREATED_AT.plusHours(1);

        assertThat(notificationMapper.markRead(others, 1L, readAt)).isZero();
        assertThat(notificationMapper.markRead(mine, 2L, readAt)).isZero();
        assertThat(notificationMapper.countUnread(2L)).isEqualTo(1);

        assertThat(notificationMapper.markRead(mine, 1L, readAt)).isEqualTo(1);
        assertThat(notificationMapper.countUnread(1L)).isZero();

        LocalDateTime laterReadAt = readAt.plusHours(1);
        notificationMapper.markRead(mine, 1L, laterReadAt);
        assertThat(notificationMapper.findByRecipient(1L, 10))
                .singleElement()
                .extracting(Notification::getReadAt)
                .isEqualTo(readAt);
    }

    @Test
    @DisplayName("타인의 알림 ID로는 삭제할 수 없고 소유 알림만 삭제된다")
    void deleteStaysWithinRecipient() {
        Long mine = insertNotification(1L, CREATED_AT, null);
        Long others = insertNotification(2L, CREATED_AT, null);

        assertThat(notificationMapper.delete(others, 1L)).isZero();
        assertThat(notificationMapper.delete(mine, 2L)).isZero();
        assertThat(notificationMapper.findByRecipient(2L, 10))
                .extracting(Notification::getNotificationId)
                .containsExactly(others);

        assertThat(notificationMapper.delete(mine, 1L)).isEqualTo(1);
        assertThat(notificationMapper.findByRecipient(1L, 10)).isEmpty();
    }

    @Test
    @DisplayName("저장된 팀 번호와 기존 가입 신청의 팀 번호를 모두 읽는다")
    void readsClubIdFromLinkedJoinRequest() {
        jdbcTemplate.update(
                "INSERT INTO club (club_id, sport_id, club_name, intro, max_members, "
                        + "activity_days, activity_start_time, activity_end_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                900L, 1L, "MAT 알림 테스트 동호회", "알림 조인 검증용", 10, "2,4", "18:00:00", "20:00:00");
        jdbcTemplate.update(
                "INSERT INTO club_join_request "
                        + "(request_id, club_id, user_id, message, status, requested_at, processed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                950L, 900L, 2L, null, "대기중", CREATED_AT, null);

        Long joinRequestNotification = insertNotification(1L, CREATED_AT, 950L);
        Long plainNotification = insertNotification(1L, CREATED_AT.plusMinutes(1), null);
        Notification transfer = new Notification();
        transfer.setRecipientUserId(1L);
        transfer.setClubId(900L);
        transfer.setType("CLUB_LEADER_TRANSFERRED");
        transfer.setTitle("동호회장으로 위임되었습니다");
        transfer.setBody("MAT 알림 테스트 동호회 팀의 동호회장 자리를 위임받았습니다.");
        transfer.setCreatedAt(CREATED_AT.plusMinutes(2));
        assertThat(notificationMapper.insert(transfer)).isEqualTo(1);

        assertThat(notificationMapper.findByRecipient(1L, 10))
                .extracting(Notification::getNotificationId, Notification::getJoinRequestId,
                        Notification::getClubId)
                .containsExactly(
                        tuple(transfer.getNotificationId(), null, 900L),
                        tuple(plainNotification, null, null),
                        tuple(joinRequestNotification, 950L, 900L));
    }

    private Long insertNotification(Long recipientUserId, LocalDateTime createdAt, Long joinRequestId) {
        Notification notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setJoinRequestId(joinRequestId);
        notification.setType("MATCH_APPLICATION");
        notification.setTitle("알림 제목");
        notification.setBody("알림 본문");
        notification.setCreatedAt(createdAt);

        assertThat(notificationMapper.insert(notification)).isEqualTo(1);
        assertThat(notification.getNotificationId()).isNotNull();
        return notification.getNotificationId();
    }
}
