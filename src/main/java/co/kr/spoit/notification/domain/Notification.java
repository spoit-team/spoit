package co.kr.spoit.notification.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code notification} 행. DB 알림함에 저장된 단일 알림을 표현한다.
 *
 * <p>수신자({@code recipientUserId})는 항상 서버가 결정하며, 조회·읽음·삭제 SQL은
 * 로그인 주체의 회원 번호와 일치하는 행만 다룬다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    private Long notificationId;
    private Long recipientUserId;
    private Long applicationId;
    private Long matchId;
    private Long joinRequestId;

    /** 대상 동호회 번호. 가입 신청 알림은 신청 행에서도 읽을 수 있다. */
    private Long clubId;

    /** 신고 접수·처리 결과 알림이면 연결할 신고 번호. 그 밖의 알림은 {@code null}이다. */
    private Long reportId;

    /** {@link NotificationType} 이름 또는 도메인별 코드 문자열이다. */
    private String type;
    private String title;
    private String body;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
