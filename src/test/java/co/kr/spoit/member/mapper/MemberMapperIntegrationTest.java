package co.kr.spoit.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

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

import co.kr.spoit.member.domain.Member;

/**
 * 명시적으로 지정한 전용 MySQL DB에서만 회원 탈퇴 SQL과 deleted_at 매핑을 검증한다.
 * 환경변수가 없으면 실행되지 않으며 모든 변경은 테스트 트랜잭션 롤백으로 되돌린다.
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql(scripts = "/member/mapper-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class MemberMapperIntegrationTest {

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    @Autowired
    private MemberMapper memberMapper;

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
    @DisplayName("회원 조회는 findByEmail과 findById 모두 deleted_at과 penalty_time을 매핑한다")
    void mapsDeletedAtAndPenaltyTime() {
        Member byEmail = memberMapper.findByEmail("member-suspended@example.invalid").orElseThrow();
        Member byId = memberMapper.findById(2L).orElseThrow();
        Member withdrawn = memberMapper.findById(3L).orElseThrow();

        assertThat(byEmail.getDeletedAt()).isNull();
        assertThat(byEmail.getPenaltyTime()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(byId.getDeletedAt()).isNull();
        assertThat(byId.getPenaltyTime()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(withdrawn.getDeletedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 10, 0));
        assertThat(withdrawn.getPenaltyTime()).isNull();
    }

    @Test
    @DisplayName("탈퇴 시각 기록은 허용 상태의 미탈퇴 회원 1건에만 적용되고 중복 탈퇴는 0건이다")
    void recordsDeletedAtOnlyOnce() {
        LocalDateTime first = LocalDateTime.of(2026, 10, 26, 12, 0);
        LocalDateTime second = first.plusMinutes(1);
        Member before = memberMapper.findById(1L).orElseThrow();

        int updated = memberMapper.recordWithdrawal(1L, first, List.of("활성", "ACTIVE", "NORMAL"));
        int duplicated = memberMapper.recordWithdrawal(1L, second, List.of("활성", "ACTIVE", "NORMAL"));

        assertThat(updated).isEqualTo(1);
        assertThat(duplicated).isZero();
        Member after = memberMapper.findById(1L).orElseThrow();
        assertThat(after.getDeletedAt()).isEqualTo(first);
        assertThat(after.getStatus()).isEqualTo(before.getStatus());
        assertThat(after.getPenaltyTime()).isEqualTo(before.getPenaltyTime());
    }

    @Test
    @DisplayName("정지 상태 등 허용되지 않은 상태는 탈퇴 시각을 기록하지 않는다")
    void doesNotRecordWithdrawalForDisallowedStatus() {
        int updated = memberMapper.recordWithdrawal(2L, LocalDateTime.of(2026, 10, 26, 12, 0),
                List.of("활성", "ACTIVE", "NORMAL"));

        assertThat(updated).isZero();
        assertThat(memberMapper.findById(2L).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("새로 저장한 회원의 deleted_at과 penalty_time은 null이다")
    void newMemberHasNullDeletedAtAndPenaltyTime() {
        Member member = new Member();
        member.setEmail("member-new@example.invalid");
        member.setPasswordHash("TEST_ONLY_NOT_A_PASSWORD_HASH");
        member.setNickname("신규 회원");
        member.setPhone("010-0000-0004");
        member.setRole("일반 회원");
        member.setStatus("활성");

        memberMapper.insert(member);

        Member saved = memberMapper.findById(member.getUserId()).orElseThrow();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getPenaltyTime()).isNull();
    }

    private static String validateUrl(String url) {
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
