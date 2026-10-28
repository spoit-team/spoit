package co.kr.spoit.facility.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import co.kr.spoit.facility.domain.FacilityClosure;
import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.dto.CalendarView;
import co.kr.spoit.facility.dto.ClosureForm;
import co.kr.spoit.facility.dto.DateScheduleForm;
import co.kr.spoit.facility.dto.SchedulePage;
import co.kr.spoit.facility.dto.WeeklyHourRow;
import co.kr.spoit.facility.dto.WeeklyScheduleForm;
import co.kr.spoit.facility.service.FacilityReservationService;
import co.kr.spoit.facility.service.FacilityScheduleService;
import co.kr.spoit.facility.service.FacilityScheduleUnavailableException;
import co.kr.spoit.facility.service.ScheduleConflictException;
import co.kr.spoit.match.domain.MatchStatus;

/**
 * 운영 일정 저장이 실제 MyBatis 매퍼·서비스를 거쳐 다시 조회·계산되는 왕복을 격리 MySQL에서 검증한다.
 *
 * <p>기존 {@code FacilityScheduleConcurrencyIntegrationTest}는 잠금 순서를 저수준 JDBC로 재현하고
 * {@code FacilityScheduleServiceImplTest}는 Mockito로 서비스만 검증한다. 이 테스트는 그 사이의
 * 빈틈, 즉 실제 저장 SQL이 만든 행을 실제 조회·계산이 그대로 읽어 내는지만 확인한다.</p>
 *
 * <p>다루는 것은 두 가지다. (1) 주간 7행(야간·24:00) 저장과 날짜별 0슬롯·주간 외 개방의 적용과
 * 초기화, (2) 인접 경계 휴관 허용과 두 활성 매칭방 충돌 시 복수 ID·구간 반환 및 무저장.</p>
 *
 * <p>아래는 이 파일에서 반복하지 않고 기존 테스트를 재사용한다.
 * 화면 렌더·flash 계약은 {@code AdminFacilityPageTest}·{@code FacilityScheduleRouteTest},
 * 구역 수 축소 거부는 {@code FacilityServiceImplTest}, 인접 구간 비과대계산은
 * {@code FacilityOccupancyTest}가 이미 고정하고 있다.</p>
 *
 * <p>{@code MAT_TEST_DB_URL} 등 환경변수가 없으면 실행되지 않는다. 모든 행은 기본 테스트
 * 트랜잭션 안에서만 저장되고 종료 시 롤백되므로 개발·운영 DB에 흔적을 남기지 않는다. 실패가
 * 예상되는 호출은 세이브포인트({@code PROPAGATION_NESTED}) 안에서 실행해 실패 지점까지만
 * 되돌리고, 이후 검증은 정상 상태의 바깥 트랜잭션에서 읽는다.</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(scripts = "/facility/mapper-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class FacilityScheduleRoundTripIntegrationTest {

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    /** fixture에서 농구(9001)·축구(9002)를 제공하고 관리자 A(9001)가 담당하는 시설이다. */
    private static final long FACILITY_ID = 9101L;
    private static final long MANAGER_USER_ID = 9001L;

    /**
     * 요일 계산을 고정하려고 요일이 확정된 날짜만 쓴다(2026-11-10은 화요일).
     * 현재 시각에 의존하지 않으므로 실행 시점과 무관하게 결정적이다.
     */
    private static final LocalDate TUESDAY = LocalDate.of(2026, 11, 10);
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 11, 11);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 14);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 15);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 11, 16);

    /** 다른 테스트 대역과 겹치지 않는 이 테스트 전용 매칭방 식별자다. */
    private static final long CLOSURE_MATCH_A = 2001L;
    private static final long CLOSURE_MATCH_B = 2002L;

    @Autowired
    private FacilityScheduleService facilityScheduleService;

    @Autowired
    private FacilityReservationService facilityReservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

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

    @Test
    @DisplayName("주간 7행(야간·24:00) 저장 뒤 날짜별 0슬롯·주간 외 개방이 조회에 적용되고 초기화하면 주간으로 돌아온다")
    void savesWeeklyHoursAndAppliesDateOverridesUntilReset() {
        facilityScheduleService.saveWeeklyHours(MANAGER_USER_ID, weeklyForm(overnightWeeklyHours()));

        // 저장한 7행을 서비스가 다시 읽어 화면 모델로 만든다(24:00 종료는 "24:00"으로 복원).
        SchedulePage weeklyPage = schedulePage(SATURDAY);
        assertThat(weeklyPage.getWeeklyHours())
                .extracting(WeeklyHourRow::getDay, WeeklyHourRow::isOpen,
                        WeeklyHourRow::getStart, WeeklyHourRow::getEnd)
                .containsExactly(
                        tuple(1, true, "09:00", "18:00"),
                        tuple(2, true, "09:00", "18:00"),
                        tuple(3, true, "09:00", "18:00"),
                        tuple(4, true, "09:00", "18:00"),
                        tuple(5, true, "09:00", "18:00"),
                        tuple(6, true, "22:00", "24:00"),
                        tuple(7, true, "22:00", "06:00"));
        // 토요일 22:00~24:00은 2슬롯이고, 일요일도 당일 야간 시간만 남는다.
        assertThat(weeklyPage.getCalendar().getRangesText()).isEqualTo("22:00–24:00");
        assertThat(schedulePage(SUNDAY).getCalendar().getRangesText()).isEqualTo("22:00–24:00");
        // 일요일 야간(익일 06:00 종료)이 다음 날로 이어지는지 월요일 슬롯으로 확인한다.
        assertThat(schedulePage(NEXT_MONDAY).getCalendar().getRangesText())
                .isEqualTo("00:00–06:00, 09:00–18:00");

        // 1) 선택 0건 저장은 그 날짜 전체 휴무이며 헤더 행을 남겨 주간 상속과 구분한다.
        facilityScheduleService.saveDateHours(MANAGER_USER_ID, dateForm(TUESDAY, List.of(), "임시 휴무"));
        CalendarView closed = schedulePage(TUESDAY).getCalendar();
        assertThat(closed.getSelectedCount()).isZero();
        assertThat(closed.getSourceLabel()).isEqualTo("이 날짜는 휴무로 설정되어 있습니다.");
        assertThat(closed.getAvailabilityText()).isEqualTo("이 날짜는 종일 예약이 불가능합니다.");
        assertThat(overrideCount(TUESDAY)).isEqualTo(1);
        assertThat(overrideSlots(TUESDAY)).isEmpty();

        // 2) 주간 운영(09:00~18:00) 밖의 임시 개방도 그 날짜에만 적용된다.
        facilityScheduleService.saveDateHours(MANAGER_USER_ID,
                dateForm(TUESDAY, List.of("20:00", "21:00"), "야간 개방"));
        CalendarView reopened = schedulePage(TUESDAY).getCalendar();
        assertThat(reopened.getSourceLabel()).isEqualTo("이 날짜에만 적용되는 시간입니다.");
        assertThat(reopened.getRangesText()).isEqualTo("20:00–22:00");
        assertThat(reopened.getNote()).isEqualTo("야간 개방");
        assertThat(reopened.getHourSlots())
                .filteredOn(CalendarView.HourSlot::isSelected)
                .extracting(CalendarView.HourSlot::getTime)
                .containsExactly("20:00", "21:00");
        assertThat(overrideSlots(TUESDAY)).containsExactly(20, 21);
        // 저장된 예외를 실제 예약 검사가 같은 값으로 읽는다(주간 밖 20:00~22:00 통과).
        facilityReservationService.requireAvailable(FACILITY_ID, TUESDAY.atTime(20, 0), TUESDAY.atTime(22, 0));

        // 3) 초기화하면 예외 행이 사라지고 주간 규칙이 다시 적용된다.
        facilityScheduleService.resetDateHours(MANAGER_USER_ID, FACILITY_ID, TUESDAY);
        assertThat(overrideCount(TUESDAY)).isZero();
        assertThat(overrideSlots(TUESDAY)).isEmpty();
        CalendarView restored = schedulePage(TUESDAY).getCalendar();
        assertThat(restored.getSourceLabel()).isEqualTo("주간 운영 시간을 기준으로 표시합니다.");
        assertThat(restored.getRangesText()).isEqualTo("09:00–18:00");
        assertThat(restored.getAvailabilityText()).isEqualTo("예약 가능 9시간");

        // 예외가 없는 다른 날짜의 같은 시각은 여전히 주간 규칙만 적용되어 거부된다.
        Throwable rejected = failureInsideSavepoint(() -> facilityReservationService.requireAvailable(
                FACILITY_ID, WEDNESDAY.atTime(20, 0), WEDNESDAY.atTime(22, 0)));
        assertThat(rejected).isInstanceOf(FacilityScheduleUnavailableException.class)
                .hasMessageContaining("운영 시간");
    }

    @Test
    @DisplayName("휴관은 매칭방과 경계만 맞닿으면 등록되고, 두 활성 매칭방과 겹치면 복수 ID·구간을 반환하며 아무것도 쓰지 않는다")
    void registersAdjacentClosureButRejectsOverlapWithEveryActiveMatch() {
        insertMatch(CLOSURE_MATCH_A, "휴관 충돌 매칭 A",
                TUESDAY.atTime(10, 0), TUESDAY.atTime(12, 0), MatchStatus.RECRUITING.dbValue());
        insertMatch(CLOSURE_MATCH_B, "휴관 충돌 매칭 B",
                TUESDAY.atTime(13, 0), TUESDAY.atTime(15, 0), MatchStatus.RECRUITING.dbValue());

        // 경계만 맞닿은 휴관(12:00~13:00)은 두 방 어느 쪽과도 겹치지 않아 등록된다.
        facilityScheduleService.registerClosure(MANAGER_USER_ID,
                closureForm(TUESDAY, "12:00", "13:00", "인접 경계 휴관"));

        assertThat(closureRows())
                .extracting(ClosureRow::startAt, ClosureRow::endAt, ClosureRow::status)
                .containsExactly(tuple(TUESDAY.atTime(12, 0), TUESDAY.atTime(13, 0),
                        FacilityClosure.STATUS_ACTIVE));

        // 두 방을 모두 덮는 휴관은 거부되고, 충돌한 방 전체와 입력 구간을 함께 돌려준다.
        Throwable failure = failureInsideSavepoint(() -> facilityScheduleService.registerClosure(
                MANAGER_USER_ID, closureForm(TUESDAY, "10:00", "14:00", "겹침 휴관")));

        assertThat(failure).isInstanceOf(ScheduleConflictException.class);
        ScheduleConflictException conflict = (ScheduleConflictException) failure;
        assertThat(conflict.getConflicts())
                .extracting(MatchPeriod::getMatchId, MatchPeriod::getTitle)
                .containsExactly(
                        tuple(CLOSURE_MATCH_A, "휴관 충돌 매칭 A"),
                        tuple(CLOSURE_MATCH_B, "휴관 충돌 매칭 B"));
        assertThat(conflict.getRangeText())
                .startsWith("휴관 ")
                .contains("2026년 11월 10일")
                .contains("10:00 ~ 14:00");

        // 휴관 insert는 없고, 앞서 등록한 인접 휴관과 두 매칭방은 그대로 남는다.
        assertThat(closureRows())
                .extracting(ClosureRow::startAt, ClosureRow::endAt)
                .containsExactly(tuple(TUESDAY.atTime(12, 0), TUESDAY.atTime(13, 0)));
        assertThat(activeMatchIds()).containsExactlyInAnyOrder(CLOSURE_MATCH_A, CLOSURE_MATCH_B);
    }

    // ------------------------------------------------------------------
    // 폼·fixture 헬퍼
    // ------------------------------------------------------------------

    /** 평일 09:00~18:00, 토요일 22:00~24:00, 일요일 22:00~익일 06:00인 야간 운영이다. */
    private static Hours overnightWeeklyHours() {
        Map<Integer, String> starts = new LinkedHashMap<>();
        Map<Integer, String> ends = new LinkedHashMap<>();
        for (int day = 1; day <= 5; day++) {
            starts.put(day, "09:00");
            ends.put(day, "18:00");
        }
        starts.put(6, "22:00");
        ends.put(6, "24:00");
        starts.put(7, "22:00");
        ends.put(7, "06:00");
        return new Hours(starts, ends);
    }

    private static WeeklyScheduleForm weeklyForm(Hours hours) {
        WeeklyScheduleForm form = new WeeklyScheduleForm();
        form.setFacilityId(FACILITY_ID);
        form.setOpenDays(List.of(1, 2, 3, 4, 5, 6, 7));
        form.setStartTimes(hours.starts());
        form.setEndTimes(hours.ends());
        return form;
    }

    private static DateScheduleForm dateForm(LocalDate date, List<String> hours, String note) {
        DateScheduleForm form = new DateScheduleForm();
        form.setFacilityId(FACILITY_ID);
        form.setDate(date.toString());
        form.setHours(hours);
        form.setNote(note);
        return form;
    }

    private static ClosureForm closureForm(LocalDate date, String start, String end, String reason) {
        ClosureForm form = new ClosureForm();
        form.setFacilityId(FACILITY_ID);
        form.setDate(date.toString());
        form.setStart(start);
        form.setEnd(end);
        form.setReason(reason);
        return form;
    }

    // ------------------------------------------------------------------
    // 조회 헬퍼
    // ------------------------------------------------------------------

    private SchedulePage schedulePage(LocalDate date) {
        return facilityScheduleService.findSchedulePage(MANAGER_USER_ID, FACILITY_ID, date,
                null, null, YearMonth.from(date));
    }

    private int overrideCount(LocalDate date) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM facility_date_override
                 WHERE facility_id = ? AND override_date = ?
                """, Integer.class, FACILITY_ID, date);
        return count == null ? 0 : count;
    }

    private List<Integer> overrideSlots(LocalDate date) {
        return jdbcTemplate.queryForList("""
                SELECT s.slot_hour
                  FROM facility_date_override_slot s
                  JOIN facility_date_override o
                    ON o.facility_date_override_id = s.facility_date_override_id
                 WHERE o.facility_id = ? AND o.override_date = ?
                 ORDER BY s.slot_hour
                """, Integer.class, FACILITY_ID, date);
    }

    private List<ClosureRow> closureRows() {
        return jdbcTemplate.query("""
                SELECT start_at, end_at, status
                  FROM schedule_change
                 WHERE facility_id = ?
                 ORDER BY start_at
                """, (rs, rowNum) -> new ClosureRow(
                rs.getTimestamp("start_at").toLocalDateTime(),
                rs.getTimestamp("end_at").toLocalDateTime(),
                rs.getString("status")), FACILITY_ID);
    }

    private List<Long> activeMatchIds() {
        return jdbcTemplate.queryForList("""
                SELECT match_id FROM social_match
                 WHERE facility_id = ?
                   AND status IN ('모집 중', '매칭 완료')
                 ORDER BY match_id
                """, Long.class, FACILITY_ID);
    }

    /** fixture의 시설·종목·회원을 참조하는 테스트 전용 매칭방이다. */
    private void insertMatch(long matchId, String title, LocalDateTime startAt, LocalDateTime endAt,
            String status) {
        jdbcTemplate.update("""
                INSERT INTO social_match (
                    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                    min_participants, max_participants, team_a_capacity, team_b_capacity,
                    fee_per_person, required_skill, status, assignment_method, rating_status
                ) VALUES (?, ?, 9001, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, matchId, FACILITY_ID, MANAGER_USER_ID, title, startAt, endAt,
                1, 2, 1, 1, new BigDecimal("0.00"), "무관", status, "MANUAL", "WAITING");
    }

    /**
     * 실패가 예상되는 호출을 세이브포인트 안에서 실행하고 그 예외를 돌려준다.
     * 실패 지점까지만 되돌리므로 바깥 테스트 트랜잭션은 rollback-only가 되지 않는다.
     */
    private Throwable failureInsideSavepoint(Runnable action) {
        TransactionTemplate nested = new TransactionTemplate(transactionManager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        return catchThrowable(() -> nested.executeWithoutResult(status -> action.run()));
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

    private record Hours(Map<Integer, String> starts, Map<Integer, String> ends) {
    }

    private record ClosureRow(LocalDateTime startAt, LocalDateTime endAt, String status) {
    }
}
