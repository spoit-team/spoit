package co.kr.spoit.report.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.BeforeTransaction;

import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.report.domain.Report;
import co.kr.spoit.report.domain.ReportMatchRow;
import co.kr.spoit.report.domain.ReportParticipantRow;
import co.kr.spoit.report.domain.ReportRow;

/**
 * 신고 SQL을 전용 MySQL DB에서만 검증한다.
 *
 * <p>{@code MAT_TEST_DB_URL} 등 환경변수가 없으면 실행되지 않는다. fixture는 테스트 트랜잭션 안에서만
 * 저장되고 종료 시 롤백되므로 개발·운영 DB에는 흔적을 남기지 않는다.</p>
 *
 * <p>고정하는 계약: (1) 경기 선택지는 담당 활성 시설의 {@code 종료} 경기만 offset 페이지로, (2)
 * 페이지 밖 링크 선택도 담당·종료 조건을 지킨 단건 조회만, (3) 대상 선택지는 {@code 확정} 참가자
 * 전부(절단 없음), (4) 시설 관리자 목록·상세는 신고자 본인·현재 담당 시설로 제한되고 원문은
 * 상세에만, (5) 저장은 접수 상태로만 되고 상태 조건부 갱신은 기대 상태가 다르면 0행, (6) 중복 조회는
 * <strong>DB 현재 시각</strong> 기준 10초 이내이면서 summary를 <strong>바이트 단위로 정확히</strong>
 * 비교한다.</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReportMapperIntegrationTest {

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    /** 담당 시설을 가진 관리자 A. */
    private static final long MANAGER_A = 990001L;
    /** 다른 시설을 담당하는 관리자 B. */
    private static final long MANAGER_B = 990002L;
    /** 신고 대상자. */
    private static final long TARGET_USER = 990003L;
    /** 확정 참가자이지만 대기 상태 행도 함께 가진 회원. */
    private static final long WAITING_USER = 990004L;

    private static final long REGION_ID = 990001L;
    private static final long SPORT_ID = 990001L;
    private static final long FACILITY_A = 990001L;
    private static final long FACILITY_B = 990002L;

    /** 담당 시설 A의 종료 경기(가장 최근 시작). */
    private static final long CLOSED_MATCH = 990001L;
    /** 담당 시설 A의 모집 중 경기. */
    private static final long RECRUITING_MATCH = 990002L;
    /** 담당 시설 B(다른 관리자)의 종료 경기. */
    private static final long OTHER_MANAGER_CLOSED_MATCH = 990003L;
    /** 담당 시설 A의 종료 경기(페이지 밖 링크 선택 검증용, 가장 오래된 시작). */
    private static final long OLD_CLOSED_MATCH = 990004L;

    private static final String PASSWORD_HASH = "TEST_ONLY_NOT_A_PASSWORD_HASH";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Autowired
    private ReportMapper reportMapper;

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
                    (?, 'report-manager-a@example.invalid', ?, '신고 관리자 A', '010-9900-0001', '시설 관리자', ?),
                    (?, 'report-manager-b@example.invalid', ?, '신고 관리자 B', '010-9900-0002', '시설 관리자', ?),
                    (?, 'report-target@example.invalid', ?, '신고 대상자', '010-9900-0003', '일반 회원', ?),
                    (?, 'report-waiting@example.invalid', ?, '대기 참가자', '010-9900-0004', '일반 회원', ?)
                """,
                MANAGER_A, PASSWORD_HASH, CREATED_AT,
                MANAGER_B, PASSWORD_HASH, CREATED_AT,
                TARGET_USER, PASSWORD_HASH, CREATED_AT,
                WAITING_USER, PASSWORD_HASH, CREATED_AT);
        jdbcTemplate.update("INSERT INTO region (region_id, parent_region_id, name) VALUES (?, NULL, ?)",
                REGION_ID, "신고 테스트 지역");
        jdbcTemplate.update("INSERT INTO sport (sport_id, name) VALUES (?, ?)", SPORT_ID, "신고 테스트 종목");
        jdbcTemplate.update("""
                INSERT INTO facility (
                    facility_id, region_id, name, address, latitude, longitude,
                    concurrent_match_limit, status, deleted_at
                ) VALUES
                    (?, ?, '신고 테스트 시설 A', '주소 A', 37.5000000, 127.0000000, 3, '운영 중', NULL),
                    (?, ?, '신고 테스트 시설 B', '주소 B', 37.5000000, 127.0000000, 3, '운영 중', NULL)
                """, FACILITY_A, REGION_ID, FACILITY_B, REGION_ID);
        jdbcTemplate.update("""
                INSERT INTO facility_sport (
                    facility_id, sport_id, default_min, default_max, default_fee, default_skill
                ) VALUES (?, ?, 1, 4, 0.00, '무관'), (?, ?, 1, 4, 0.00, '무관')
                """, FACILITY_A, SPORT_ID, FACILITY_B, SPORT_ID);
        jdbcTemplate.update("""
                INSERT INTO facility_manager (facility_id, user_id, assigned_at) VALUES
                    (?, ?, ?), (?, ?, ?)
                """, FACILITY_A, MANAGER_A, CREATED_AT, FACILITY_B, MANAGER_B, CREATED_AT);
        // 시작 시각이 최신순 정렬 기준이다. CLOSED_MATCH > OLD_CLOSED_MATCH 순으로 노출된다.
        jdbcTemplate.update("""
                INSERT INTO social_match (
                    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                    min_participants, max_participants, team_a_capacity, team_b_capacity,
                    fee_per_person, required_skill, status, assignment_method, rating_status
                ) VALUES
                    (?, ?, ?, ?, '종료 경기', '2026-11-03 10:00:00', '2026-11-03 12:00:00',
                     1, 4, 2, 2, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
                    (?, ?, ?, ?, '모집 중 경기', '2026-11-05 10:00:00', '2026-11-05 12:00:00',
                     1, 4, 2, 2, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
                    (?, ?, ?, ?, '다른 관리자 종료 경기', '2026-11-02 10:00:00', '2026-11-02 12:00:00',
                     1, 4, 2, 2, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
                    (?, ?, ?, ?, '오래된 종료 경기', '2026-11-01 10:00:00', '2026-11-01 12:00:00',
                     1, 4, 2, 2, 0.00, '무관', '종료', 'MANUAL', 'APPLIED')
                """,
                CLOSED_MATCH, FACILITY_A, SPORT_ID, MANAGER_A,
                RECRUITING_MATCH, FACILITY_A, SPORT_ID, MANAGER_A,
                OTHER_MANAGER_CLOSED_MATCH, FACILITY_B, SPORT_ID, MANAGER_B,
                OLD_CLOSED_MATCH, FACILITY_A, SPORT_ID, MANAGER_A);
        jdbcTemplate.update("""
                INSERT INTO match_application (
                    application_id, match_id, applicant_user_id, club_id, application_type,
                    status, receipt_no, created_at
                ) VALUES
                    (?, ?, ?, NULL, '개인', '확정', 'REPORT-RCPT-1', ?),
                    (?, ?, ?, NULL, '개인', '확정', 'REPORT-RCPT-2', ?),
                    (?, ?, ?, NULL, '개인', '확정', 'REPORT-RCPT-3', ?),
                    (?, ?, ?, NULL, '개인', '확정', 'REPORT-RCPT-4', ?)
                """,
                990001L, CLOSED_MATCH, TARGET_USER, CREATED_AT,
                990002L, CLOSED_MATCH, WAITING_USER, CREATED_AT,
                990003L, OTHER_MANAGER_CLOSED_MATCH, TARGET_USER, CREATED_AT,
                990004L, OLD_CLOSED_MATCH, TARGET_USER, CREATED_AT);
        // 종료 경기의 확정 참가자 한 명과 대기 한 명(대상 선택지에서 빠져야 한다).
        jdbcTemplate.update("""
                INSERT INTO match_participant (
                    participant_id, application_id, match_id, user_id, participation_status,
                    match_side, entry_mmr, joined_at
                ) VALUES
                    (?, ?, ?, ?, '확정', 'A', 1000.00, ?),
                    (?, ?, ?, ?, '대기', NULL, 1000.00, ?),
                    (?, ?, ?, ?, '확정', 'A', 1000.00, ?)
                """,
                990001L, 990001L, CLOSED_MATCH, TARGET_USER, CREATED_AT,
                990002L, 990002L, CLOSED_MATCH, WAITING_USER, CREATED_AT,
                990003L, 990004L, OLD_CLOSED_MATCH, TARGET_USER, CREATED_AT);
    }

    @Test
    @DisplayName("경기 선택지는 담당 활성 시설의 종료 경기만 최신순 offset 페이지로 준다")
    void reportableMatchesOnlyClosedAndManaged() {
        List<ReportMatchRow> firstPage =
                reportMapper.findReportableMatches(MANAGER_A, MatchStatus.CLOSED.dbValue(), 2, 0);

        // 시작 시각 내림차순: CLOSED_MATCH(11-03) → OLD_CLOSED_MATCH(11-01). 모집 중·타 시설은 제외.
        assertThat(firstPage).extracting(ReportMatchRow::getMatchId)
                .containsExactly(CLOSED_MATCH, OLD_CLOSED_MATCH);

        List<ReportMatchRow> secondPage =
                reportMapper.findReportableMatches(MANAGER_A, MatchStatus.CLOSED.dbValue(), 2, 2);
        assertThat(secondPage).isEmpty();

        // 다른 관리자의 종료 경기는 그 관리자에게만 보인다.
        assertThat(reportMapper.findReportableMatches(MANAGER_B, MatchStatus.CLOSED.dbValue(), 10, 0))
                .extracting(ReportMatchRow::getMatchId)
                .containsExactly(OTHER_MANAGER_CLOSED_MATCH);
    }

    @Test
    @DisplayName("페이지 밖 종료 경기도 담당 관리자에게는 단건 선택 조회로 열린다")
    void linkedMatchLookupReturnsManagedClosedMatch() {
        // 1건 페이지에서 두 번째 경기는 목록에 없지만 링크 선택으로는 조회된다.
        assertThat(reportMapper.findReportableMatches(MANAGER_A, MatchStatus.CLOSED.dbValue(), 1, 0))
                .extracting(ReportMatchRow::getMatchId)
                .containsExactly(CLOSED_MATCH);

        ReportMatchRow linked = reportMapper.findReportableMatch(MANAGER_A, OLD_CLOSED_MATCH,
                MatchStatus.CLOSED.dbValue());
        assertThat(linked).isNotNull();
        assertThat(linked.getMatchId()).isEqualTo(OLD_CLOSED_MATCH);

        // 미종료 경기와 타인 담당 경기는 단건 조회에서도 나오지 않는다(다른 정보 누출 없음).
        assertThat(reportMapper.findReportableMatch(MANAGER_A, RECRUITING_MATCH,
                MatchStatus.CLOSED.dbValue())).isNull();
        assertThat(reportMapper.findReportableMatch(MANAGER_A, OTHER_MANAGER_CLOSED_MATCH,
                MatchStatus.CLOSED.dbValue())).isNull();
    }

    @Test
    @DisplayName("대상 선택지는 확정 참가자 전부를 남기고 대기 참가자는 제외한다")
    void participantsOnlyConfirmedWithoutTruncation() {
        // 경기당 참가자가 많아도 SQL이 임의 건수로 자르지 않는지, 확정 참가자를 모두 돌려주는지 본다.
        jdbcTemplate.update("""
                INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)
                VALUES (?, 'report-bulk@example.invalid', ?, '대량 참가자', '010-9900-9999', '일반 회원', ?)
                """, 991000L, PASSWORD_HASH, CREATED_AT);
        jdbcTemplate.update("""
                INSERT INTO match_application (
                    application_id, match_id, applicant_user_id, club_id, application_type,
                    status, receipt_no, created_at
                ) VALUES (?, ?, ?, NULL, '개인', '확정', 'REPORT-BULK-1', ?)
                """, 991000L, OLD_CLOSED_MATCH, 991000L, CREATED_AT);
        jdbcTemplate.update("""
                INSERT INTO match_participant (
                    participant_id, application_id, match_id, user_id, participation_status,
                    match_side, entry_mmr, joined_at
                ) VALUES (?, ?, ?, ?, '확정', 'A', 1000.00, ?)
                """, 991000L, 991000L, OLD_CLOSED_MATCH, 991000L, CREATED_AT);

        List<ReportParticipantRow> participants = reportMapper.findParticipants(CLOSED_MATCH,
                ParticipationStatus.CONFIRMED.dbValue());

        assertThat(participants).extracting(ReportParticipantRow::getUserId).containsExactly(TARGET_USER);
        assertThat(reportMapper.existsParticipant(CLOSED_MATCH, WAITING_USER,
                ParticipationStatus.CONFIRMED.dbValue())).isFalse();
        assertThat(reportMapper.existsParticipant(CLOSED_MATCH, TARGET_USER,
                ParticipationStatus.CONFIRMED.dbValue())).isTrue();

        // 확정 참가자가 둘인 경기는 두 명 모두(대량 참가자 포함) 돌려준다.
        assertThat(reportMapper.findParticipants(OLD_CLOSED_MATCH, ParticipationStatus.CONFIRMED.dbValue()))
                .extracting(ReportParticipantRow::getUserId)
                .containsExactlyInAnyOrder(TARGET_USER, 991000L);
    }

    @Test
    @DisplayName("시설 소유권은 담당 활성 시설에만 인정한다")
    void facilityManagerScope() {
        assertThat(reportMapper.existsFacilityManager(FACILITY_A, MANAGER_A)).isTrue();
        assertThat(reportMapper.existsFacilityManager(FACILITY_A, MANAGER_B)).isFalse();
        assertThat(reportMapper.existsFacilityManager(FACILITY_B, MANAGER_A)).isFalse();
    }

    @Test
    @DisplayName("신고는 접수 상태로 저장되고 생성 키를 받는다")
    void insertsReceivedReport() {
        Report report = new Report();
        report.setReporterUserId(MANAGER_A);
        report.setTargetUserId(TARGET_USER);
        report.setMatchId(CLOSED_MATCH);
        report.setCategory("노쇼");
        report.setSummary("경기에 나오지 않았습니다.");
        report.setStatus("접수");

        assertThat(reportMapper.insertReport(report)).isEqualTo(1);
        assertThat(report.getReportId()).isNotNull();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM report WHERE report_id = ?", String.class, report.getReportId());
        assertThat(status).isEqualTo("접수");
    }

    @Test
    @DisplayName("시설 관리자 목록은 본인이 접수하고 현재 담당 시설인 신고만, 원문 없이 offset 페이지로 준다")
    void facilityReportsAreOwnAndScoped() {
        long ownReport = insertReportMinutesAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "기타", "본인 신고", "접수", 5);
        // 다른 사람이 접수한 같은 경기 신고.
        insertReportMinutesAgo(MANAGER_B, TARGET_USER, CLOSED_MATCH, "기타", "남의 신고", "접수", 5);
        // 본인이 접수했지만 다른 관리자 시설의 경기 신고.
        insertReportMinutesAgo(MANAGER_A, TARGET_USER, OTHER_MANAGER_CLOSED_MATCH, "기타", "다른 시설 신고", "접수", 5);

        List<ReportRow> rows = reportMapper.findReportsForFacilityManager(MANAGER_A, MANAGER_A, 50, 0);

        assertThat(rows).extracting(ReportRow::getReportId).containsExactly(ownReport);
        assertThat(rows.get(0).getSummary()).isNull();

        // 두 번째 페이지(offset 1)는 빈 페이지다.
        assertThat(reportMapper.findReportsForFacilityManager(MANAGER_A, MANAGER_A, 50, 1)).isEmpty();

        // 권한 밖 신고 상세는 단건 조회로도 나오지 않는다.
        assertThat(reportMapper.findFacilityManagerReport(MANAGER_A, MANAGER_A, ownReport)).isNotNull();
        assertThat(reportMapper.findReportsForFacilityManager(MANAGER_B, MANAGER_B, 50, 0))
                .extracting(ReportRow::getReportId)
                .doesNotContain(ownReport);
    }

    @Test
    @DisplayName("상세 조회만 신고 원문을 담는다")
    void detailIncludesSummary() {
        long reportId = insertReportMinutesAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "기타", "상세 원문", "접수", 1);

        ReportRow row = reportMapper.findFacilityManagerReport(MANAGER_A, MANAGER_A, reportId);

        assertThat(row).isNotNull();
        assertThat(row.getSummary()).isEqualTo("상세 원문");
        assertThat(row.getTargetName()).isEqualTo("신고 대상자");
    }

    @Test
    @DisplayName("상태 조건부 갱신은 기대 상태가 다르면 0행이고 끝난 건은 재처리되지 않는다")
    void updatesOnlyOnExpectedStatus() {
        long reportId = insertReportMinutesAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "기타", "상태 조건부", "접수", 1);

        // 접수는 검토 중으로만 갈 수 있다(검토 중 → 검토 중 재처리는 0행).
        assertThat(reportMapper.markReviewing(reportId, "검토 중", "검토 중", MANAGER_A)).isZero();
        assertThat(reportMapper.markReviewing(reportId, "접수", "검토 중", MANAGER_A)).isEqualTo(1);
        // 기대 상태가 어긋나면 0행, 검토 중에서는 최종 처리가 허용된다.
        assertThat(reportMapper.applyFinalDecision(reportId, "접수", "처리 완료", "사유", MANAGER_A)).isZero();
        assertThat(reportMapper.applyFinalDecision(reportId, "검토 중", "처리 완료", "사유", MANAGER_A))
                .isEqualTo(1);
        // 완결 상태는 expectedStatus가 그대로여도 SQL의 허용 원본 상태 조건에 걸려 다시 바뀌지 않는다.
        assertThat(reportMapper.applyFinalDecision(reportId, "처리 완료", "기각", "다른 사유", MANAGER_A))
                .isZero();
        assertThat(reportMapper.markReviewing(reportId, "처리 완료", "검토 중", MANAGER_A)).isZero();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM report WHERE report_id = ?", String.class, reportId);
        String resolution = jdbcTemplate.queryForObject(
                "SELECT resolution FROM report WHERE report_id = ?", String.class, reportId);
        assertThat(status).isEqualTo("처리 완료");
        assertThat(resolution).isEqualTo("사유");
    }

    @Test
    @DisplayName("기각된 신고도 DB 허용 원본 상태 조건으로 다시 처리되지 않는다")
    void dismissedReportIsTerminalAtSqlLevel() {
        long reportId = insertReportMinutesAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "기타", "기각 완결", "접수", 1);

        assertThat(reportMapper.applyFinalDecision(reportId, "접수", "기각", "기각 사유", MANAGER_A)).isEqualTo(1);
        // expectedStatus를 실제 값(기각)으로 맞춰도 완결 상태는 원본 상태 허용 조건에 걸려 0행이다.
        assertThat(reportMapper.applyFinalDecision(reportId, "기각", "처리 완료", "재처리", MANAGER_A)).isZero();
        assertThat(reportMapper.markReviewing(reportId, "기각", "검토 중", MANAGER_A)).isZero();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM report WHERE report_id = ?", String.class, reportId)).isEqualTo("기각");
    }

    @Test
    @DisplayName("중복 조회는 DB 현재 시각 기준 10초 이내 행만 찾고 오래된 동일 내용은 잡지 않는다")
    void duplicateLookupUsesDbClockWindow() {
        long recent = insertReportSecondsAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "기타", "같은 원문", "접수", 3);
        // 10초를 넘긴 동일 내용은 중복이 아니다.
        long stale = insertReportSecondsAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "기타", "같은 원문", "접수", 40);

        Long found = reportMapper.findRecentDuplicate(MANAGER_A, CLOSED_MATCH, TARGET_USER,
                "기타", "같은 원문", 10);

        // 창 안의 가장 최근 행만 잡는다(오래된 stale 행이 아니라 recent).
        assertThat(found).isEqualTo(recent);
        assertThat(found).isNotEqualTo(stale);
    }

    @Test
    @DisplayName("중복 조회는 대소문자·악센트가 다른 별개 진술을 동일시하지 않는다")
    void duplicateLookupComparesSummaryExactly() {
        insertReportSecondsAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "기타", "No Show", "접수", 2);

        // 기본 collation(ai_ci)이면 "no show"가 같은 값으로 묶이지만, BINARY 비교라 별개다.
        assertThat(reportMapper.findRecentDuplicate(MANAGER_A, CLOSED_MATCH, TARGET_USER,
                "기타", "no show", 10)).isNull();
        // 악센트만 다른 진술도 별개다.
        insertReportSecondsAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "기타", "café", "접수", 2);
        assertThat(reportMapper.findRecentDuplicate(MANAGER_A, CLOSED_MATCH, TARGET_USER,
                "기타", "cafe", 10)).isNull();
        // 정확히 같은 바이트의 진술만 중복이다.
        assertThat(reportMapper.findRecentDuplicate(MANAGER_A, CLOSED_MATCH, TARGET_USER,
                "기타", "No Show", 10)).isNotNull();
    }

    @Test
    @DisplayName("중복 조회는 신고자·경기·대상·유형이 다른 행을 잡지 않는다")
    void duplicateLookupRespectsOtherKeys() {
        long recent = insertReportSecondsAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "기타", "공통 원문", "접수", 2);
        insertReportSecondsAgo(MANAGER_B, TARGET_USER, CLOSED_MATCH, "기타", "공통 원문", "접수", 2);
        insertReportSecondsAgo(MANAGER_A, WAITING_USER, CLOSED_MATCH, "기타", "공통 원문", "접수", 2);
        insertReportSecondsAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "노쇼", "공통 원문", "접수", 2);

        assertThat(reportMapper.findRecentDuplicate(MANAGER_A, CLOSED_MATCH, TARGET_USER,
                "기타", "공통 원문", 10)).isEqualTo(recent);
        assertThat(reportMapper.findRecentDuplicate(MANAGER_A, CLOSED_MATCH, TARGET_USER,
                "폭력·싸움", "공통 원문", 10)).isNull();
    }

    @Test
    @DisplayName("중복 후보 잠금은 PK로 그 행만 잠그고 없는 번호는 null이다")
    void lockReportByIdLocksCandidate() {
        long reportId = insertReportSecondsAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "기타", "잠금 대상", "접수", 2);

        assertThat(reportMapper.lockReportById(reportId)).isEqualTo(reportId);
        assertThat(reportMapper.lockReportById(999999L)).isNull();
    }

    @Test
    @DisplayName("시스템 목록은 원문 없이 신고자 이름을 담고 offset 페이지로 자르며, 상세는 원문을 담는다")
    void systemListAndDetail() {
        long reportId = insertReportMinutesAgo(MANAGER_A, TARGET_USER, CLOSED_MATCH, "폭력·싸움", "시스템 원문", "접수", 1);

        List<ReportRow> rows = reportMapper.findSystemReports("접수", 100, 0);
        ReportRow row = rows.stream().filter(item -> reportId == item.getReportId()).findFirst().orElseThrow();

        assertThat(row.getReporterName()).isEqualTo("신고 관리자 A");
        assertThat(row.getSummary()).isNull();

        // offset이 목록 끝을 넘으면 빈 페이지다.
        assertThat(reportMapper.findSystemReports("접수", 100, 5)).isEmpty();

        ReportRow detail = reportMapper.findSystemReport(reportId);
        assertThat(detail.getSummary()).isEqualTo("시스템 원문");
        assertThat(reportMapper.findSystemReports("기각", 100, 0))
                .extracting(ReportRow::getReportId)
                .doesNotContain(reportId);
    }

    @Test
    @DisplayName("경기 스냅샷은 종료·모집 중 상태와 시설 매핑을 잠금 조회로 읽는다")
    void matchSnapshotReadsStatus() {
        var closed = reportMapper.findMatchSnapshotForUpdate(CLOSED_MATCH);
        var recruiting = reportMapper.findMatchSnapshotForUpdate(RECRUITING_MATCH);

        assertThat(closed.getStatus()).isEqualTo(MatchStatus.CLOSED.dbValue());
        assertThat(closed.getFacilityId()).isEqualTo(FACILITY_A);
        assertThat(recruiting.getStatus()).isEqualTo(MatchStatus.RECRUITING.dbValue());
        assertThat(reportMapper.findMatchSnapshotForUpdate(999999L)).isNull();
    }

    private long insertReportMinutesAgo(long reporterId, long targetId, long matchId, String category,
            String summary, String status, int minutesAgo) {
        return insertReportAt(reporterId, targetId, matchId, category, summary, status,
                dbNow().minusMinutes(minutesAgo));
    }

    private long insertReportSecondsAgo(long reporterId, long targetId, long matchId, String category,
            String summary, String status, int secondsAgo) {
        return insertReportAt(reporterId, targetId, matchId, category, summary, status,
                dbNow().minusSeconds(secondsAgo));
    }

    /**
     * 신고 한 건을 지정 시각으로 저장한다. 중복 창 판정은 SQL이 DB CURRENT_TIMESTAMP로 하므로,
     * 테스트도 DB 기준 현재 시각으로 상대 시각을 만든다.
     */
    private long insertReportAt(long reporterId, long targetId, long matchId, String category,
            String summary, String status, LocalDateTime createdAt) {
        jdbcTemplate.update("""
                INSERT INTO report (
                    reporter_user_id, target_user_id, match_id, reviewed_by,
                    category, summary, status, resolution, created_at, reviewed_at
                ) VALUES (?, ?, ?, NULL, ?, ?, ?, NULL, ?, NULL)
                """, reporterId, targetId, matchId, category, summary, status, createdAt);
        Long reportId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return reportId == null ? 0L : reportId;
    }

    /** DB의 현재 시각을 자바 시각으로 읽는다. fixture 시각이 DB 시계와 어긋나지 않게 한다. */
    private LocalDateTime dbNow() {
        java.sql.Timestamp timestamp =
                jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", java.sql.Timestamp.class);
        return timestamp == null ? LocalDateTime.now() : timestamp.toLocalDateTime();
    }

    static String validateUrl(String url) {
        var matcher = TEST_DATABASE_URL.matcher(url == null ? "" : url);
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
