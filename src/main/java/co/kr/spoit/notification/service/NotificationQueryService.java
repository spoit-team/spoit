package co.kr.spoit.notification.service;

import java.util.List;

import co.kr.spoit.notification.dto.NotificationItemDto;
import co.kr.spoit.notification.dto.NotificationListResponse;

/**
 * 알림함 조회 계약. 모든 조회는 로그인 주체의 회원 번호로만 수행한다.
 *
 * <p>컨트롤러·공통 모델 어드바이스는 이 계약으로만 알림을 읽고, 저장·전송은
 * {@link NotificationDispatcher}·{@link NotificationService}가 담당한다.</p>
 */
public interface NotificationQueryService {

    /** 수신자의 최근 {@code limit}건과 모든 미확인 알림을 최신순 화면 모델로 바꾼다. */
    List<NotificationItemDto> findForView(Long userId, int limit);

    /** 같은 조회 결과로 목록과 미확인 수를 구성한다. */
    NotificationListResponse findInbox(Long userId, int limit);

    /** 수신자의 읽지 않은 알림 수. 로그인하지 않은 경우 0. */
    int countUnread(Long userId);
}
