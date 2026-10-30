package co.kr.spoit.facility.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.service.FacilityDeleteBlockedException;
import co.kr.spoit.facility.service.FacilityServiceImpl;
import co.kr.spoit.match.dto.MatchDetailRow;
import co.kr.spoit.match.mapper.MatchMapper;
import co.kr.spoit.support.MySqlTestDatabase;

/**
 * 매칭방이 참조하는 시설·종목의 변경·삭제 규칙을 전용 MySQL DB에서만 검증한다.
 *
 * <p>{@code MAT_TEST_DB_URL} 등 환경변수가 없으면 실행되지 않는다. fixture와 테스트용
 * {@code social_match} 행은 테스트 트랜잭션 안에서만 저장되고 종료 시 롤백되므로 개발·운영 DB에는
 * 흔적을 남기지 않는다.</p>
 *
 * <p>시설 서비스는 {@code @Import}로 실제 {@link FacilityServiceImpl}과 실제 매퍼를 연결해 검증한다.
 * {@link MatchMapper}는 매칭 상세에 시설 정보가 반영되는지 확인하는 테스트 전용 조회 수단이며,
 * 운영 서비스끼리 매퍼를 공유시키기 위한 것이 아니다.</p>
 *
 * <p>서비스 메서드가 테스트 트랜잭션에 참여한 상태로 실패하면 트랜잭션이 rollback-only가 되므로,
 * 실패 케이스는 세이브포인트({@code PROPAGATION_NESTED}) 안에서 호출해 실패 지점까지만 되돌리고
 * 이후 검증은 정상 상태의 바깥 트랜잭션에서 읽는다.</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@MybatisTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FacilityServiceImpl.class)
@Sql(scripts = "/facility/mapper-fixture.sql", config = @SqlConfig(encoding = "UTF-8"))
class FacilityMatchReferenceIntegrationTest {

    /** fixture에서 농구(9001)·축구(9002)를 제공하고 A(9001)가 담당하는 시설. */
    private static final long FACILITY_ID = 9101L;
    /** fixture의 시설 관리자 A. */
    private static final long MANAGER_USER_ID = 9001L;
    private static final long BASKETBALL_SPORT_ID = 9001L;
    private static final long SOCCER_SPORT_ID = 9002L;

    @Autowired
    private FacilityServiceImpl facilityService;

    @Autowired
    private FacilityMapper facilityMapper;

    @Autowired
    private MatchMapper matchMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        MySqlTestDatabase.registerDataSource(registry);
    }

    @BeforeTransaction
    void verifyDatabaseBeforeFixture() {
        MySqlTestDatabase.assertDedicatedDatabase(jdbcTemplate);
    }

    @Test
    @DisplayName("참조된 종목을 유지한 채 시설명·주소를 수정하면 매칭 상세에 반영되고 종목 기본 설정은 남는다")
    void updatesFacilityReferencedByMatchAndKeepsSportDefaults() {
        insertMatch(1001L, BASKETBALL_SPORT_ID, "모집 중");

        facilityService.updateFacility(MANAGER_USER_ID, FACILITY_ID,
                form("참조 유지 시설명", "참조 유지 주소", 3, "운영 중",
                        "시설 테스트 농구", "시설 테스트 축구"));

        MatchDetailRow detail = matchMapper.findMatchDetail(1001L);
        assertThat(detail).isNotNull();
        assertThat(detail.getFacilityId()).isEqualTo(FACILITY_ID);
        assertThat(detail.getFacilityName()).isEqualTo("참조 유지 시설명");
        assertThat(detail.getAddress()).isEqualTo("참조 유지 주소");
        assertThat(detail.getSportId()).isEqualTo(BASKETBALL_SPORT_ID);
        assertThat(detail.getSportName()).isEqualTo("시설 테스트 농구");

        // 종목 매핑과 fixture의 기본 모집 조건이 그대로 남아 있어야 한다.
        assertThat(facilitySportDefaults()).containsExactly(
                new SportDefault(BASKETBALL_SPORT_ID, 1, 2, new BigDecimal("0.00"), "무관"),
                new SportDefault(SOCCER_SPORT_ID, 1, 2, new BigDecimal("0.00"), "무관"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"모집 중", "매칭 완료"})
    @DisplayName("모집 중·매칭 완료 매칭방이 있는 시설은 삭제할 수 없고 deleted_at은 NULL로 남는다")
    void rejectsDeletionWhenUnfinishedMatchReferencesFacility(String matchStatus) {
        insertMatch(1002L, BASKETBALL_SPORT_ID, matchStatus);

        assertDeleteBlocked(() -> facilityService.deleteFacility(MANAGER_USER_ID, FACILITY_ID), 1002L);

        assertThat(isFacilityDeleted()).isFalse();
        assertThat(facilityMapper.selectManagedFacilities(MANAGER_USER_ID))
                .extracting(summary -> summary.getFacilityId())
                .contains(FACILITY_ID);
    }

    @Test
    @DisplayName("취소·종료 매칭방만 남아 있으면 시설을 논리 삭제할 수 있다")
    void deletesFacilityWhenOnlyFinishedMatchesReferenceIt() {
        insertMatch(1003L, BASKETBALL_SPORT_ID, "취소");
        insertMatch(1004L, SOCCER_SPORT_ID, "종료");

        facilityService.deleteFacility(MANAGER_USER_ID, FACILITY_ID);

        assertThat(isFacilityDeleted()).isTrue();
        assertThat(facilityMapper.selectManagedFacilities(MANAGER_USER_ID))
                .extracting(summary -> summary.getFacilityId())
                .doesNotContain(FACILITY_ID);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"모집 중", "매칭 완료", "취소", "종료"})
    @DisplayName("어떤 상태의 매칭방이든 참조하는 종목은 선택 해제할 수 없고 종목 매핑이 남는다")
    void rejectsDeselectingSportReferencedByAnyMatch(String matchStatus) {
        insertMatch(1005L, BASKETBALL_SPORT_ID, matchStatus);

        assertRejected(() -> facilityService.updateFacility(MANAGER_USER_ID, FACILITY_ID,
                form("종목 해제 시도", "종목 해제 주소", 3, "운영 중", "시설 테스트 축구")));

        // 참조된 종목과 기본 모집 조건, 시설 정보가 모두 원래대로 남아 있어야 한다.
        assertThat(facilitySportDefaults()).containsExactly(
                new SportDefault(BASKETBALL_SPORT_ID, 1, 2, new BigDecimal("0.00"), "무관"),
                new SportDefault(SOCCER_SPORT_ID, 1, 2, new BigDecimal("0.00"), "무관"));
        assertThat(facilityMapper.selectManagedFacilities(MANAGER_USER_ID))
                .filteredOn(summary -> summary.getFacilityId() == FACILITY_ID)
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.getName()).isEqualTo("A 관리 시설 1");
                    assertThat(summary.getAddress()).isEqualTo("테스트 주소 1");
                });
    }

    @Test
    @DisplayName("참조되지 않은 종목만 제거하면 수정이 성공하고 참조 종목의 기본 설정은 유지된다")
    void removesOnlyUnreferencedSport() {
        insertMatch(1006L, BASKETBALL_SPORT_ID, "모집 중");

        facilityService.updateFacility(MANAGER_USER_ID, FACILITY_ID,
                form("종목 정리 시설", "종목 정리 주소", 3, "운영 중", "시설 테스트 농구"));

        assertThat(facilitySportDefaults()).containsExactly(
                new SportDefault(BASKETBALL_SPORT_ID, 1, 2, new BigDecimal("0.00"), "무관"));
        assertThat(matchMapper.findMatchDetail(1006L).getSportName()).isEqualTo("시설 테스트 농구");
    }

    /**
     * 실패가 예상되는 서비스 호출을 세이브포인트 안에서 실행한다.
     * 실패 지점까지만 되돌리므로 바깥 테스트 트랜잭션은 rollback-only가 되지 않는다.
     */
    private void assertRejected(Runnable action) {
        TransactionTemplate nested = new TransactionTemplate(transactionManager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);

        assertThatThrownBy(() -> nested.executeWithoutResult(status -> action.run()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 삭제 차단은 전용 예외로 알리므로 안내 문구와 차단 매칭방까지 함께 검증한다.
     * {@link #assertRejected(Runnable)}와 같은 세이브포인트 안에서 실행해 이후 검증은 바깥 트랜잭션에서 읽는다.
     */
    private void assertDeleteBlocked(Runnable action, Long... blockingMatchIds) {
        TransactionTemplate nested = new TransactionTemplate(transactionManager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);

        assertThatThrownBy(() -> nested.executeWithoutResult(status -> action.run()))
                .isInstanceOfSatisfying(FacilityDeleteBlockedException.class, exception -> {
                    assertThat(exception).hasMessageContaining("시설을 삭제할 수 없습니다");
                    assertThat(exception.getConflicts())
                            .extracting(MatchPeriod::getMatchId)
                            .containsExactly(blockingMatchIds);
                });
    }

    /** 테스트 트랜잭션 안에서만 쓰는 매칭방 행이다. fixture의 시설·종목·회원을 참조한다. */
    private void insertMatch(long matchId, long sportId, String status) {
        jdbcTemplate.update("""
                INSERT INTO social_match (
                    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                    min_participants, max_participants, team_a_capacity, team_b_capacity,
                    fee_per_person, required_skill, status, assignment_method, rating_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                matchId, FACILITY_ID, sportId, MANAGER_USER_ID,
                "시설 참조 테스트 매칭 " + matchId,
                LocalDateTime.of(2026, 10, 22, 10, 0), LocalDateTime.of(2026, 10, 22, 12, 0),
                1, 2, 1, 1, new BigDecimal("0.00"), "무관", status, "MANUAL", "WAITING");
    }

    private List<SportDefault> facilitySportDefaults() {
        return jdbcTemplate.query("""
                        SELECT sport_id, default_min, default_max, default_fee, default_skill
                          FROM facility_sport
                         WHERE facility_id = ?
                         ORDER BY sport_id
                        """,
                (rs, rowNum) -> new SportDefault(
                        rs.getLong("sport_id"),
                        rs.getInt("default_min"),
                        rs.getInt("default_max"),
                        rs.getBigDecimal("default_fee"),
                        rs.getString("default_skill")),
                FACILITY_ID);
    }

    private boolean isFacilityDeleted() {
        Integer deleted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM facility WHERE facility_id = ? AND deleted_at IS NOT NULL",
                Integer.class, FACILITY_ID);
        return deleted != null && deleted > 0;
    }

    private static FacilityForm form(String name, String address, Integer concurrentMatchLimit,
            String status, String... sports) {
        FacilityForm form = new FacilityForm();
        form.setName(name);
        form.setAddress(address);
        form.setSports(List.of(sports));
        form.setConcurrentMatchLimit(concurrentMatchLimit);
        form.setStatus(status);
        return form;
    }

    private record SportDefault(long sportId, int defaultMin, int defaultMax,
            BigDecimal defaultFee, String defaultSkill) {
    }
}
