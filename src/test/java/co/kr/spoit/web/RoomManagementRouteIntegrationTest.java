package co.kr.spoit.web;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.support.MySqlTestDatabase;

/**
 * 시설 관리자 매칭방 조회의 소유권 경계를 실제 컨트롤러 → 서비스 → MyBatis → MySQL 경로로 검증한다.
 *
 * <p>SQL 단위 테스트와 MockMvc 단위 테스트를 잇는 계층이다. 화면에 전달되는 모델이 곧 서비스 결과이므로
 * 목록·상세의 모델 값으로 담당 범위를 확인하고, 렌더링된 신청자·참가자 이름도 함께 확인한다.</p>
 *
 * <p>명시적으로 지정한 전용 MySQL DB에서만 실행되며, 환경변수가 없으면 건너뛴다. 모든 행은
 * 테스트 트랜잭션 롤백으로 되돌린다.</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
@Sql(scripts = "/match/room-management-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class RoomManagementRouteIntegrationTest {

    /** 시설 9301·9304를 담당하며 매칭방 9401·9402·9403·9404를 볼 수 있다. */
    private static final long MANAGER_A = 9201L;
    /** 시설 9303만 담당하므로 관리자 A의 매칭방은 볼 수 없다. */
    private static final long MANAGER_B = 9202L;

    /** 관리자 A의 매칭방: 접수 대기 개인 신청과 확정 참가자가 함께 있다. */
    private static final long OWNED_MATCH_ID = 9401L;
    /** 관리자 B의 시설(9303) 매칭방: 관리자 A에게는 보이지 않아야 한다. */
    private static final long FOREIGN_MATCH_ID = 9406L;
    /** 관리자 A의 담당 시설 두 곳: 9301(방 2건)과 9304(방 1건). */
    private static final long FACILITY_A = 9301L;
    private static final long FACILITY_B = 9304L;
    /** 9304의 유일한 매칭방(종료 상태). */
    private static final long FACILITY_B_MATCH_ID = 9403L;
    /** 관리자 A가 담당하지만 논리 삭제된 시설과 그 방. */
    private static final long DELETED_FACILITY = 9302L;
    private static final long DELETED_FACILITY_MATCH_ID = 9405L;
    /** 관리자 B만 담당하는 시설. */
    private static final long FOREIGN_FACILITY = 9303L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.registerDataSource(registry);
    }

    @BeforeTransaction
    void verifyDatabaseBeforeFixture() {
        MySqlTestDatabase.assertDedicatedDatabase(jdbcTemplate);
    }

    @Test
    @DisplayName("담당 관리자는 필터된 목록에서 자기 매칭방을 찾고 상세에서 실제 신청자·참가자를 본다")
    void ownedManagerSeesFilteredListAndRealDetail() throws Exception {
        // 목록: 농구 + 모집 중 필터는 담당 시설의 9401만 남긴다. 삭제 시설·다른 관리자 시설 방은 빠진다.
        mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .param("q", "농구")
                        .param("status", "recruiting")
                        .param("sportId", "9201")
                        .with(user(facilityManager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("matches", hasItem(
                        hasProperty("matchId", is(OWNED_MATCH_ID)))))
                .andExpect(model().attribute("matches", org.hamcrest.Matchers.not(hasItem(
                        hasProperty("matchId", is(FOREIGN_MATCH_ID))))))
                .andExpect(content().string(containsString("금요 농구 모집")));

        // 상세: 신청 내역과 참가자 명단이 서비스 조회 결과 그대로 모델에 담긴다.
        mockMvc.perform(get("/admin/operations")
                        .param("matchId", String.valueOf(OWNED_MATCH_ID))
                        .with(user(facilityManager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("match", hasProperty("matchId", is(OWNED_MATCH_ID))))
                .andExpect(model().attribute("match", hasProperty("applications", hasItem(
                        org.hamcrest.Matchers.allOf(
                                hasProperty("applicantName", is("신청자하나")),
                                hasProperty("statusLabel", is("확정 대기")))))))
                .andExpect(model().attribute("match", hasProperty("participants", hasItem(
                        org.hamcrest.Matchers.allOf(
                                hasProperty("name", is("참가자둘")),
                                hasProperty("teamLabel", is("A팀")),
                                hasProperty("statusLabel", is("참가 확정")))))))
                // 렌더링까지 확인해 템플릿이 모델 계약을 실제로 소비하는지 본다.
                .andExpect(content().string(containsString("신청자하나")))
                .andExpect(content().string(containsString("참가자둘")))
                .andExpect(content().string(containsString("A팀")));
    }

    @Test
    @DisplayName("다른 관리자의 매칭방 상세는 조회할 수 없어 사용할 수 없음으로 렌더링된다")
    void foreignManagerCannotSeeOtherManagersRoom() throws Exception {
        // 관리자 B는 9406을 실제로 소유하지만, 관리자 A에게는 소유권 밖이다.
        mockMvc.perform(get("/admin/operations")
                        .param("matchId", String.valueOf(FOREIGN_MATCH_ID))
                        .with(user(facilityManager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("match", org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(containsString("매칭방을 찾을 수 없습니다.")));

        // 같은 방이 소유자(관리자 B)에게는 실제 상세로 열린다.
        mockMvc.perform(get("/admin/operations")
                        .param("matchId", String.valueOf(FOREIGN_MATCH_ID))
                        .with(user(facilityManager(MANAGER_B))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("match", hasProperty("matchId", is(FOREIGN_MATCH_ID))));
    }

    @Test
    @DisplayName("담당 시설 필터는 그 시설의 매칭방만 남기고 선택 상태를 되돌려준다")
    void facilityFilterNarrowsToOwnedFacilityOnly() throws Exception {
        // 관리자 A는 9301과 9304를 담당한다. 9304에는 종료된 9403 한 건만 있다.
        mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .param("facilityId", "9304")
                        .with(user(facilityManager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("matches", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(model().attribute("matches", hasItem(
                        hasProperty("matchId", is(FACILITY_B_MATCH_ID)))))
                .andExpect(model().attribute("bookingFilters",
                        hasProperty("facilityId", is(FACILITY_B))))
                .andExpect(content().string(containsString("일요 축구 종료")))
                // 다른 담당 시설(9301)의 방은 필터로 빠진다.
                .andExpect(model().attribute("matches", org.hamcrest.Matchers.not(hasItem(
                        hasProperty("matchId", is(OWNED_MATCH_ID))))));
    }

    @Test
    @DisplayName("없거나 삭제됐거나 남의 시설 ID는 전체로 정규화되고 다른 시설 데이터가 새지 않는다")
    void unknownOrForeignFacilityFilterFallsBackToAllOwnedRooms() throws Exception {
        // 존재하지 않는 시설: 전체로 되돌아가되 담당 범위(9301·9304)만 남는다.
        mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .param("facilityId", "999999")
                        .with(user(facilityManager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookingFilters",
                        hasProperty("facilityId", org.hamcrest.Matchers.nullValue())))
                .andExpect(model().attribute("matches", hasItem(
                        hasProperty("matchId", is(OWNED_MATCH_ID)))))
                .andExpect(model().attribute("matches", org.hamcrest.Matchers.not(hasItem(
                        hasProperty("matchId", is(FOREIGN_MATCH_ID))))));

        // 삭제된 시설(9302): 담당이지만 삭제됐으므로 전체로 정규화되고 그 방(9405)은 나오지 않는다.
        mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .param("facilityId", "9302")
                        .with(user(facilityManager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookingFilters",
                        hasProperty("facilityId", org.hamcrest.Matchers.nullValue())))
                .andExpect(model().attribute("matches", org.hamcrest.Matchers.not(hasItem(
                        hasProperty("matchId", is(DELETED_FACILITY_MATCH_ID))))));

        // 남의 시설(9303, 관리자 B 소유): 전체로 정규화되고 9406은 여전히 보이지 않는다.
        mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .param("facilityId", String.valueOf(FOREIGN_FACILITY))
                        .with(user(facilityManager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("bookingFilters",
                        hasProperty("facilityId", org.hamcrest.Matchers.nullValue())))
                .andExpect(model().attribute("matches", org.hamcrest.Matchers.not(hasItem(
                        hasProperty("matchId", is(FOREIGN_MATCH_ID))))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("다른 관리자 방"))));
    }

    @Test
    @DisplayName("시설 선택지는 담당 시설만 노출하고 남의 시설·삭제 시설은 넣지 않는다")
    void facilityOptionsExposeManagedFacilitiesOnly() throws Exception {
        mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .with(user(facilityManager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("facilities", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(model().attribute("facilities", hasItem(
                        hasProperty("facilityId", is(FACILITY_A)))))
                .andExpect(model().attribute("facilities", hasItem(
                        hasProperty("facilityId", is(FACILITY_B)))))
                // 삭제된 9302와 관리자 B 소유 9303은 선택지에 없다.
                .andExpect(model().attribute("facilities", org.hamcrest.Matchers.not(hasItem(
                        hasProperty("facilityId", is(DELETED_FACILITY))))))
                .andExpect(model().attribute("facilities", org.hamcrest.Matchers.not(hasItem(
                        hasProperty("facilityId", is(FOREIGN_FACILITY))))));
    }

    @Test
    @DisplayName("일정 화면의 facilityId·sportId는 숨은 매칭방 목록을 좁히지 않는다")
    void scheduleViewSelectionDoesNotFilterBookings() throws Exception {
        mockMvc.perform(get("/admin/facility")
                        .param("view", "schedule")
                        .param("facilityId", String.valueOf(FACILITY_B))
                        .param("sportId", "9201")
                        .with(user(facilityManager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("view", "schedule"))
                .andExpect(model().attribute("bookingFilters",
                        org.hamcrest.Matchers.allOf(
                                hasProperty("facilityId", org.hamcrest.Matchers.nullValue()),
                                hasProperty("sportId", org.hamcrest.Matchers.nullValue()))))
                // 9301의 방까지 그대로 남아 있어야 한다.
                .andExpect(model().attribute("matches", hasItem(
                        hasProperty("matchId", is(OWNED_MATCH_ID)))));
    }

}
