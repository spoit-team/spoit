package co.kr.spoit.notification.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.notification.domain.Notification;
import co.kr.spoit.notification.domain.NotificationType;

/**
 * 신고 알림의 이동 경로·노출 값 검증.
 *
 * <p>접수 알림은 시스템 관리자 화면, 결과 알림은 시설 관리자 셸의 신고 화면으로 보낸다. 신고 번호가
 * 양수가 아니면 관리 화면으로 추측해 보내지 않고 알림함으로 되돌린다.</p>
 */
class ReportNotificationItemDtoTest {

    private static final long REPORT_ID = 15L;

    @Test
    @DisplayName("신고 접수 알림은 시스템 관리자 신고 화면으로 신고 번호를 붙여 보낸다")
    void submittedHrefPointsToSystemReports() {
        assertThat(item(NotificationType.REPORT_SUBMITTED, REPORT_ID).getHref())
                .isEqualTo("/admin/system/reports?reportId=15");
    }

    @Test
    @DisplayName("신고 결과 알림은 시설 관리자 셸의 신고 화면으로 신고 번호를 붙여 보낸다")
    void resolvedHrefPointsToFacilityReports() {
        assertThat(item(NotificationType.REPORT_RESOLVED, REPORT_ID).getHref())
                .isEqualTo("/admin/facility/reports?reportId=15");
    }

    @Test
    @DisplayName("신고 번호가 없거나 0·음수면 관리 화면 대신 알림함으로 되돌린다")
    void invalidReportIdFallsBackToInbox() {
        for (Long invalid : new Long[] {null, 0L, -1L}) {
            assertThat(item(NotificationType.REPORT_SUBMITTED, invalid).getHref())
                    .as("reportId=%s", invalid)
                    .isEqualTo("/mypage#notifications");
            assertThat(item(NotificationType.REPORT_RESOLVED, invalid).getHref())
                    .as("reportId=%s", invalid)
                    .isEqualTo("/mypage#notifications");
        }
    }

    @Test
    @DisplayName("표시 모델은 양수 신고 번호만 노출하고 그 밖의 값은 null로 둔다")
    void exposesOnlyValidReportId() {
        assertThat(item(NotificationType.REPORT_SUBMITTED, REPORT_ID).getReportId()).isEqualTo(REPORT_ID);
        assertThat(item(NotificationType.REPORT_SUBMITTED, 0L).getReportId()).isNull();
        assertThat(item(NotificationType.REPORT_SUBMITTED, -3L).getReportId()).isNull();
        assertThat(item(NotificationType.MATCH_AUTO_ASSIGNED, null).getReportId()).isNull();
    }

    @Test
    @DisplayName("신고 알림 경로도 같은 출처의 내부 경로이며 외부로 나가지 않는다")
    void reportHrefsStayInternal() {
        for (NotificationType type : new NotificationType[] {
                NotificationType.REPORT_SUBMITTED, NotificationType.REPORT_RESOLVED}) {
            String href = item(type, REPORT_ID).getHref();
            assertThat(href).startsWith("/").doesNotStartWith("//").doesNotContain("://", "\\");
        }
    }

    private static NotificationItemDto item(NotificationType type, Long reportId) {
        Notification notification = new Notification();
        notification.setNotificationId(1L);
        notification.setRecipientUserId(7L);
        notification.setType(type.name());
        notification.setTitle("제목");
        notification.setBody("본문");
        notification.setReportId(reportId);
        notification.setCreatedAt(LocalDateTime.now());
        return NotificationItemDto.from(notification);
    }
}
