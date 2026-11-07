package co.kr.spoit.notification.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import co.kr.spoit.notification.domain.Notification;
import co.kr.spoit.notification.domain.NotificationType;
import lombok.Getter;

/**
 * 화면·JSON·웹소켓이 공유하는 알림 표시 모델이다.
 *
 * <p>{@code time}은 서버에서 완성한 표시 문자열이고, {@code href}는 서버가 정한 내부 경로만 담는다.
 * 사용자 입력이나 DB의 임의 문자열을 경로로 쓰지 않으므로 외부로 나가는 링크가 될 수 없다.</p>
 *
 * <p>이동 경로는 알림 종류로 먼저 결정한다. 같은 대상 번호라도 수신자의 권한이 다르기 때문에,
 * 번호만 보고 관리 화면으로 보내면 권한 없는 사용자를 막다른 화면으로 데려간다.</p>
 */
@Getter
public final class NotificationItemDto {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private static final String MYPAGE_NOTIFICATIONS = "/mypage#notifications";
    private static final String MYPAGE_RESERVATIONS = "/mypage#reservations";

    /** 경기 결과 화면(참가자·팀장). JSON 요약 API {@code /result/summary}와 다른 화면 경로다. */
    private static final String MATCH_RESULT_FORM = "/mypage/matches/";
    private static final String MATCH_RESULT_FORM_SUFFIX = "/result-form";
    /** 참가자용 매칭 상세. */
    private static final String MATCH_DETAIL = "/matches/";
    /** 담당 시설 관리자의 매칭방 신청 관리 화면. */
    private static final String ADMIN_MATCH_ROOM = "/admin/operations?matchId=";
    /** 동호회장의 팀 관리 화면과 가입 신청 탭. */
    private static final String CLUB_MANAGE = "/teams/manage";
    private static final String CLUB_MANAGE_APPLICATIONS = "/teams/manage?tab=applications";
    /** 관리 권한이 없는 사용자도 열 수 있는 동호회 목록. */
    private static final String CLUB_LIST = "/teams";
    /** 동호회 상세. 승인·거절된 신청자도 열 수 있는 공개 화면이다. */
    private static final String CLUB_DETAIL = "/teams/";

    private final Long notificationId;
    private final boolean read;
    private final String type;
    private final String title;
    private final String body;
    private final String time;
    private final String href;

    /**
     * 알림이 가리키는 동호회 번호. 알 수 없으면 {@code null}이다.
     */
    private final Long clubId;

    private NotificationItemDto(Long notificationId, boolean read, String type, String title,
            String body, String time, String href, Long clubId) {
        this.notificationId = notificationId;
        this.read = read;
        this.type = type;
        this.title = title;
        this.body = body;
        this.time = time;
        this.href = href;
        this.clubId = clubId;
    }

