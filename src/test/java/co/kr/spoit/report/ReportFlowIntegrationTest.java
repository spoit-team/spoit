package co.kr.spoit.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.notification.domain.NotificationType;
import co.kr.spoit.report.domain.ReportCategory;
import co.kr.spoit.report.domain.ReportStatus;

/**
 * 신고 기능의 실제 요청 경계를 컨트롤러 → 서비스 → MyBatis → MySQL → 알림 저장까지 한 번에 검증한다.
 *
 * <p>기존 테스트는 계층별로 나뉘어 있다. {@code FacilityReportControllerTest}·
 * {@code SystemReportControllerTest}는 서비스를 목으로 두고 라우트·PRG 계약만 보고,
 * {@code ReportMapperIntegrationTest}는 매퍼 SQL만 본다. 그 사이의 빈틈, 즉 <strong>실제 시설 관리자
 * POST가 실제 DB에 신고 행과 시스템 관리자 알림을 만들고, 실제 시스템 관리자 POST가 그 신고를
 * 완결하며 신고자 알림을 남기는지</strong>는 어느 테스트도 확인하지 않는다. 이 파일은 그 한 줄기를
 * 고정하는 첫 실제 DB 경계 테스트라 중복이 아니다.</p>
 *
 * <p>고정하는 계약: (1) 시설 관리자 신고는 인증 주체·확정 참가자 기준으로 접수 상태 행을 만들고,
 * 시스템 관리자에게만 신고 종류 알림을 신고 번호와 함께 남기며, (2) 처리 완료는 상태·검토자·사유·
 * 검토 시각을 남기고 신고자에게만 결과 알림을 남긴다. 두 알림 제목·본문에는 신고 원문·처리 사유·
 * 대상자 닉네임·연락처·신고 유형 같은 내용이 실리지 않는다. (3) 이용이 끝나지 않은 경기는 확정
 * 참가자여도 접수되지 않고 어떤 행·알림도 남기지 않는다(미종료 경기 게이트를 가장 중요한 단일
 * 차단으로 선택). (4) 다른 역할은 시설 신고 접수·시스템 처리를 호출할 수 없다.</p>
 *
 * <p>제재·업로드·대상자 통지는 이 기능의 범위가 아니므로 검증하지 않고, 다른 도메인의 동작도
 * 건드리지 않는다. {@code MAT_TEST_DB_URL} 등 환경변수가 없으면 실행되지 않으며, 모든 fixture는
 * 테스트 트랜잭션 안에서만 삽입되어 종료 시 롤백된다. 개발·운영 DB에는 흔적을 남기지 않는다.</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class ReportFlowIntegrationTest {

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    /** 리다이렉트 Location에서 생성된 신고 번호를 꺼낸다. */
    private static final Pattern REPORT_ID_IN_LOCATION = Pattern.compile("[?&]reportId=(\\d+)");

    /** 담당 시설 A를 관리하는 시설 관리자이자 이번 신고의 신고자. */
    private static final long MANAGER_A = 995001L;
    /** 신고 접수 알림을 받는 시스템 관리자이자 처리 완료 검토자. */
    private static final long ADMIN_USER = 995002L;
    /** 종료 경기의 확정 참가자이자 신고 대상자. */
    private static final long TARGET_USER = 995003L;
    /** 종료 경기의 대기 참가자. 확정이 아니므로 신고 대상이 아니다. */
    private static final long WAITING_USER = 995004L;

    private static final long REGION_ID = 995001L;
    private static final long SPORT_ID = 995001L;
    private static final long FACILITY_A = 995001L;

    /** 담당 시설 A의 종료 경기(정상 접수 대상). */
    private static final long CLOSED_MATCH = 995001L;
    /** 담당 시설 A의 모집 중 경기(미종료 게이트 검증용). */
    private static final long RECRUITING_MATCH = 995002L;

    private static final String CATEGORY = ReportCategory.NO_SHOW.dbValue();
    private static final String SUMMARY = "경기 시작 후 연락 없이 불참했습니다.";
    private static final String RESOLUTION = "신고 내용을 확인해 경고 처리했습니다.";
    private static final String TARGET_NICKNAME = "신고대상자";
    private static final String TARGET_PHONE = "010-9950-0003";
    private static final String PASSWORD_HASH = "TEST_ONLY_NOT_A_PASSWORD_HASH";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        String url = validateUrl(requiredEnvironmentVariable("MAT_TEST_DB_URL"));
        String username = requiredEnvironmentVariable("MAT_TEST_DB_USERNAME");
        String password = requiredEnvironmentVariable("MAT_TEST_DB_PASSWORD");

        registry.add("spring.datasource.url", () -> url + JDBC_OPTIONS);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeTransaction
    void verifyDatabaseBeforeFixture() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"spoit_mat_test".equals(database)) {
            throw new IllegalStateException("전용 테스트 DB가 아니므로 fixture 실행을 중단합니다.");
        }
    }

    @BeforeEach
    void insertFixture() {
        jdbcTemplate.update("""
                INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)
                VALUES
                    (?, 'report-flow-manager@example.invalid', ?, '신고흐름 관리자', '010-9950-0001', '시설 관리자', ?),
                    (?, 'report-flow-admin@example.invalid', ?, '신고흐름 관리자시스템', '010-9950-0002', '시스템 관리자', ?),
                    (?, 'report-flow-target@example.invalid', ?, ?, ?, '일반 회원', ?),
                    (?, 'report-flow-waiting@example.invalid', ?, '대기참가자', '010-9950-0004', '일반 회원', ?)
                """,
                MANAGER_A, PASSWORD_HASH, CREATED_AT,
                ADMIN_USER, PASSWORD_HASH, CREATED_AT,
                TARGET_USER, PASSWORD_HASH, TARGET_NICKNAME, TARGET_PHONE, CREATED_AT,
                WAITING_USER, PASSWORD_HASH, CREATED_AT);
        jdbcTemplate.update("INSERT INTO region (region_id, parent_region_id, name) VALUES (?, NULL, ?)",
                REGION_ID, "신고 흐름 테스트 지역");
        jdbcTemplate.update("INSERT INTO sport (sport_id, name) VALUES (?, ?)", SPORT_ID, "신고 흐름 테스트 종목");
        jdbcTemplate.update("""
                INSERT INTO facility (
                    facility_id, region_id, name, address, latitude, longitude,
                    concurrent_match_limit, status, deleted_at
                ) VALUES (?, ?, '신고 흐름 테스트 시설', '테스트 주소', 37.5000000, 127.0000000, 3, '운영 중', NULL)
                """, FACILITY_A, REGION_ID);
        jdbcTemplate.update("""
                INSERT INTO facility_sport (
                    facility_id, sport_id, default_min, default_max, default_fee, default_skill
                ) VALUES (?, ?, 1, 4, 0.00, '무관')
                """, FACILITY_A, SPORT_ID);
        jdbcTemplate.update("""
                INSERT INTO facility_manager (facility_id, user_id, assigned_at) VALUES (?, ?, ?)
                """, FACILITY_A, MANAGER_A, CREATED_AT);
        jdbcTemplate.update("""
                INSERT INTO social_match (
                    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                    min_participants, max_participants, team_a_capacity, team_b_capacity,
                    fee_per_person, required_skill, status, assignment_method, rating_status
                ) VALUES
                    (?, ?, ?, ?, '신고 흐름 종료 경기', '2026-11-03 10:00:00', '2026-11-03 12:00:00',
                     1, 4, 2, 2, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
                    (?, ?, ?, ?, '신고 흐름 모집 중 경기', '2026-11-05 10:00:00', '2026-11-05 12:00:00',
                     1, 4, 2, 2, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING')
                """,
                CLOSED_MATCH, FACILITY_A, SPORT_ID, MANAGER_A,
                RECRUITING_MATCH, FACILITY_A, SPORT_ID, MANAGER_A);
        jdbcTemplate.update("""
                INSERT INTO match_application (
                    application_id, match_id, applicant_user_id, club_id, application_type,
                    status, receipt_no, created_at
                ) VALUES
                    (?, ?, ?, NULL, '개인', '확정', 'REPORT-FLOW-RCPT-1', ?),
                    (?, ?, ?, NULL, '개인', '대기', 'REPORT-FLOW-RCPT-2', ?),
                    (?, ?, ?, NULL, '개인', '확정', 'REPORT-FLOW-RCPT-3', ?)
                """,
                995001L, CLOSED_MATCH, TARGET_USER, CREATED_AT,
                995002L, CLOSED_MATCH, WAITING_USER, CREATED_AT,
                995003L, RECRUITING_MATCH, TARGET_USER, CREATED_AT);
        // 종료 경기: 확정 1명 + 대기 1명. 모집 중 경기: 확정 1명(상태 게이트만 남기기 위해 확정 참가자를 둔다).
        jdbcTemplate.update("""
                INSERT INTO match_participant (
                    participant_id, application_id, match_id, user_id, participation_status,
                    match_side, entry_mmr, joined_at
                ) VALUES
                    (?, ?, ?, ?, '확정', 'A', 1000.00, ?),
                    (?, ?, ?, ?, '대기', NULL, 1000.00, ?),
                    (?, ?, ?, ?, '확정', 'A', 1000.00, ?)
                """,
                995001L, 995001L, CLOSED_MATCH, TARGET_USER, CREATED_AT,
                995002L, 995002L, CLOSED_MATCH, WAITING_USER, CREATED_AT,
                995003L, 995003L, RECRUITING_MATCH, TARGET_USER, CREATED_AT);
    }

    @Test
    @DisplayName("시설 관리자 신고가 DB·시스템 관리자 알림을 만들고, 시스템 관리자 처리 완료가 신고자 알림까지 잇는다")
    void reportAndDecisionFlowPersistsRowsAndNotifications() throws Exception {
        // 1) 실제 시설 관리자 POST: 접수 상태 신고 저장 + 시스템 관리자 접수 알림.
        MvcResult submitted = mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", String.valueOf(CLOSED_MATCH))
                        .param("targetUserId", String.valueOf(TARGET_USER))
                        .param("category", CATEGORY)
                        .param("summary", SUMMARY)
                        .with(csrf())
                        .with(user(principal(MANAGER_A, "시설 관리자"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "신고가 접수되었습니다."))
                .andReturn();

        long reportId = reportIdFromLocation(submitted);
        assertThat(submitted.getResponse().getRedirectedUrl())
                .isEqualTo("/admin/facility/reports?reportId=" + reportId
                        + "&matchPage=0&reportPage=0#report-selected");

        assertThat(reportStatus(reportId)).isEqualTo(ReportStatus.RECEIVED.dbValue());
        assertThat(reportSummary(reportId)).isEqualTo(SUMMARY);
        assertThat(queryString("SELECT category FROM report WHERE report_id = ?", reportId))
                .isEqualTo(CATEGORY);
        assertThat(queryLong("SELECT reporter_user_id FROM report WHERE report_id = ?", reportId))
                .isEqualTo(MANAGER_A);
        assertThat(queryLong("SELECT target_user_id FROM report WHERE report_id = ?", reportId))
                .isEqualTo(TARGET_USER);
        assertThat(queryLong("SELECT match_id FROM report WHERE report_id = ?", reportId))
                .isEqualTo(CLOSED_MATCH);
        // 접수 직후에는 검토자·처리 사유·검토 시각이 모두 비어 있다.
        assertThat(queryInt("""
                SELECT COUNT(*)
                  FROM report
                 WHERE report_id = ?
                   AND reviewed_by IS NULL
                   AND resolution IS NULL
                   AND reviewed_at IS NULL
                """, reportId)).isEqualTo(1);

        NotificationRow submittedNotification =
                singleNotification(reportId, NotificationType.REPORT_SUBMITTED.name(), ADMIN_USER);
        assertThat(submittedNotification.reportId()).isEqualTo(reportId);
        assertNoPrivateContent(submittedNotification);
        // 접수 알림은 대상자에게 가지 않는다(시스템 관리자에게만 간다).
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE report_id = ? AND recipient_user_id = ?",
                Integer.class, reportId, TARGET_USER)).isZero();

        // 2) 실제 시스템 관리자 POST: 처리 완료 + 검토자·사유·검토 시각 + 신고자 결과 알림.
        mockMvc.perform(post("/admin/system/reports/{reportId}/decision", reportId)
                        .param("action", "complete")
                        .param("expectedStatus", ReportStatus.RECEIVED.dbValue())
                        .param("resolution", RESOLUTION)
                        .with(csrf())
                        .with(user(principal(ADMIN_USER, "시스템 관리자"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/system/reports?reportId=" + reportId + "#report-case"))
                .andExpect(flash().attribute("message", "신고를 처리 완료로 마무리했습니다."));

        assertThat(reportStatus(reportId)).isEqualTo(ReportStatus.COMPLETED.dbValue());
        assertThat(queryLong("SELECT reviewed_by FROM report WHERE report_id = ?", reportId))
                .isEqualTo(ADMIN_USER);
        assertThat(queryString("SELECT resolution FROM report WHERE report_id = ?", reportId))
                .isEqualTo(RESOLUTION);
        assertThat(queryInt(
                "SELECT COUNT(*) FROM report WHERE report_id = ? AND reviewed_at IS NOT NULL",
                reportId)).isEqualTo(1);

        // 결과 알림은 신고자에게만, 같은 신고 번호로 남고 개인정보를 담지 않는다.
        NotificationRow resolvedNotification =
                singleNotification(reportId, NotificationType.REPORT_RESOLVED.name(), MANAGER_A);
        assertThat(resolvedNotification.reportId()).isEqualTo(reportId);
        assertNoPrivateContent(resolvedNotification);
        // 이 신고의 결과 알림은 신고자 1건뿐이고, 대상자에게는 어떤 알림도 가지 않는다.
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM notification
                 WHERE report_id = ?
                   AND notification_type = ?
                """, Integer.class, reportId, NotificationType.REPORT_RESOLVED.name())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE report_id = ? AND recipient_user_id = ?",
                Integer.class, reportId, TARGET_USER)).isZero();
    }

    @Test
    @DisplayName("이용이 끝나지 않은 경기는 확정 참가자여도 접수되지 않고 신고·알림 행을 남기지 않는다")
    void recruitingMatchIsRejectedWithoutRows() throws Exception {
        // 모집 중 경기의 대상자는 확정 참가자다. 남는 게이트는 경기 상태뿐이다.
        mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", String.valueOf(RECRUITING_MATCH))
                        .param("targetUserId", String.valueOf(TARGET_USER))
                        .param("category", CATEGORY)
                        .param("summary", SUMMARY)
                        .with(csrf())
                        .with(user(principal(MANAGER_A, "시설 관리자"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "이용이 끝난 경기만 신고할 수 있습니다."))
                .andExpect(redirectedUrl("/admin/facility/reports?matchId=" + RECRUITING_MATCH
                        + "&targetUserId=" + TARGET_USER + "&matchPage=0&reportPage=0"));

        assertThat(countReportsByReporter()).isZero();
        assertThat(countSubmittedNotifications()).isZero();
    }

    @Test
    @DisplayName("시스템 관리자·일반 회원은 신고 접수를, 시설 관리자는 시스템 처리를 호출할 수 없다")
    void otherRolesCannotSubmitOrDecide() throws Exception {
        mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", String.valueOf(CLOSED_MATCH))
                        .param("targetUserId", String.valueOf(TARGET_USER))
                        .param("category", CATEGORY)
                        .param("summary", SUMMARY)
                        .with(csrf())
                        .with(user(principal(ADMIN_USER, "시스템 관리자"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/facility/reports")
                        .param("matchId", String.valueOf(CLOSED_MATCH))
                        .param("targetUserId", String.valueOf(TARGET_USER))
                        .param("category", CATEGORY)
                        .param("summary", SUMMARY)
                        .with(csrf())
                        .with(user(principal(WAITING_USER, "일반 회원"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/system/reports/{reportId}/decision", 999999999L)
                        .param("action", "review")
                        .param("expectedStatus", ReportStatus.RECEIVED.dbValue())
                        .with(csrf())
                        .with(user(principal(MANAGER_A, "시설 관리자"))))
                .andExpect(status().isForbidden());

        assertThat(countReportsByReporter()).isZero();
        assertThat(countSubmittedNotifications()).isZero();
    }

    /** 리다이렉트 Location에서 신고 번호를 꺼낸다. 번호가 없으면 계약 위반으로 실패한다. */
    private static long reportIdFromLocation(MvcResult result) {
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).as("신고 접수 후 리다이렉트 위치").isNotNull();
        Matcher matcher = REPORT_ID_IN_LOCATION.matcher(location);
        assertThat(matcher.find()).as("리다이렉트에 생성된 신고 번호가 있어야 합니다: %s", location).isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private String reportStatus(long reportId) {
        return queryString("SELECT status FROM report WHERE report_id = ?", reportId);
    }

    private String reportSummary(long reportId) {
        return queryString("SELECT summary FROM report WHERE report_id = ?", reportId);
    }

    private String queryString(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private long queryLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private int queryInt(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    /** 알림 한 건에서 검증에 필요한 값만 담는다. */
    private record NotificationRow(Long reportId, String title, String body) {
    }

    /** 신고 번호·종류·수신자로 유일한 알림 한 건을 읽는다. 여러 건이면 계약 위반이다. */
    private NotificationRow singleNotification(long reportId, String type, long recipientUserId) {
        List<NotificationRow> rows = jdbcTemplate.query("""
                SELECT report_id, title, body
                  FROM notification
                 WHERE report_id = ?
                   AND notification_type = ?
                   AND recipient_user_id = ?
                """, (rs, rowNum) -> {
                    long linkedReportId = rs.getLong("report_id");
                    return new NotificationRow(rs.wasNull() ? null : linkedReportId,
                            rs.getString("title"), rs.getString("body"));
                },
                reportId, type, recipientUserId);
        assertThat(rows).as("%s 알림은 수신자당 한 건이어야 합니다", type).hasSize(1);
        return rows.get(0);
    }

    /** 알림 제목·본문에 신고 원문·처리 사유·대상자 닉네임·연락처·유형이 실리지 않는지 확인한다. */
    private static void assertNoPrivateContent(NotificationRow notification) {
        assertThat(notification.reportId()).isNotNull();
        assertThat(notification.title()).isNotBlank();
        assertThat(notification.body()).isNotBlank();
        assertThat(notification.title())
                .doesNotContain(SUMMARY, RESOLUTION, TARGET_NICKNAME, TARGET_PHONE, CATEGORY);
        assertThat(notification.body())
                .doesNotContain(SUMMARY, RESOLUTION, TARGET_NICKNAME, TARGET_PHONE, CATEGORY);
    }

    private int countReportsByReporter() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM report WHERE reporter_user_id = ?", Integer.class, MANAGER_A);
        return count == null ? 0 : count;
    }

    /**
     * 이 테스트 신고자(MANAGER_A)의 신고에 연결된 접수 알림 수를 센다. 신고가 저장되지 않았으면
     * 연결될 알림도 없어야 하므로 음성 검증의 기준이 된다.
     */
    private int countSubmittedNotifications() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM notification n
                  JOIN report r ON r.report_id = n.report_id
                 WHERE n.notification_type = ?
                   AND n.recipient_user_id = ?
                   AND r.reporter_user_id = ?
                """, Integer.class, NotificationType.REPORT_SUBMITTED.name(), ADMIN_USER, MANAGER_A);
        return count == null ? 0 : count;
    }

    /** DB 역할 값과 같은 표기를 써서 실제 인증 주체와 동일한 권한을 갖게 한다. */
    private static MemberPrincipal principal(long userId, String role) {
        Member member = new Member();
        member.setUserId(userId);
        member.setEmail("report-flow-" + userId + "@example.invalid");
        member.setNickname("신고흐름사용자");
        member.setPhone("010-0000-0000");
        member.setRole(role);
        return MemberPrincipal.from(member);
    }

    static String validateUrl(String url) {
        Matcher matcher = TEST_DATABASE_URL.matcher(url == null ? "" : url);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "MAT_TEST_DB_URL은 localhost 또는 127.0.0.1의 명시적 포트와 spoit_mat_test DB를 지정해야 합니다.");
        }
        int port = Integer.parseInt(matcher.group(1));
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("MAT_TEST_DB_URL의 포트는 1~65535 범위여야 합니다.");
        }
        return url;
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다. 개발 DB 설정으로 대체하지 않습니다.");
        }
        return value;
    }
}
