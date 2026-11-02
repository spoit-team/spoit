package co.kr.spoit.match.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.transaction.BeforeTransaction;

import co.kr.spoit.match.dto.RoomCommand;
import co.kr.spoit.match.dto.RoomEditTarget;
import co.kr.spoit.match.dto.RoomManagementFacilitySport;
import co.kr.spoit.match.dto.RoomManagementQuery;
import co.kr.spoit.match.dto.RoomManagementRow;

/**
 * 명시적으로 지정한 전용 MySQL DB에서만 매칭방 생성·수정·취소 SQL과 목록의 수정용 값을 검증한다.
 * 환경변수가 없으면 실행되지 않으며 모든 변경은 테스트 트랜잭션 롤백으로 되돌린다.
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/match/room-management-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class RoomCommandMapperIntegrationTest {

    /** 시설 9301·9302(삭제)·9304를 담당한다. */
    private static final long MANAGER_A = 9201L;
    /** 확정 참가자 1 + 대기 개인 신청 1 + 대기 동호회 신청 1, 취소된 신청·참가자 1씩 */
    private static final long RECRUITING_MATCH = 9401L;

    private static final List<String> OCCUPYING = List.of("대기", "확정");
    /** 취소할 수 있는 방 상태 */
    private static final List<String> OCCUPYING_ROOM = List.of("모집 중", "매칭 완료");

    @Autowired
    private RoomCommandMapper roomCommandMapper;

    @Autowired
    private RoomManagementMapper roomManagementMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        String url = RoomManagementMapperIntegrationTest.validateUrl(requiredEnvironmentVariable("MAT_TEST_DB_URL"));
        String username = requiredEnvironmentVariable("MAT_TEST_DB_USERNAME");
        String password = requiredEnvironmentVariable("MAT_TEST_DB_PASSWORD");

        registry.add("spring.datasource.url", () -> url
                + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false");
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
    @DisplayName("수정 대상은 담당 활성 시설의 방만 조회하고 잠금 조회도 같은 범위다")
    void findsOnlyManagedActiveRoom() {
        RoomEditTarget target = roomCommandMapper.findRoomForUpdate(MANAGER_A, RECRUITING_MATCH);
        assertThat(target).isNotNull();
        assertThat(target.getFacilityId()).isEqualTo(9301L);
        assertThat(target.getStatus()).isEqualTo("모집 중");
        assertThat(target.getStartAt()).isEqualTo(LocalDateTime.of(2026, 11, 6, 18, 0));

        // 다른 관리자 시설, 삭제된 시설의 방
        assertThat(roomCommandMapper.findRoom(MANAGER_A, 9406L)).isNull();
        assertThat(roomCommandMapper.findRoom(MANAGER_A, 9405L)).isNull();
        assertThat(roomCommandMapper.findRoomForUpdate(MANAGER_A, 9406L)).isNull();
    }

    @Test
    @DisplayName("시설·종목 검증은 담당 활성 시설의 제공 종목만 인정한다")
    void countsManagedFacilitySport() {
        assertThat(roomCommandMapper.countManagedFacilitySport(MANAGER_A, 9301L, 9203L)).isEqualTo(1);
        // 제공하지 않는 종목, 삭제된 시설, 다른 관리자 시설
        assertThat(roomCommandMapper.countManagedFacilitySport(MANAGER_A, 9304L, 9201L)).isZero();
        assertThat(roomCommandMapper.countManagedFacilitySport(MANAGER_A, 9302L, 9201L)).isZero();
        assertThat(roomCommandMapper.countManagedFacilitySport(MANAGER_A, 9303L, 9201L)).isZero();
    }

    @Test
    @DisplayName("예약 인원은 참가자(대기·확정)와 대기 신청(개인·동호회)의 합이고 취소 행은 제외한다")
    void countsReservedPeople() {
        assertThat(roomCommandMapper.countReserved(RECRUITING_MATCH, OCCUPYING, "대기")).isEqualTo(3);
        // 확정 신청 2건은 참가자 행으로만 센다.
        assertThat(roomCommandMapper.countReserved(9402L, OCCUPYING, "대기")).isEqualTo(2);
    }

    @Test
    @DisplayName("생성은 ID를 돌려주고 수정은 일정 포함 여부에 따라 바뀌는 컬럼이 다르다")
    void insertsAndUpdatesRoom() {
        RoomCommand create = RoomCommand.builder()
                .facilityId(9301L).sportId(9203L).createdBy(MANAGER_A)
                .title("새 배드민턴 방")
                .startAt(LocalDateTime.of(2026, 12, 1, 10, 0))
                .endAt(LocalDateTime.of(2026, 12, 1, 12, 0))
                .minParticipants(2).maxParticipants(4).teamACapacity(2).teamBCapacity(2)
                .feePerPerson(5000).requiredSkill("초급").status("모집 중")
                .build();
        roomCommandMapper.insertRoom(create);
        assertThat(create.getMatchId()).isNotNull();

        Map<String, Object> created = row(create.getMatchId());
        assertThat(created.get("title")).isEqualTo("새 배드민턴 방");
        assertThat(created.get("status")).isEqualTo("모집 중");
        assertThat(created.get("assignment_method")).isEqualTo("MANUAL");
        assertThat(((Number) created.get("max_participants")).intValue()).isEqualTo(4);

        RoomCommand details = RoomCommand.builder()
                .matchId(create.getMatchId())
                .title("이름 변경").minParticipants(3).maxParticipants(6).teamACapacity(3).teamBCapacity(3)
                .feePerPerson(7000).requiredSkill("고급")
                .build();
        assertThat(roomCommandMapper.updateRoomDetails(details)).isEqualTo(1);
        Map<String, Object> detailed = row(create.getMatchId());
        assertThat(detailed.get("title")).isEqualTo("이름 변경");
        assertThat(detailed.get("start_at")).isEqualTo(LocalDateTime.of(2026, 12, 1, 10, 0));

        RoomCommand full = RoomCommand.builder()
                .matchId(create.getMatchId())
                .facilityId(9301L).sportId(9201L)
                .title("농구로 변경")
                .startAt(LocalDateTime.of(2026, 12, 2, 20, 0))
                .endAt(LocalDateTime.of(2026, 12, 2, 22, 0))
                .minParticipants(2).maxParticipants(8).teamACapacity(4).teamBCapacity(4)
                .feePerPerson(0).requiredSkill("중급")
                .build();
        assertThat(roomCommandMapper.updateRoom(full)).isEqualTo(1);
        Map<String, Object> moved = row(create.getMatchId());
        assertThat(((Number) moved.get("sport_id")).longValue()).isEqualTo(9201L);
        assertThat(moved.get("start_at")).isEqualTo(LocalDateTime.of(2026, 12, 2, 20, 0));
    }

    @Test
    @DisplayName("취소는 방·활성 참가자·활성 신청만 바꾸고 이미 취소된 행은 그대로 둔다")
    void cancelsRoomWithActiveRows() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 2, 9, 30);

        assertThat(roomCommandMapper.cancelRoom(RECRUITING_MATCH, "취소", OCCUPYING_ROOM, now)).isEqualTo(1);
        assertThat(roomCommandMapper.cancelParticipants(RECRUITING_MATCH, "취소", OCCUPYING, now)).isEqualTo(1);
        assertThat(roomCommandMapper.cancelApplications(RECRUITING_MATCH, "취소", OCCUPYING, MANAGER_A, now))
                .isEqualTo(3);

        Map<String, Object> room = row(RECRUITING_MATCH);
        assertThat(room.get("status")).isEqualTo("취소");
        assertThat(room.get("cancelled_at")).isEqualTo(now);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_application WHERE match_id = ? AND status <> '취소'",
                Integer.class, RECRUITING_MATCH)).isZero();
        // 회원이 먼저 취소한 신청은 처리자가 바뀌지 않는다.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cancelled_by FROM match_application WHERE application_id = 9503", Long.class))
                .isEqualTo(9206L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cancelled_by FROM match_application WHERE application_id = 9501", Long.class))
                .isEqualTo(MANAGER_A);

        // 이미 취소된 방은 다시 바뀌지 않는다.
        assertThat(roomCommandMapper.cancelRoom(RECRUITING_MATCH, "취소", OCCUPYING_ROOM, now)).isZero();
    }

    @Test
    @DisplayName("목록은 수정 모달용 현재 값과 예약 인원을, 종목 선택지는 시설별 제공 종목을 준다")
    void listsEditValuesAndFacilitySports() {
        RoomManagementRow row = roomManagementMapper.findRooms(RoomManagementQuery.builder()
                        .managerUserId(MANAGER_A)
                        .waitingApplicationStatus("대기")
                        .individualType("개인")
                        .participationStatuses(OCCUPYING)
                        .build())
                .stream().filter(r -> r.getMatchId() == RECRUITING_MATCH).findFirst().orElseThrow();
        assertThat(row.getTeamACapacity()).isEqualTo(4);
        assertThat(row.getTeamBCapacity()).isEqualTo(4);
        assertThat(row.getRequiredSkill()).isEqualTo("무관");
        assertThat(row.getReservedCount()).isEqualTo(3);

        assertThat(roomManagementMapper.findFacilitySports(MANAGER_A))
                .extracting(RoomManagementFacilitySport::getFacilityId, RoomManagementFacilitySport::getSportId)
                .containsExactly(
                        tuple(9301L, 9201L),
                        tuple(9301L, 9202L),
                        tuple(9301L, 9203L),
                        tuple(9304L, 9202L));
    }

    private Map<String, Object> row(long matchId) {
        return jdbcTemplate.queryForMap("SELECT * FROM social_match WHERE match_id = ?", matchId);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다. 개발 DB 설정으로 대체하지 않습니다.");
        }
        return value;
    }
}
