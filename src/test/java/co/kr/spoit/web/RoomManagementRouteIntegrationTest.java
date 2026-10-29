package co.kr.spoit.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Pattern;

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

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;

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

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

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

    @Test
    @DisplayName("담당 관리자는 필터된 목록에서 자기 매칭방을 찾고 상세에서 실제 신청자·참가자를 본다")
    void ownedManagerSeesFilteredListAndRealDetail() throws Exception {
        // 목록: 농구 + 모집 중 필터는 담당 시설의 9401만 남긴다. 삭제 시설·다른 관리자 시설 방은 빠진다.
        mockMvc.perform(get("/admin/facility")
                        .param("view", "bookings")
                        .param("q", "농구")
                        .param("status", "recruiting")
                        .param("sportId", "9201")
                        .with(user(manager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("matches", hasItem(
                        hasProperty("matchId", is(OWNED_MATCH_ID)))))
                .andExpect(model().attribute("matches", org.hamcrest.Matchers.not(hasItem(
                        hasProperty("matchId", is(FOREIGN_MATCH_ID))))))
                .andExpect(content().string(containsString("금요 농구 모집")));

        // 상세: 신청 내역과 참가자 명단이 서비스 조회 결과 그대로 모델에 담긴다.
        mockMvc.perform(get("/admin/operations")
                        .param("matchId", String.valueOf(OWNED_MATCH_ID))
                        .with(user(manager(MANAGER_A))))
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
                        .with(user(manager(MANAGER_A))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("match", org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(containsString("매칭방을 찾을 수 없습니다.")));

        // 같은 방이 소유자(관리자 B)에게는 실제 상세로 열린다.
        mockMvc.perform(get("/admin/operations")
                        .param("matchId", String.valueOf(FOREIGN_MATCH_ID))
                        .with(user(manager(MANAGER_B))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("match", hasProperty("matchId", is(FOREIGN_MATCH_ID))));
    }

    /** DB 역할 값과 같은 표기를 써서 실제 인증 주체와 동일한 권한을 갖게 한다. */
    private static MemberPrincipal manager(long userId) {
        Member member = new Member();
        member.setUserId(userId);
        member.setEmail("room-manager-" + userId + "@example.invalid");
        member.setNickname("방관리자");
        member.setPhone("010-9200-0000");
        member.setRole("시설 관리자");
        return MemberPrincipal.from(member);
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
