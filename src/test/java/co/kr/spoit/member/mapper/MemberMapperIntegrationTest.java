package co.kr.spoit.member.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

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
import co.kr.spoit.support.MySqlTestDatabase;

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

    @Autowired
    private MemberMapper memberMapper;

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

        int updated = memberMapper.recordWithdrawal(1L, first, first);
        int duplicated = memberMapper.recordWithdrawal(1L, second, second);

        assertThat(updated).isEqualTo(1);
        assertThat(duplicated).isZero();
        Member after = memberMapper.findById(1L).orElseThrow();
        assertThat(after.getDeletedAt()).isEqualTo(first);
        assertThat(after.getPenaltyTime()).isEqualTo(before.getPenaltyTime());
    }

    @Test
    @DisplayName("이용정지 기간이 남은 회원은 탈퇴 시각을 기록하지 않는다")
    void doesNotRecordWithdrawalDuringPenalty() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 12, 0);

        int updated = memberMapper.recordWithdrawal(2L, now, now);

        assertThat(updated).isZero();
        assertThat(memberMapper.findById(2L).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("이용정지 종료 시각과 같은 시각에는 탈퇴할 수 있다")
    void allowsWithdrawalAtPenaltyBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 12, 0);
        jdbcTemplate.update("UPDATE app_user SET penalty_time = ? WHERE user_id = 2", now);

        int updated = memberMapper.recordWithdrawal(2L, now, now);

        assertThat(updated).isEqualTo(1);
        assertThat(memberMapper.findById(2L).orElseThrow().getDeletedAt()).isEqualTo(now);
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

        memberMapper.insert(member);

        Member saved = memberMapper.findById(member.getUserId()).orElseThrow();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getPenaltyTime()).isNull();
    }
}
