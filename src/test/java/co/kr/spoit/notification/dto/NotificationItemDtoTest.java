package co.kr.spoit.notification.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import co.kr.spoit.notification.domain.Notification;
import co.kr.spoit.notification.domain.NotificationType;

/**
 * 알림 이동 경로가 알림 종류별로 올바른 화면을 가리키는지 검증한다.
 *
 * <p>같은 대상 번호라도 수신자 권한이 다르므로, 번호만 보고 관리 화면으로 보내면 권한 없는
 * 사용자를 막다른 화면으로 데려간다. 이 테스트는 그 회귀를 막는다.</p>
 */
class NotificationItemDtoTest {

    private static final long MATCH_ID = 42L;

    @Test
    @DisplayName("결과 알림 세 종류는 모두 실제 결과 화면(/result-form)으로 보낸다")
    void resultNoticesPointToResultForm() {
        String expected = "/mypage/matches/42/result-form";

        assertThat(hrefOf(NotificationType.MATCH_RESULT_CONFLICT, MATCH_ID, null, null)).isEqualTo(expected);
        assertThat(hrefOf(NotificationType.MATCH_RESULT_REMINDER, MATCH_ID, null, null)).isEqualTo(expected);
        assertThat(hrefOf(NotificationType.MATCH_RESULT_COMPLETED, MATCH_ID, null, null)).isEqualTo(expected);
    }

    @Test
    @DisplayName("결과 알림은 JSON 요약 API 주소가 아닌 화면 경로를 쓴다")
    void resultNoticesDoNotUseJsonEndpoint() {
        String href = hrefOf(NotificationType.MATCH_RESULT_COMPLETED, MATCH_ID, null, null);

        // /result는 팀장 전용 JSON API이고 그 하위도 JSON이다. 화면 경로는 /result-form이다.
        assertThat(href).doesNotEndWith("/result");
        assertThat(href).endsWith("/result-form");
    }

    @Test
    @DisplayName("매칭 신청 접수·취소는 시설 관리자용 신청 관리 화면으로 보낸다")
    void managerApplicationNoticesPointToAdminOperations() {
        assertThat(hrefOf(NotificationType.MATCH_APPLICATION_RECEIVED, MATCH_ID, 7L, null))
                .isEqualTo("/admin/operations?matchId=42");
        assertThat(hrefOf(NotificationType.MATCH_APPLICATION_CANCELLED, MATCH_ID, 7L, null))
                .isEqualTo("/admin/operations?matchId=42");
    }

    @Test
    @DisplayName("사용자용 매칭 알림은 매칭 상세로 보낸다")
    void participantMatchNoticesPointToMatchDetail() {
        assertThat(hrefOf(NotificationType.MATCH_APPLICATION_CONFIRMED, MATCH_ID, 7L, null))
                .isEqualTo("/matches/42");
        assertThat(hrefOf(NotificationType.MATCH_AUTO_ASSIGNED, MATCH_ID, null, null))
                .isEqualTo("/matches/42");
        assertThat(hrefOf(NotificationType.MATCH_AUTO_CANCELLED, MATCH_ID, null, null))
                .isEqualTo("/matches/42");
        assertThat(hrefOf(NotificationType.MATCH_ROOM_CANCELLED, MATCH_ID, null, null))
                .isEqualTo("/matches/42");
    }

    @Test
    @DisplayName("클럽 알림은 수신자 권한과 대상 팀에 맞는 경로로 보낸다")
    void clubNoticesMatchRecipientPermission() {
        // 가입 신청 접수는 동호회장만 받는다. 동호회장이 여러 팀이면 관리 화면은 첫 팀만 열리므로
        // 신청이 접수된 팀(clubId)을 지정해야 엉뚱한 팀의 신청 목록을 보여 주지 않는다.
        assertThat(hrefOf(NotificationType.CLUB_JOIN_REQUESTED, null, null, 3L, 55L))
                .isEqualTo("/teams/manage?clubId=55&tab=applications");
        // 승인된 팀의 상세와 위임받은 팀의 관리 화면으로 연결한다.
        assertThat(hrefOf(NotificationType.CLUB_JOIN_APPROVED, null, null, 3L, 55L))
                .isEqualTo("/teams/55");
        assertThat(hrefOf(NotificationType.CLUB_LEADER_TRANSFERRED, null, null, null, 55L))
                .isEqualTo("/teams/manage?clubId=55&tab=members");
    }

