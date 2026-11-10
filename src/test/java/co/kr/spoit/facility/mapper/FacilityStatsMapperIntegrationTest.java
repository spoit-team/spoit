package co.kr.spoit.facility.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.assertj.core.groups.Tuple;
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

import co.kr.spoit.facility.dto.ApplicationCountSummary;
import co.kr.spoit.facility.dto.WeekdayHourParticipantCount;

/**
 * 시설 관리자 예약 통계 SQL을 전용 MySQL DB에서만 검증한다.
 *
 * <p>{@code MAT_TEST_DB_URL} 등 환경변수가 없으면 실행되지 않는다. fixture는 테스트 트랜잭션 안에서만
 * 저장되고 종료 시 롤백되므로 개발·운영 DB에는 흔적을 남기지 않는다.</p>
 *
 * <p>검증 기간은 2026-10-05(월) 09:00 ~ 2026-10-09(금) 18:00이며, 같은 시설에 기간 경계값·모집 중·
 * 매칭 완료·취소 매칭방과 다른 담당자의 시설 매칭방을 함께 둬 대상 필터를 확인한다. 요일·시간대
 * 집계가 매칭방 수가 아니라 종료 매칭의 확정 참가 인원임을 확인하기 위해 화요일 매칭방 하나에
 * 확정 참가자 2명을 두고, 같은 회원이 두 경기에 참여하는 반복 참가자도 넣는다. 요일별·시간대별
 * 합계는 요일×시간대 집계 한 번에서 서비스가 파생하므로, 이 테스트도 같은 방식으로 접어 검증한다.</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never",
        "mybatis.configuration.map-underscore-to-camel-case=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FacilityStatsMapperIntegrationTest {

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    /** fixture에서 관리자 A(9501)가 담당하는 시설. */
    private static final long FACILITY_ID = 9601L;
    /** fixture의 시설 관리자 A. */
    private static final long MANAGER_USER_ID = 9501L;
    /** fixture의 시설 관리자 B. 담당 범위 밖 검증에 쓴다. */
    private static final long OTHER_MANAGER_USER_ID = 9503L;
    /** 관리자 A가 담당하지 않는 다른 시설. */
    private static final long OTHER_FACILITY_ID = 9602L;

    private static final LocalDateTime RANGE_START = LocalDateTime.of(2026, 10, 5, 9, 0);
    private static final LocalDateTime RANGE_END = LocalDateTime.of(2026, 10, 9, 18, 0);

    @Autowired
    private FacilityStatsMapper statsMapper;

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

    /**
     * 테스트 트랜잭션 안에서만 쓰는 fixture다. 기간 안의 종료 매칭 3건(월 09시, 화 10시, 금 18시)과
     * 기간 밖·모집 중·매칭 완료·취소·다른 담당자 매칭방을 함께 넣는다.
     */
    @BeforeEach
    void insertFixture() {
        jdbcTemplate.update("""
                INSERT INTO app_user (
                    user_id, email, password_hash, nickname, phone, role, created_at
                ) VALUES
                    (9501, 'stats-manager-a@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
                     '통계 관리자 A', '010-9500-0001', '시설 관리자', '2026-10-01 09:00:00'),
                    (9502, 'stats-user-1@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
                     '통계 테스트 회원 1', '010-9500-0002', '일반 회원', '2026-10-01 09:00:00'),
                    (9503, 'stats-manager-b@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
                     '통계 관리자 B', '010-9500-0003', '시설 관리자', '2026-10-01 09:00:00'),
                    (9504, 'stats-user-2@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
                     '통계 테스트 회원 2', '010-9500-0004', '일반 회원', '2026-10-01 09:00:00'),
                    (9505, 'stats-user-3@example.invalid', 'TEST_ONLY_NOT_A_PASSWORD_HASH',
                     '통계 테스트 회원 3', '010-9500-0005', '일반 회원', '2026-10-01 09:00:00')
                """);
        jdbcTemplate.update("""
                INSERT INTO sport (sport_id, name) VALUES (9701, '통계 테스트 종목')
                """);
        jdbcTemplate.update("""
                INSERT INTO region (region_id, parent_region_id, name) VALUES
                    (9701, NULL, '통계 테스트 지역')
                """);
        jdbcTemplate.update("""
                INSERT INTO facility (
                    facility_id, region_id, name, address, latitude, longitude,
                    concurrent_match_limit, status, deleted_at
                ) VALUES
                    (9601, 9701, '통계 테스트 시설 A', '테스트 주소 A', 37.5000000, 127.0000000,
                     3, '운영 중', NULL),
                    (9602, 9701, '통계 테스트 시설 B', '테스트 주소 B', 37.5001000, 127.0001000,
                     3, '운영 중', NULL)
                """);
        jdbcTemplate.update("""
                INSERT INTO facility_sport (
                    facility_id, sport_id, default_min, default_max, default_fee, default_skill
                ) VALUES
                    (9601, 9701, 1, 2, 0.00, '무관'),
                    (9602, 9701, 1, 2, 0.00, '무관')
                """);
        jdbcTemplate.update("""
                INSERT INTO facility_manager (facility_id, user_id, assigned_at) VALUES
                    (9601, 9501, '2026-10-01 09:00:00'),
                    (9602, 9503, '2026-10-01 09:00:00')
                """);
        // 종료 3건만 대상이다. 9804(모집 중)·9805(매칭 완료)·9808(취소)는 상태로,
        // 9802·9807은 기간 경계로, 9809는 다른 시설·담당자로 빠진다.
        jdbcTemplate.update("""
                INSERT INTO social_match (
                    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                    min_participants, max_participants, team_a_capacity, team_b_capacity,
                    fee_per_person, required_skill, status, assignment_method, rating_status
                ) VALUES
                    (9801, 9601, 9701, 9501, '통계 종료 시작 경계', '2026-10-05 09:00:00', '2026-10-05 11:00:00',
                     1, 2, 1, 1, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
                    (9802, 9601, 9701, 9501, '통계 기간 이전', '2026-10-05 08:59:59', '2026-10-05 10:59:59',
                     1, 2, 1, 1, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
                    (9803, 9601, 9701, 9501, '통계 종료 화요일', '2026-10-06 10:00:00', '2026-10-06 12:00:00',
                     1, 2, 1, 1, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
                    (9804, 9601, 9701, 9501, '통계 모집 중 수요일', '2026-10-07 14:00:00', '2026-10-07 16:00:00',
                     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
                    (9805, 9601, 9701, 9501, '통계 매칭 완료 목요일', '2026-10-08 10:00:00', '2026-10-08 12:00:00',
                     1, 2, 1, 1, 0.00, '무관', '매칭 완료', 'MANUAL', 'WAITING'),
                    (9806, 9601, 9701, 9501, '통계 종료 끝 경계', '2026-10-09 18:00:00', '2026-10-09 20:00:00',
                     1, 2, 1, 1, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
                    (9807, 9601, 9701, 9501, '통계 기간 이후', '2026-10-09 18:00:01', '2026-10-09 20:00:01',
                     1, 2, 1, 1, 0.00, '무관', '종료', 'MANUAL', 'APPLIED'),
                    (9808, 9601, 9701, 9501, '통계 취소 매칭방', '2026-10-06 10:00:00', '2026-10-06 12:00:00',
                     1, 2, 1, 1, 0.00, '무관', '취소', 'MANUAL', 'EXCLUDED'),
                    (9809, 9602, 9701, 9503, '통계 다른 담당자 시설', '2026-10-07 11:00:00', '2026-10-07 13:00:00',
                     1, 2, 1, 1, 0.00, '무관', '종료', 'MANUAL', 'APPLIED')
                """);
        jdbcTemplate.update("""
                INSERT INTO match_application (
                    application_id, match_id, applicant_user_id, club_id, application_type,
                    status, receipt_no, created_at, confirmed_at, cancelled_at, cancelled_by,
                    group_application_id
                ) VALUES
                    (9901, 9801, 9502, NULL, '개인', '확정', 'STATS-RECEIPT-9901',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL),
                    (9902, 9803, 9502, NULL, '동호회', '확정', 'STATS-RECEIPT-9902',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, 9902),
                    (9903, 9803, 9503, NULL, '동호회', '대기', 'STATS-RECEIPT-9903',
                     '2026-10-01 09:00:00', NULL, NULL, NULL, 9902),
                    (9904, 9803, 9504, NULL, '동호회', '확정', 'STATS-RECEIPT-9904',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, 9902),
                    (9905, 9806, 9505, NULL, '개인', '확정', 'STATS-RECEIPT-9905',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL),
                    (9906, 9804, 9502, NULL, '개인', '확정', 'STATS-RECEIPT-9906',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL),
                    (9907, 9805, 9503, NULL, '개인', '확정', 'STATS-RECEIPT-9907',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL),
                    (9908, 9808, 9502, NULL, '개인', '확정', 'STATS-RECEIPT-9908',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL),
                    (9909, 9802, 9502, NULL, '개인', '확정', 'STATS-RECEIPT-9909',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL),
                    (9910, 9807, 9504, NULL, '개인', '확정', 'STATS-RECEIPT-9910',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL),
                    (9911, 9809, 9503, NULL, '개인', '확정', 'STATS-RECEIPT-9911',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL)
                """);
        // 9921이 확정이므로 9801은 참가 인원 1명, 9803은 확정 2명(9931·9933)이다.
        // 회원 9502는 9801과 9803에 모두 참여해 2인으로 집계된다(반복 참가자).
        jdbcTemplate.update("""
                INSERT INTO match_participant (
                    participant_id, application_id, match_id, user_id, participation_status,
                    match_side, entry_mmr, joined_at, cancelled_at
                ) VALUES
                    (9921, 9901, 9801, 9502, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9922, 9901, 9801, 9503, '대기', NULL, 1000.00, '2026-10-01 09:00:00', NULL),
                    (9923, 9901, 9801, 9504, '취소', 'B', 1000.00, '2026-10-01 09:00:00', '2026-10-03 09:00:00'),
                    (9931, 9902, 9803, 9502, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9932, 9903, 9803, 9503, '대기', NULL, 1000.00, '2026-10-01 09:00:00', NULL),
                    (9933, 9904, 9803, 9504, '확정', 'B', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9941, 9905, 9806, 9505, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9951, 9906, 9804, 9502, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9961, 9907, 9805, 9503, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9971, 9908, 9808, 9502, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9981, 9909, 9802, 9502, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9991, 9910, 9807, 9504, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9992, 9911, 9809, 9503, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL)
                """);
    }

    @Test
    @DisplayName("종료 매칭방만 기간 양끝을 포함해 세고 모집 중·매칭 완료·취소는 제외한다")
    void countsOnlyFinishedMatchesInRange() {
        long count = statsMapper.countFinishedMatches(MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END);

        // 9801(시작 경계), 9803, 9806(끝 경계)만 대상이다.
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("종료 매칭의 확정 참가자 행만 세고 대기·취소·다른 상태 매칭방 인원은 제외한다")
    void countsConfirmedParticipantsOnly() {
        long participants =
                statsMapper.countConfirmedParticipants(MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END);

        // 9921(9801) + 9931·9933(9803) + 9941(9806) = 4. 9922·9932(대기), 9923(취소)는 빠지고,
        // 모집 중(9951)·매칭 완료(9961)·취소 매칭방(9971)·기간 밖(9981·9991)·다른 시설(9992)도 빠진다.
        // 회원 9502가 9921·9931에 두 번 참여해 2인으로 집계된다.
        assertThat(participants).isEqualTo(4);
    }

    @Test
    @DisplayName("신청 지표도 종료 매칭방으로 제한하고 팀 신청은 대표 행만 센다")
    void countsRepresentativeApplicationRowsOfFinishedMatches() {
        ApplicationCountSummary summary =
                statsMapper.countApplications(MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END);

        // 9901(개인) + 9902(동호회 대표) + 9905(개인) = 3, 팀원 9903·9904는 제외.
        // 모집 중(9906)·매칭 완료(9907)·취소(9908)·기간 밖(9909·9910)·다른 시설(9911)도 제외.
        assertThat(summary.getTotal()).isEqualTo(3);
        assertThat(summary.getIndividualCount()).isEqualTo(2);
        assertThat(summary.getClubCount()).isEqualTo(1);
        assertThat(summary.getApprovedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("요일별 확정 참가 인원은 요일×시간대 집계의 요일 합계로 방 수보다 큰 값을 돌려준다")
    void derivesWeekdayTotalsFromWeekdayHourGroups() {
        var counts = statsMapper.countParticipantsByWeekdayAndHour(MANAGER_USER_ID, FACILITY_ID, RANGE_START,
                RANGE_END);

        // 종료 방은 3건이지만 확정 참가 인원은 4명이다. 화요일은 방 1건에 참가자 2명이다.
        // 서비스가 쓰는 것과 같은 파생 방식(요일 합계)으로 검증한다.
        assertThat(weekdayTotals(counts))
                .containsExactlyInAnyOrder(
                        tuple(2, 1L),  // 월
                        tuple(3, 2L),  // 화
                        tuple(6, 1L)); // 금
    }

    @Test
    @DisplayName("시간대별 확정 참가 인원은 요일×시간대 집계의 시간대 합계로 파생한다")
    void derivesHourTotalsFromWeekdayHourGroups() {
        var counts = statsMapper.countParticipantsByWeekdayAndHour(MANAGER_USER_ID, FACILITY_ID, RANGE_START,
                RANGE_END);

        assertThat(hourTotals(counts))
                .containsExactlyInAnyOrder(
                        tuple(9, 1L),
                        tuple(10, 2L),
                        tuple(18, 1L));
    }

    @Test
    @DisplayName("요일×시작 시간대별로 종료 매칭의 확정 참가 인원을 함께 묶는다")
    void groupsParticipantCountByWeekdayAndHour() {
        var counts = statsMapper.countParticipantsByWeekdayAndHour(MANAGER_USER_ID, FACILITY_ID, RANGE_START,
                RANGE_END);

        assertThat(counts)
                .extracting(WeekdayHourParticipantCount::getWeekday, WeekdayHourParticipantCount::getHour,
                        WeekdayHourParticipantCount::getParticipantCount)
                .containsExactlyInAnyOrder(
                        tuple(2, 9, 1L),   // 월 09시
                        tuple(3, 10, 2L),  // 화 10시
                        tuple(6, 18, 1L)); // 금 18시
    }

    @Test
    @DisplayName("담당하지 않은 관리자나 다른 시설로 조회하면 모든 지표가 0이다")
    void scopesEveryQueryToManagerAndFacility() {
        assertThat(statsMapper.countFinishedMatches(OTHER_MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END))
                .isZero();
        assertThat(statsMapper.countConfirmedParticipants(OTHER_MANAGER_USER_ID, FACILITY_ID, RANGE_START,
                RANGE_END)).isZero();
        var otherCounts = statsMapper.countParticipantsByWeekdayAndHour(OTHER_MANAGER_USER_ID, FACILITY_ID,
                RANGE_START, RANGE_END);
        assertThat(otherCounts).isEmpty();
        assertThat(weekdayTotals(otherCounts)).isEmpty();
        assertThat(hourTotals(otherCounts)).isEmpty();

        ApplicationCountSummary summary =
                statsMapper.countApplications(OTHER_MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END);
        assertThat(summary.getTotal()).isZero();
        assertThat(summary.getIndividualCount()).isZero();
        assertThat(summary.getClubCount()).isZero();
        assertThat(summary.getApprovedCount()).isZero();

        // 관리자 A가 담당하지 않은 시설(9602)도 같은 방식으로 0건이다.
        assertThat(statsMapper.countFinishedMatches(MANAGER_USER_ID, OTHER_FACILITY_ID, RANGE_START, RANGE_END))
                .isZero();
        assertThat(statsMapper.countConfirmedParticipants(MANAGER_USER_ID, OTHER_FACILITY_ID, RANGE_START,
                RANGE_END)).isZero();
        var otherFacilityCounts = statsMapper.countParticipantsByWeekdayAndHour(MANAGER_USER_ID,
                OTHER_FACILITY_ID, RANGE_START, RANGE_END);
        assertThat(otherFacilityCounts).isEmpty();
        assertThat(weekdayTotals(otherFacilityCounts)).isEmpty();
    }

    @Test
    @DisplayName("대상 기간에 종료 매칭이 없으면 모든 지표가 0건이고 파생 합계도 비어 있다")
    void returnsZeroWhenNoFinishedMatchesInRange() {
        LocalDateTime emptyStart = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime emptyEnd = LocalDateTime.of(2026, 11, 30, 23, 59, 59);

        assertThat(statsMapper.countFinishedMatches(MANAGER_USER_ID, FACILITY_ID, emptyStart, emptyEnd))
                .isZero();
        assertThat(statsMapper.countConfirmedParticipants(MANAGER_USER_ID, FACILITY_ID, emptyStart, emptyEnd))
                .isZero();
        var counts = statsMapper.countParticipantsByWeekdayAndHour(MANAGER_USER_ID, FACILITY_ID, emptyStart,
                emptyEnd);
        assertThat(counts).isEmpty();
        assertThat(weekdayTotals(counts)).isEmpty();
        assertThat(hourTotals(counts)).isEmpty();

        ApplicationCountSummary summary =
                statsMapper.countApplications(MANAGER_USER_ID, FACILITY_ID, emptyStart, emptyEnd);
        assertThat(summary.getTotal()).isZero();
        assertThat(summary.getIndividualCount()).isZero();
        assertThat(summary.getClubCount()).isZero();
        assertThat(summary.getApprovedCount()).isZero();
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

    /** 서비스와 같은 방식으로 요일×시간대 행을 요일 합계(DAYOFWEEK)로 접는다. 0건 요일은 뺀다. */
    private static List<Tuple> weekdayTotals(List<WeekdayHourParticipantCount> counts) {
        List<Tuple> totals = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            long sum = 0;
            for (WeekdayHourParticipantCount row : counts) {
                if (row.getWeekday() == day) {
                    sum += row.getParticipantCount();
                }
            }
            if (sum > 0) {
                totals.add(tuple(day, sum));
            }
        }
        return totals;
    }

    /** 서비스와 같은 방식으로 요일×시간대 행을 시간대 합계(0~23시)로 접는다. 0건 시간대는 뺀다. */
    private static List<Tuple> hourTotals(List<WeekdayHourParticipantCount> counts) {
        long[] totalsByHour = new long[24];
        for (WeekdayHourParticipantCount row : counts) {
            if (row.getHour() >= 0 && row.getHour() < totalsByHour.length) {
                totalsByHour[row.getHour()] += row.getParticipantCount();
            }
        }
        List<Tuple> totals = new ArrayList<>();
        for (int hour = 0; hour < totalsByHour.length; hour++) {
            if (totalsByHour[hour] > 0) {
                totals.add(tuple(hour, totalsByHour[hour]));
            }
        }
        return totals;
    }
}
