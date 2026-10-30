package co.kr.spoit.match.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.transaction.BeforeTransaction;

import co.kr.spoit.match.dto.RoomManagementApplicationRow;
import co.kr.spoit.match.dto.RoomManagementPage;
import co.kr.spoit.match.dto.RoomManagementParticipantRow;
import co.kr.spoit.match.dto.RoomManagementQuery;
import co.kr.spoit.match.dto.RoomManagementRow;
import co.kr.spoit.match.dto.RoomManagementSport;
import co.kr.spoit.match.service.RoomManagementService;

/**
 * 명시적으로 지정한 전용 MySQL DB에서만 담당 시설 매칭방 조회 SQL을 검증한다.
 * 환경변수가 없으면 실행되지 않으며 모든 변경은 테스트 트랜잭션 롤백으로 되돌린다.
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RoomManagementService.class)
@Sql(scripts = "/match/room-management-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class RoomManagementMapperIntegrationTest {

    private static final long MANAGER_A = 9201L;
    /** 다른 관리자 시설(9303)만 담당한다. */
    private static final long MANAGER_B = 9202L;
    /** 매칭방이 하나도 없는 시설(9305)만 담당한다. */
    private static final long MANAGER_EMPTY = 9207L;

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    @Autowired
    private RoomManagementMapper roomManagementMapper;

    @Autowired
    private RoomManagementService roomManagementService;

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

    @Test
    @DisplayName("담당 시설의 모든 상태·기간 방만 조회하고 삭제·타 관리자 시설은 제외한다")
    void listsOnlyManagedActiveRooms() {
        var rows = roomManagementMapper.findRooms(query(MANAGER_A, null, null, null, null));

        assertThat(rows).extracting(RoomManagementRow::getMatchId)
                .containsExactly(9403L, 9401L, 9402L, 9404L);
        assertThat(rows).extracting(RoomManagementRow::getFacilityName)
                .containsOnly("관리 시설 알파", "관리 시설 베타");

        assertThat(roomManagementMapper.findRooms(query(MANAGER_EMPTY, null, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("점유 인원은 참가자(대기·확정) + 확정 대기 개인 신청이며 동호회 대기는 제외한다")
    void countsOccupancyWithoutInflatingJoinRows() {
        var rows = roomManagementMapper.findRooms(query(MANAGER_A, null, null, null, null));

        RoomManagementRow recruiting = rows.stream()
                .filter(row -> row.getMatchId() == 9401L).findFirst().orElseThrow();
        // 확정 참가자 1명 + 확정 대기 개인 신청 1명 (동호회 대기 신청·취소 참가자는 제외)
        assertThat(recruiting.getOccupiedCount()).isEqualTo(2);

        RoomManagementRow matched = rows.stream()
                .filter(row -> row.getMatchId() == 9402L).findFirst().orElseThrow();
        assertThat(matched.getOccupiedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("검색어는 방 번호·제목·시설명·주최자 닉네임으로 찾고 상태·종목 필터가 함께 적용된다")
    void filtersByQueryStatusAndSport() {
        assertThat(ids(query(MANAGER_A, null, "9402", null, null))).containsExactly(9402L);
        assertThat(ids(query(MANAGER_A, null, "농구 모집", null, null))).containsExactly(9401L);
        assertThat(ids(query(MANAGER_A, null, "관리 시설 베타", null, null))).containsExactly(9403L);
        assertThat(ids(query(MANAGER_A, null, "호스트비", null, null)))
                .containsExactly(9401L, 9402L, 9404L);

        assertThat(ids(query(MANAGER_A, null, null, "모집 중", null))).containsExactly(9401L);
        assertThat(ids(query(MANAGER_A, null, null, null, 9202L))).containsExactly(9403L, 9402L);
    }

    @Test
    @DisplayName("종목 선택지는 담당 시설이 제공하는 종목에서 나오므로 방이 없는 종목도 포함한다")
    void listsSportsFromManagedFacilitySports() {
        assertThat(roomManagementMapper.findSports(MANAGER_A))
                .extracting(RoomManagementSport::getSportId, RoomManagementSport::getName)
                .containsExactly(
                        tuple(9201L, "Match 테스트 농구"),
                        tuple(9202L, "Match 테스트 축구"),
                        tuple(9203L, "Match 테스트 배드민턴"));

        assertThat(roomManagementMapper.findSports(MANAGER_B))
                .extracting(RoomManagementSport::getSportId)
                .containsExactly(9201L);
        // 매칭방이 없어도 담당 시설의 종목 선택지는 나온다.
        assertThat(roomManagementMapper.findSports(MANAGER_EMPTY))
                .extracting(RoomManagementSport::getSportId)
                .containsExactly(9201L);
        assertThat(roomManagementMapper.findSports(9999L)).isEmpty();
    }

    @Test
    @DisplayName("상세는 담당 방만 반환하고 타 관리자·삭제 시설·없는 방은 null이다")
    void isolatesDetailByTenant() {
        assertThat(roomManagementMapper.findRoom(query(MANAGER_A, 9401L, null, null, null))).isNotNull();
        assertThat(roomManagementMapper.findRoom(query(MANAGER_A, 9405L, null, null, null)))
                .as("삭제된 시설").isNull();
        assertThat(roomManagementMapper.findRoom(query(MANAGER_A, 9406L, null, null, null)))
                .as("다른 관리자 시설").isNull();
        assertThat(roomManagementMapper.findRoom(query(MANAGER_A, 9999L, null, null, null))).isNull();

        assertThat(roomManagementService.findDetail(MANAGER_A, 9406L)).isNull();
        assertThat(roomManagementService.findDetail(MANAGER_B, 9401L)).isNull();
        assertThat(roomManagementService.findDetail(MANAGER_A, 9401L)).isNotNull();
    }

    @Test
    @DisplayName("신청 내역과 참가자 명단은 담당 방 기준으로 결정적 순서와 상태로 조회된다")
    void readsApplicationsAndParticipantsInOrder() {
        var applications = roomManagementMapper.findApplications(query(MANAGER_A, 9401L, null, null, null));
        assertThat(applications)
                .extracting(RoomManagementApplicationRow::getReceiptNo,
                        RoomManagementApplicationRow::getApplicationType,
                        RoomManagementApplicationRow::getStatus)
                .containsExactly(
                        tuple("ROOM-TEST-9501", "개인", "대기"),
                        tuple("ROOM-TEST-9502", "개인", "확정"),
                        tuple("ROOM-TEST-9503", "개인", "취소"),
                        tuple("ROOM-TEST-9504", "동호회", "대기"));
        assertThat(applications.get(3).getClubName()).isEqualTo("Match 테스트 동호회");

        var participants = roomManagementMapper.findParticipants(query(MANAGER_A, 9401L, null, null, null));
        assertThat(participants)
                .extracting(RoomManagementParticipantRow::getParticipantId,
                        RoomManagementParticipantRow::getParticipationStatus,
                        RoomManagementParticipantRow::getMatchSide)
                .containsExactly(tuple(9701L, "확정", "A"));

        // 다른 관리자는 같은 방의 신청·참가자를 볼 수 없다.
        assertThat(roomManagementMapper.findApplications(query(MANAGER_B, 9401L, null, null, null))).isEmpty();
        assertThat(roomManagementMapper.findParticipants(query(MANAGER_B, 9401L, null, null, null))).isEmpty();
    }

    @Test
    @DisplayName("서비스 목록은 정규화된 필터와 연도·자정 넘김 일시·점유 문구를 담당 범위로 만든다")
    void serviceBuildsPageFromManagedRows() {
        // 9204는 삭제된 시설에만 매핑돼 있어 담당 종목이 아니므로 '전체'로 정규화된다.
        var page = roomManagementService.findRooms(MANAGER_A, "축구", "all", 9204L, null);

        assertThat(page.getFilters().getQ()).isEqualTo("축구");
        assertThat(page.getFilters().getStatus()).isEqualTo("all");
        assertThat(page.getFilters().getSportId())
                .as("담당 시설 종목이 아니면 전체로 정규화").isNull();
        assertThat(page.getSports()).extracting(RoomManagementPage.Sport::getName)
                .containsExactly("Match 테스트 농구", "Match 테스트 축구", "Match 테스트 배드민턴");
        // 9403(일요 축구 종료)과 9402(토요 축구 매칭)가 담당 시설의 '축구' 방이다.
        assertThat(page.getMatches()).extracting(RoomManagementPage.Match::getMatchId)
                .containsExactly(9403L, 9402L);
        RoomManagementPage.Match target = page.getMatches().stream()
                .filter(match -> match.getMatchId() == 9402L).findFirst().orElseThrow();
        assertThat(target.getStatusLabel()).isEqualTo("매칭 완료");
        assertThat(target.getDateLabel())
                .isEqualTo("2026-11-07(토) 22:00~2026-11-08(일) 00:30");
        assertThat(target.getParticipation()).isEqualTo("2 / 10명");

        assertThat(roomManagementService.findRooms(null, null, null, null, null).getMatches()).isEmpty();
        assertThat(roomManagementService.findRooms(MANAGER_B, null, "cancelled", null, null).getMatches())
                .isEmpty();
        // 매칭방이 없는 담당자도 종목 선택지는 받는다.
        assertThat(roomManagementService.findRooms(MANAGER_EMPTY, null, null, null, null).getMatches()).isEmpty();
        assertThat(roomManagementService.findRooms(MANAGER_EMPTY, null, null, null, null).getSports()).hasSize(1);
    }

    @Test
    @DisplayName("서비스 상세는 신청·참가자 컬렉션을 항상 채우고 방 상태를 정확히 표기한다")
    void serviceBuildsDetailCollections() {
        var detail = roomManagementService.findDetail(MANAGER_A, 9401L);

        assertThat(detail).isNotNull();
        assertThat(detail.getStatusLabel()).isEqualTo("모집 중");
        assertThat(detail.getApplications()).hasSize(4);
        assertThat(detail.getParticipants()).hasSize(1);
        assertThat(detail.getApplications().get(0).getCreatedAtLabel()).isEqualTo("2026-11-01 10:00");
        assertThat(detail.getApplications().get(3).getApplicantName()).isEqualTo("Match 테스트 동호회");
        assertThat(detail.getApplications().get(3).getKindLabel()).isEqualTo("동호회 신청");
    }

    private List<Long> ids(RoomManagementQuery query) {
        return roomManagementMapper.findRooms(query).stream()
                .map(RoomManagementRow::getMatchId)
                .toList();
    }

    private static RoomManagementQuery query(long managerUserId, Long matchId, String q,
            String statusValue, Long sportId) {
        return RoomManagementQuery.builder()
                .managerUserId(managerUserId)
                .matchId(matchId)
                .q(q == null ? null : q.replace("!", "!!").replace("%", "!%").replace("_", "!_"))
                .statusValue(statusValue)
                .sportId(sportId)
                .waitingApplicationStatus("대기")
                .individualType("개인")
                .participationStatuses(List.of("대기", "확정"))
                .build();
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