    @Test
    @DisplayName("clubId가 없으면 이전의 안전한 경로로 되돌아간다")
    void clubNoticesFallBackWithoutClubId() {
        // 조인 실패·빈 값·0·음수는 모두 '대상 팀을 모른다'로 보고 기존 fallback을 쓴다.
        for (Long missing : new Long[] {null, 0L, -1L}) {
            assertThat(hrefOf(NotificationType.CLUB_JOIN_REQUESTED, null, null, 3L, missing))
                    .as("clubId=%s", missing)
                    .isEqualTo("/teams/manage?tab=applications");
            assertThat(hrefOf(NotificationType.CLUB_JOIN_APPROVED, null, null, 3L, missing))
                    .as("clubId=%s", missing)
                    .isEqualTo("/teams/manage");
            assertThat(hrefOf(NotificationType.CLUB_JOIN_REJECTED, null, null, 3L, missing))
                    .as("clubId=%s", missing)
                    .isEqualTo("/teams");
            assertThat(hrefOf(NotificationType.CLUB_LEADER_TRANSFERRED, null, null, null, missing))
                    .as("clubId=%s", missing)
                    .isEqualTo("/teams");
        }
    }

    @Test
    @DisplayName("표시 모델은 서버가 조인해 읽은 clubId를 그대로 담고, 없으면 null이다")
    void exposesClubIdFromJoinedRow() {
        assertThat(item(NotificationType.CLUB_JOIN_REQUESTED, null, null, 3L, 55L, null).getClubId())
                .isEqualTo(55L);
        assertThat(item(NotificationType.CLUB_JOIN_REQUESTED, null, null, 3L, null, null).getClubId())
                .isNull();
        // 화면 경로에 쓸 수 없는 값은 노출하지 않는다.
        assertThat(item(NotificationType.CLUB_JOIN_REQUESTED, null, null, 3L, 0L, null).getClubId())
                .isNull();
        // 클럽 알림이 아닌 행도 조인 결과가 없으면 null로 남는다.
        assertThat(item(NotificationType.MATCH_AUTO_ASSIGNED, MATCH_ID, null, null, null, null).getClubId())
                .isNull();
    }

    @Test
    @DisplayName("가입 거절·추방은 권한 없는 팀 관리가 아닌 접근 가능한 경로로 보낸다")
    void revokedClubNoticesAvoidManagePage() {
        String rejected = hrefOf(NotificationType.CLUB_JOIN_REJECTED, null, null, 3L, 55L);
        String kicked = hrefOf(NotificationType.CLUB_MEMBER_KICKED, null, null, null, 55L);

        // 거절된 신청자·추방된 팀원은 더 이상 팀장이 아니므로 관리 화면은 권한 오류 화면이 된다.
        assertThat(rejected).isEqualTo("/teams/55");
        assertThat(kicked).isEqualTo("/teams/55");
        assertThat(rejected).doesNotContain("/teams/manage");
        assertThat(kicked).doesNotContain("/teams/manage");
    }

    @Test
    @DisplayName("join_request_id만으로 일괄 관리 화면에 보내지 않는다")
    void doesNotRouteClubNoticeByJoinRequestIdAlone() {
        // 알 수 없는 코드 + join_request_id 조합. 클럽 번호를 조인해 알 수 있더라도 코드를 모르면
        // 관리 화면으로 추측해 보내지 않고 공개 목록(또는 조인이 있으면 상세)으로만 보낸다.
        assertThat(hrefOfRaw("CLUB_UNKNOWN_FUTURE_CODE", null, null, 3L)).isEqualTo("/teams");
        assertThat(hrefOfRaw("CLUB_UNKNOWN_FUTURE_CODE", null, null, 3L)).doesNotContain("/teams/manage");
        // 알 수 없는 코드에는 clubId를 붙여 관리 화면 딥링크를 만들지 않는다.
        assertThat(hrefOfRaw("CLUB_UNKNOWN_FUTURE_CODE", null, null, 3L, 55L)).isEqualTo("/teams");
        assertThat(hrefOfRaw("CLUB_UNKNOWN_FUTURE_CODE", null, null, 3L, 55L)).doesNotContain("clubId=");
    }

    @Test
    @DisplayName("매칭 번호가 없으면 결과·매칭 알림은 알림함으로 돌린다")
    void matchNoticesWithoutMatchIdFallBackToInbox() {
        assertThat(hrefOf(NotificationType.MATCH_RESULT_COMPLETED, null, null, null))
                .isEqualTo("/mypage#notifications");
        assertThat(hrefOf(NotificationType.MATCH_APPLICATION_RECEIVED, null, 7L, null))
                .isEqualTo("/mypage#notifications");
        assertThat(hrefOf(NotificationType.MATCH_AUTO_ASSIGNED, null, null, null))
                .isEqualTo("/mypage#notifications");
    }

