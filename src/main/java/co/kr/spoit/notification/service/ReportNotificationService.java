package co.kr.spoit.notification.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import co.kr.spoit.member.domain.MemberRole;
import co.kr.spoit.notification.domain.NotificationType;
import co.kr.spoit.notification.dto.SystemAdminCandidate;
import co.kr.spoit.notification.mapper.NotificationMapper;

/**
 * 신고 접수·처리 결과 알림을 만드는 컴포넌트다.
 *
 * <p>저장·전송 경계는 기존 {@link NotificationDispatcher}에 그대로 맡긴다. 이 클래스는 수신자와
 * 안전한 고정 문구만 결정하고, 호출한 업무 트랜잭션에 저장이 참여하도록 위임한다. 따라서 신고
 * 저장이 롤백되면 알림도 남지 않고, 전송은 커밋 이후에만 예약된다.</p>
 *
 * <p>접수 알림은 탈퇴하지 않은 실제 시스템 관리자에게만 보낸다. DB 후보 조회 결과를
 * {@link MemberRole#from(String)}의 정확 일치 규칙으로 다시 판정하므로, {@code ROLE_} 접두사·
 * 공백·대소문자만 보정되고 부분 일치나 알 수 없는 값은 관리자로 승격되지 않는다.</p>
 *
 * <p>제목·본문에는 신고 원문·대상 회원·신고 사유 같은 민감정보를 넣지 않는다. 알림은 처리 화면으로
 * 이동하는 신호일 뿐이며, 권한별 접근은 이동한 서버 화면이 다시 검사한다.</p>
 */
@Component
public class ReportNotificationService {

    /** 접수 알림의 제목·본문. 민감정보 없이 확인 동작만 안내한다. */
    private static final String SUBMITTED_TITLE = "새로운 신고가 접수되었습니다";
    private static final String SUBMITTED_BODY = "신고 내용을 확인하고 처리해 주세요.";

    /** 결과 알림의 제목·본문. 상태 원문을 그대로 옮기지 않고 고정 문구만 쓴다. */
    private static final String RESOLVED_TITLE = "신고 처리 결과가 도착했습니다";
    private static final String RESOLVED_BODY = "접수하신 신고의 처리 결과를 확인해 주세요.";

    /** 기각 결과의 고정 문구. 그 밖의 상태는 일반 결과 문구로 안내한다. */
    private static final String DISMISSED_STATUS = "기각";
    private static final String DISMISSED_STATUS_CODE = "DISMISSED";
    private static final String DISMISSED_TITLE = "접수하신 신고가 기각되었습니다";
    private static final String DISMISSED_BODY = "신고 검토가 끝났습니다. 처리 결과를 확인해 주세요.";

    private final NotificationMapper notificationMapper;
    private final NotificationDispatcher notificationDispatcher;

    public ReportNotificationService(NotificationMapper notificationMapper,
            NotificationDispatcher notificationDispatcher) {
        this.notificationMapper = notificationMapper;
        this.notificationDispatcher = notificationDispatcher;
    }

    /**
     * 신고 접수 알림을 활성 시스템 관리자에게 저장한다. 신고 번호가 없거나 0 이하면 아무 것도 하지 않는다.
     */
    public void notifySubmitted(Long reportId) {
        if (reportId == null || reportId <= 0) {
            return;
        }
        List<Long> recipients = activeSystemAdminUserIds();
        if (recipients.isEmpty()) {
            return;
        }
        notificationDispatcher.notifyAfterCommit(recipients, NotificationType.REPORT_SUBMITTED.name(),
                SUBMITTED_TITLE, SUBMITTED_BODY, null, null, null, null, reportId);
    }

    /**
     * 신고 처리 결과 알림을 신고자에게만 저장한다. 신고 번호·신고자 번호가 유효하지 않으면 아무 것도 하지 않는다.
     */
    public void notifyResolved(Long reportId, Long reporterUserId, String status) {
        if (reportId == null || reportId <= 0 || reporterUserId == null || reporterUserId <= 0) {
            return;
        }
        boolean dismissed = isDismissed(status);
        notificationDispatcher.notifyAfterCommit(List.of(reporterUserId), NotificationType.REPORT_RESOLVED.name(),
                dismissed ? DISMISSED_TITLE : RESOLVED_TITLE,
                dismissed ? DISMISSED_BODY : RESOLVED_BODY,
                null, null, null, null, reportId);
    }

    /** 탈퇴하지 않은 회원 중 정확히 시스템 관리자로 해석되는 번호만 모은다. */
    private List<Long> activeSystemAdminUserIds() {
        List<SystemAdminCandidate> candidates = notificationMapper.findActiveAdminCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Long> recipients = new ArrayList<>();
        for (SystemAdminCandidate candidate : candidates) {
            if (candidate == null || candidate.getUserId() == null || candidate.getUserId() <= 0) {
                continue;
            }
            if (MemberRole.from(candidate.getRole()) == MemberRole.ADMIN) {
                recipients.add(candidate.getUserId());
            }
        }
        return recipients;
    }

    /** 기각 상태만 별도 문구로 안내한다. 상태 표기는 한글 라벨과 영문 코드를 모두 허용한다. */
    private static boolean isDismissed(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String value = status.strip();
        return DISMISSED_STATUS.equals(value) || DISMISSED_STATUS_CODE.equalsIgnoreCase(value);
    }
}
