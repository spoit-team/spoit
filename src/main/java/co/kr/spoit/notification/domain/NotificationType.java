package co.kr.spoit.notification.domain;

/** notification.notification_type 값. DB에는 상수 이름 그대로 저장한다. */
public enum NotificationType {
    MATCH_RESULT_CONFLICT,
    MATCH_RESULT_REMINDER,
    MATCH_AUTO_ASSIGNED,
    MATCH_AUTO_CANCELLED
}