    @Test
    @DisplayName("읽지 않은 알림은 read가 false이고 읽은 알림은 true다")
    void mapsReadState() {
        assertThat(item(NotificationType.CLUB_JOIN_APPROVED, null, null, null, null).isRead()).isFalse();
        assertThat(item(NotificationType.CLUB_JOIN_APPROVED, null, null, null, LocalDateTime.now()).isRead())
                .isTrue();
    }

    @Test
    @DisplayName("경로는 항상 같은 출처의 내부 경로이며 외부로 나가지 않는다")
    void everyHrefStaysInternal() {
        for (NotificationType type : NotificationType.values()) {
            String href = hrefOf(type, MATCH_ID, 7L, 3L);
            assertThat(href).as("type=%s", type).startsWith("/");
            assertThat(href).as("type=%s", type).doesNotStartWith("//");
            assertThat(href).as("type=%s", type).doesNotContain("://");
            assertThat(href).as("type=%s", type).doesNotContain("\\");
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(NotificationType.class)
    @DisplayName("모든 알림 종류가 비어 있지 않은 내부 경로를 갖는다")
    void everyTypeHasDestination(NotificationType type) {
        assertThat(hrefOf(type, MATCH_ID, 7L, 3L)).isNotBlank().startsWith("/");
    }

    @ParameterizedTest(name = "type={0}")
    @ValueSource(strings = {"", "   ", "UNKNOWN_CODE"})
    @DisplayName("종류가 비었거나 알 수 없으면 대상 번호로만 안전하게 되돌린다")
    void unknownTypeUsesSafeFallback(String rawType) {
        // 매칭 번호가 있으면 참가자용 상세로, 없으면 알림함으로 간다. 관리 화면으로는 보내지 않는다.
        assertThat(hrefOfRaw(rawType, MATCH_ID, null, null)).isEqualTo("/matches/42");
        assertThat(hrefOfRaw(rawType, null, null, 3L)).isEqualTo("/teams");
        assertThat(hrefOfRaw(rawType, null, null, null)).isEqualTo("/mypage#notifications");
    }

    private static String hrefOf(NotificationType type, Long matchId, Long applicationId, Long joinRequestId) {
        return item(type, matchId, applicationId, joinRequestId, null, null).getHref();
    }

    private static String hrefOf(NotificationType type, Long matchId, Long applicationId, Long joinRequestId,
            Long clubId) {
        return item(type, matchId, applicationId, joinRequestId, clubId, null).getHref();
    }

    private static String hrefOfRaw(String rawType, Long matchId, Long applicationId, Long joinRequestId) {
        return hrefOfRaw(rawType, matchId, applicationId, joinRequestId, null);
    }

    private static String hrefOfRaw(String rawType, Long matchId, Long applicationId, Long joinRequestId,
            Long clubId) {
        return NotificationItemDto.from(notification(rawType, matchId, applicationId, joinRequestId, clubId, null))
                .getHref();
    }

    private static NotificationItemDto item(NotificationType type, Long matchId, Long applicationId,
            Long joinRequestId, LocalDateTime readAt) {
        return NotificationItemDto.from(
                notification(type.name(), matchId, applicationId, joinRequestId, null, readAt));
    }

    private static NotificationItemDto item(NotificationType type, Long matchId, Long applicationId,
            Long joinRequestId, Long clubId, LocalDateTime readAt) {
        return NotificationItemDto.from(
                notification(type.name(), matchId, applicationId, joinRequestId, clubId, readAt));
    }

    private static Notification notification(String type, Long matchId, Long applicationId, Long joinRequestId,
            LocalDateTime readAt) {
        return notification(type, matchId, applicationId, joinRequestId, null, readAt);
    }

    private static Notification notification(String type, Long matchId, Long applicationId, Long joinRequestId,
            Long clubId, LocalDateTime readAt) {
        Notification notification = new Notification();
        notification.setNotificationId(1L);
        notification.setRecipientUserId(7L);
        notification.setType(type);
        notification.setTitle("제목");
        notification.setBody("본문");
        notification.setMatchId(matchId);
        notification.setApplicationId(applicationId);
        notification.setJoinRequestId(joinRequestId);
        notification.setClubId(clubId);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setReadAt(readAt);
        return notification;
    }
}
