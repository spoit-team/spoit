package co.kr.spoit.notification.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import co.kr.spoit.notification.domain.Notification;
import co.kr.spoit.notification.domain.NotificationType;
import co.kr.spoit.notification.dto.NotificationItemDto;
import co.kr.spoit.notification.mapper.NotificationMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 알림 저장 구현. 웹소켓 전송은 저장 트랜잭션이 커밋된 뒤에만 예약한다.
 *
 * <p>호출 측 트랜잭션에 참여해 저장하고, {@code afterCommit} 콜백에서
 * {@link NotificationSender}로 보낸다. 저장이 롤백되면 콜백이 실행되지 않으므로 전송도 없다.
 * 전송 실패는 저장 결과에 영향을 주지 않는다.</p>
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationSender notificationSender;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
            NotificationSender notificationSender) {
        this.notificationMapper = notificationMapper;
        this.notificationSender = notificationSender;
    }

    /** 기존 경기 알림 경로. 수신자 1명에 대한 저장·전송을 한 번에 처리한다. */
    @Override
    @Transactional
    public void notifyMatch(Long recipientUserId, Long matchId, NotificationType type, String title, String body) {
        if (recipientUserId == null || recipientUserId <= 0 || type == null) {
            return;
        }
        store(recipientUserId, type.name(), title, body, matchId, null, null, null, null);
    }

    @Override
    @Transactional
    public void notifyRecipients(List<Long> recipientUserIds, String type, String title, String body,
            Long matchId, Long applicationId, Long joinRequestId, Long clubId) {
        notifyRecipients(recipientUserIds, type, title, body, matchId, applicationId, joinRequestId, clubId, null);
    }

    @Override
    @Transactional
    public void notifyRecipients(List<Long> recipientUserIds, String type, String title, String body,
            Long matchId, Long applicationId, Long joinRequestId, Long clubId, Long reportId) {
        if (recipientUserIds == null || type == null || type.isBlank()) {
            return;
        }
        for (Long recipientUserId : recipientUserIds) {
            if (recipientUserId == null || recipientUserId <= 0) {
                continue;
            }
            store(recipientUserId, type, title, body, matchId, applicationId, joinRequestId, clubId, reportId);
        }
    }

    /**
     * 알림 1건을 저장하고, 이 트랜잭션이 커밋된 뒤에만 전송을 예약한다.
     * 저장 트랜잭션이 없으면(동기화 비활성) 즉시 전송하지 않고 저장만 한다.
     */
    private void store(Long recipientUserId, String type, String title, String body,
            Long matchId, Long applicationId, Long joinRequestId, Long clubId, Long reportId) {
        Notification notification = new Notification();
        notification.setRecipientUserId(recipientUserId);
        notification.setType(type);
        // 스키마의 title은 100자, body는 TEXT다. 개인정보를 새로 넣지 않고 호출 측 문구만 쓴다.
        notification.setTitle(clip(title, 100));
        notification.setBody(body == null ? "" : body);
        notification.setMatchId(matchId);
        notification.setApplicationId(applicationId);
        notification.setJoinRequestId(joinRequestId);
        notification.setClubId(clubId);
        notification.setReportId(reportId);
        // DB 기본값에만 맡기면 커밋 전 실시간 payload의 시간이 비므로, 저장 시각을 명시한다.
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        NotificationItemDto item = NotificationItemDto.from(notification);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notificationSender.send(recipientUserId, item);
                } catch (RuntimeException e) {
                    log.warn("알림 전송 실패: recipientUserId={}, type={}", recipientUserId, type, e);
                }
            }
        });
    }

    private static String clip(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