    /** 저장된 알림 목록을 표시 모델 목록으로 바꾼다. null 목록은 빈 목록이 된다. */
    public static List<NotificationItemDto> fromAll(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return List.of();
        }
        return notifications.stream().map(NotificationItemDto::from).toList();
    }

    /** 저장된 알림 행을 표시 모델로 바꾼다. */
    public static NotificationItemDto from(Notification notification) {
        return new NotificationItemDto(
                notification.getNotificationId(),
                notification.getReadAt() != null,
                NotificationType.labelOf(notification.getType()),
                nullToEmpty(notification.getTitle()),
                nullToEmpty(notification.getBody()),
                formatTime(notification.getCreatedAt()),
                hrefOf(notification),
                validId(notification.getClubId()));
    }

    /**
     * 알림이 안내하는 화면 경로를 정한다.
     *
     * <p>종류를 먼저 보고, 그 종류의 수신자가 실제로 접근할 수 있는 화면으로 보낸다.
     * 대상 번호가 없거나 알 수 없는 코드면 알림함으로 돌려보내 존재하지 않는 화면으로 이동하지 않게 한다.</p>
     */
    private static String hrefOf(Notification notification) {
        NotificationType kind = typeOf(notification.getType());
        Long matchId = notification.getMatchId();
        boolean hasMatch = matchId != null && matchId > 0;
        Long clubId = validId(notification.getClubId());
        boolean hasClub = clubId != null;

        if (kind == null) {
            return fallbackHref(notification, hasMatch);
        }
        return switch (kind) {
            // 결과 알림은 모두 결과 화면으로 보낸다(확정 참가자·양 팀장·미제출 팀장).
            case MATCH_RESULT_CONFLICT, MATCH_RESULT_REMINDER, MATCH_RESULT_COMPLETED ->
                    hasMatch ? MATCH_RESULT_FORM + matchId + MATCH_RESULT_FORM_SUFFIX : MYPAGE_NOTIFICATIONS;
            // 신청 접수·취소의 수신자는 담당 시설 관리자이므로 신청 관리 화면으로 보낸다.
            case MATCH_APPLICATION_RECEIVED, MATCH_APPLICATION_CANCELLED ->
                    hasMatch ? ADMIN_MATCH_ROOM + matchId : MYPAGE_NOTIFICATIONS;
            // 그 밖의 매칭 알림 수신자는 참가자·신청자이므로 사용자용 매칭 상세로 보낸다.
            case MATCH_APPLICATION_CONFIRMED, MATCH_AUTO_ASSIGNED, MATCH_AUTO_CANCELLED, MATCH_ROOM_CANCELLED ->
                    hasMatch ? MATCH_DETAIL + matchId : MYPAGE_NOTIFICATIONS;
            // 가입 신청 접수는 동호회장만 받는다. 동호회장이 여러 팀이면 관리 화면이 첫 팀만 열므로,
            // 신청이 접수된 팀을 clubId로 지정해야 잘못된 팀의 신청 목록을 보여 주지 않는다.
            case CLUB_JOIN_REQUESTED -> hasClub
                    ? CLUB_MANAGE + "?clubId=" + clubId + "&tab=applications"
                    : CLUB_MANAGE_APPLICATIONS;
            case CLUB_JOIN_APPROVED -> hasClub ? CLUB_DETAIL + clubId : CLUB_MANAGE;
            case CLUB_LEADER_TRANSFERRED -> hasClub ? CLUB_MANAGE + "?clubId=" + clubId + "&tab=members" : CLUB_LIST;
            // 거절된 신청자·추방된 팀원은 더 이상 팀 관리 권한이 없으므로 상세(없으면 목록)로 보낸다.
            case CLUB_JOIN_REJECTED, CLUB_MEMBER_KICKED -> hasClub ? CLUB_DETAIL + clubId : CLUB_LIST;
        };
    }

    /**
     * 정의되지 않은 코드의 이동 경로. 대상을 추측해 관리 화면으로 보내지 않는다.
     *
     * <p>{@code join_request_id}만으로 팀 관리 화면에 보내면 동호회장이 아닌 수신자가 권한 없는
     * 화면을 만나게 되므로, 알 수 없는 클럽 알림은 동호회 목록으로 둔다.</p>
     */
    private static String fallbackHref(Notification notification, boolean hasMatch) {
        if (hasMatch) {
            return MATCH_DETAIL + notification.getMatchId();
        }
        if (notification.getApplicationId() != null) {
            return MYPAGE_RESERVATIONS;
        }
        if (notification.getJoinRequestId() != null) {
            return CLUB_LIST;
        }
        return MYPAGE_NOTIFICATIONS;
    }

    /** 저장된 코드 문자열을 열거형으로 해석한다. 정의되지 않은 코드는 {@code null}. */
    private static NotificationType typeOf(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return null;
        }
        for (NotificationType type : NotificationType.values()) {
            if (type.name().equals(rawType)) {
                return type;
            }
        }
        return null;
    }

    /** 화면 경로에 쓸 수 있는 번호만 통과시킨다. null·0·음수는 없는 대상으로 본다. */
    private static Long validId(Long id) {
        return id != null && id > 0 ? id : null;
    }

    private static String formatTime(LocalDateTime createdAt) {
        return createdAt == null ? "" : createdAt.format(TIME_FORMAT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
