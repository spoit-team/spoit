package co.kr.spoit.facility.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
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

import co.kr.spoit.facility.dto.ApplicationCountSummary;
import co.kr.spoit.facility.dto.HourRoomCount;
import co.kr.spoit.facility.dto.WeekdayRoomCount;

/**
 * 시설 관리자 예약 통계 SQL을 전용 MySQL DB에서만 검증한다.
 *
 * <p>{@code MAT_TEST_DB_URL} 등 환경변수가 없으면 실행되지 않는다. fixture는 테스트 트랜잭션 안에서만
 * 저장되고 종료 시 롤백되므로 개발·운영 DB에는 흔적을 남기지 않는다.</p>
 *
 * <p>검증 기간은 2026-10-05(월) 09:00 ~ 2026-10-09(금) 18:00이며, 같은 시설에 기간 경계값·취소
 * 매칭방·다른 담당자의 시설 매칭방을 함께 둬 대상 필터를 확인한다.</p>
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
     * 테스트 트랜잭션 안에서만 쓰는 fixture다. 기간 안의 매칭방 5건(월 09시, 화 10시, 수 14시,
     * 목 10시, 금 18시)과 기간 밖·취소·다른 담당자 매칭방을 함께 넣는다.
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
        jdbcTemplate.update("""
                INSERT INTO social_match (
                    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                    min_participants, max_participants, team_a_capacity, team_b_capacity,
                    fee_per_person, required_skill, status, assignment_method, rating_status
                ) VALUES
                    (9801, 9601, 9701, 9501, '통계 경계 시작 포함', '2026-10-05 09:00:00', '2026-10-05 11:00:00',
                     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
                    (9802, 9601, 9701, 9501, '통계 기간 이전', '2026-10-05 08:59:59', '2026-10-05 10:59:59',
                     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
                    (9803, 9601, 9701, 9501, '통계 화요일', '2026-10-06 10:00:00', '2026-10-06 12:00:00',
                     1, 2, 1, 1, 0.00, '무관', '매칭 완료', 'MANUAL', 'WAITING'),
                    (9804, 9601, 9701, 9501, '통계 수요일', '2026-10-07 14:00:00', '2026-10-07 16:00:00',
                     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
                    (9805, 9601, 9701, 9501, '통계 목요일', '2026-10-08 10:00:00', '2026-10-08 12:00:00',
                     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
                    (9806, 9601, 9701, 9501, '통계 경계 끝 포함', '2026-10-09 18:00:00', '2026-10-09 20:00:00',
                     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
                    (9807, 9601, 9701, 9501, '통계 기간 이후', '2026-10-09 18:00:01', '2026-10-09 20:00:01',
                     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING'),
                    (9808, 9601, 9701, 9501, '통계 취소 매칭방', '2026-10-06 10:00:00', '2026-10-06 12:00:00',
                     1, 2, 1, 1, 0.00, '무관', '취소', 'MANUAL', 'EXCLUDED'),
                    (9809, 9602, 9701, 9503, '통계 다른 담당자 시설', '2026-10-07 11:00:00', '2026-10-07 13:00:00',
                     1, 2, 1, 1, 0.00, '무관', '모집 중', 'MANUAL', 'WAITING')
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
                    (9905, 9804, 9503, NULL, '개인', '대기', 'STATS-RECEIPT-9905',
                     '2026-10-01 09:00:00', NULL, NULL, NULL, NULL),
                    (9906, 9805, 9504, NULL, '개인', '확정', 'STATS-RECEIPT-9906',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL),
                    (9907, 9806, 9505, NULL, '개인', '취소', 'STATS-RECEIPT-9907',
                     '2026-10-01 09:00:00', NULL, '2026-10-03 09:00:00', 9505, NULL),
                    (9908, 9808, 9502, NULL, '개인', '확정', 'STATS-RECEIPT-9908',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL),
                    (9909, 9807, 9502, NULL, '개인', '확정', 'STATS-RECEIPT-9909',
                     '2026-10-01 09:00:00', '2026-10-02 09:00:00', NULL, NULL, NULL)
                """);
        jdbcTemplate.update("""
                INSERT INTO match_participant (
                    participant_id, application_id, match_id, user_id, participation_status,
                    match_side, entry_mmr, joined_at, cancelled_at
                ) VALUES
                    (9911, 9901, 9801, 9502, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9912, 9901, 9801, 9503, '취소', 'B', 1000.00, '2026-10-01 09:00:00', '2026-10-03 09:00:00'),
                    (9921, 9902, 9803, 9502, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9922, 9903, 9803, 9503, '대기', NULL, 1000.00, '2026-10-01 09:00:00', NULL),
                    (9923, 9904, 9803, 9504, '취소', 'B', 1000.00, '2026-10-01 09:00:00', '2026-10-03 09:00:00'),
                    (9931, 9905, 9804, 9503, '대기', NULL, 1000.00, '2026-10-01 09:00:00', NULL),
                    (9941, 9906, 9805, 9504, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9951, 9907, 9806, 9505, '확정', 'B', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9961, 9908, 9808, 9502, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL),
                    (9971, 9909, 9807, 9502, '확정', 'A', 1000.00, '2026-10-01 09:00:00', NULL)
                """);
    }

    @Test
    @DisplayName("기간 양끝을 포함하고 취소 매칭방·기간 밖 매칭방은 제외한다")
    void countsOnlyInRangeNonCancelledMatches() {
        long count = statsMapper.countMatches(MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END);

        // 9801(시작 경계), 9803, 9804, 9805, 9806(끝 경계)만 대상이다.
        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("대기·확정 참가자만 더하고 취소 참가자와 취소 매칭방 인원은 제외한다")
    void sumsParticipantsWithActiveStatusesOnly() {
        long participants = statsMapper.sumParticipants(MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END);

        // 9911, 9921, 9922, 9931, 9941, 9951 = 6. 9912·9923(취소), 9961(취소 매칭방)은 빠진다.
        assertThat(participants).isEqualTo(6);
    }

    @Test
    @DisplayName("팀 신청은 대표 행만 세어 한 건으로 집계하고 유형·확정 수를 함께 센다")
    void countsRepresentativeApplicationRows() {
        ApplicationCountSummary summary =
                statsMapper.countApplications(MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END);

        // 9901·9905·9906·9907(개인) + 9902(동호회 대표) = 5, 팀원 9903·9904는 제외.
        assertThat(summary.getTotal()).isEqualTo(5);
        assertThat(summary.getIndividualCount()).isEqualTo(4);
        assertThat(summary.getClubCount()).isEqualTo(1);
        // 9901, 9902 대표, 9906만 확정이다.
        assertThat(summary.getApprovedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("요일별로 취소 제외 매칭방 수를 DAYOFWEEK 기준으로 묶는다")
    void groupsMatchCountByWeekday() {
        var counts = statsMapper.countByWeekday(MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END);

        assertThat(counts)
                .extracting(WeekdayRoomCount::getWeekday, WeekdayRoomCount::getRoomCount)
                .containsExactly(
                        tuple(2, 1L),  // 월
                        tuple(3, 1L),  // 화
                        tuple(4, 1L),  // 수
                        tuple(5, 1L),  // 목
                        tuple(6, 1L)); // 금
    }

    @Test
    @DisplayName("시간대별로 취소 제외 매칭방 수를 HOUR 기준으로 묶는다")
    void groupsMatchCountByHour() {
        var counts = statsMapper.countByHour(MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END);

        assertThat(counts)
                .extracting(HourRoomCount::getHour, HourRoomCount::getRoomCount)
                .containsExactly(
                        tuple(9, 1L),
                        tuple(10, 2L),
                        tuple(14, 1L),
                        tuple(18, 1L));
    }

    @Test
    @DisplayName("담당하지 않은 관리자나 다른 시설로 조회하면 모든 지표가 0이다")
    void scopesEveryQueryToManagerAndFacility() {
        assertThat(statsMapper.countMatches(OTHER_MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END))
                .isZero();
        assertThat(statsMapper.sumParticipants(OTHER_MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END))
                .isZero();
        assertThat(statsMapper.countByWeekday(OTHER_MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END))
                .isEmpty();
        assertThat(statsMapper.countByHour(OTHER_MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END))
                .isEmpty();

        ApplicationCountSummary summary =
                statsMapper.countApplications(OTHER_MANAGER_USER_ID, FACILITY_ID, RANGE_START, RANGE_END);
        assertThat(summary.getTotal()).isZero();
        assertThat(summary.getIndividualCount()).isZero();
        assertThat(summary.getClubCount()).isZero();
        assertThat(summary.getApprovedCount()).isZero();

        // 관리자 A가 담당하지 않은 시설(9602)도 같은 방식으로 0건이다.
        assertThat(statsMapper.countMatches(MANAGER_USER_ID, 9602L, RANGE_START, RANGE_END)).isZero();
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
