package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.match.domain.ApplicationStatus;
import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.member.service.MemberAccountLockService;
import co.kr.spoit.member.service.MemberService;

/**
 * 매치 신청 잠금 순서(P1)를 격리 MySQL에서 두 트랜잭션의 진행 순서를 고정해 검증한다.
 *
 * <ul>
 *   <li>팀 신청(시설 → 매칭방 → 동호회 → 계정)과 회장 위임(동호회 → 계정 → 동호회원)은 어느 쪽이 먼저
 *       시작해도 교착 없이 둘 다 끝난다.</li>
 *   <li>같은 회원이 서로 다른 시설의 겹치는 매치에 개인·팀으로 동시에 신청하면 계정 잠금에서 줄을 서서
 *       하나만 저장된다(READ_COMMITTED로 뒤 요청이 앞 요청의 커밋을 본다).</li>
 *   <li>회원 탈퇴가 계정을 먼저 잠그고 커밋하면 개인 신청은 최신 탈퇴 상태를 보고 신청 행을 만들지 않는다.
 *       동호회원은 회원 탈퇴 자체가 막히므로, 팀 쪽은 동호회 탈퇴가 팀 신청이 잡은 동호회 행에서 기다리는지로 확인한다.</li>
 * </ul>
 *
 * <p>진행 순서는 실제 빈을 감싼 spy({@link MemberAccountLockService}, {@link ClubService})에서
 * {@link CountDownLatch}로 맞춘다. sleep은 쓰지 않고, 상대가 DB 잠금에서 막혀 있는지는 짧은 제한 시간 안에
 * 끝나지 않는지로만 확인한다.</p>
 *
 * <p>{@code MAT_TEST_DB_URL} 등 환경변수가 없으면 실행되지 않는다. 여러 연결의 커밋을 관찰해야 하므로
 * 테스트 트랜잭션으로 감싸지 않고, 이 테스트 전용 식별자 대역(981001~)만 만들고 지운다.</p>
 */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Timeout(120)
class ApplyLockOrderConcurrencyIntegrationTest {

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS =
            "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
                    + "&allowPublicKeyRetrieval=true&useSSL=false";

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalDate TARGET_DATE = LocalDate.now(ZONE).plusDays(7);
    private static final LocalDateTime MATCH_START = TARGET_DATE.atTime(10, 0);
    private static final LocalDateTime MATCH_END = TARGET_DATE.atTime(12, 0);

    /** 다른 통합 테스트(9800xx)와 겹치지 않는 이 테스트 전용 식별자 대역이다. */
    private static final long LEADER_USER_ID = 981001L;
    private static final long MEMBER_A_USER_ID = 981002L;
    private static final long MEMBER_B_USER_ID = 981003L;
    private static final long SOLO_USER_ID = 981004L;
    private static final long MANAGER_USER_ID = 981005L;
    private static final long USER_ID_FIRST = 981001L;
    private static final long USER_ID_LAST = 981005L;

    private static final long REGION_ID = 981001L;
    private static final long SPORT_ID = 981001L;
    private static final long FACILITY_1_ID = 981001L;
    private static final long FACILITY_2_ID = 981002L;
    /** 시설 1의 매칭방. 개인 신청용 */
    private static final long MATCH_1_ID = 981001L;
    /** 시설 2의 매칭방. 시간은 MATCH_1과 같다. 팀 신청용 */
    private static final long MATCH_2_ID = 981002L;

    private static final long CLUB_ID = 981001L;
    private static final long LEADER_CLUB_MEMBER_ID = 981001L;
    private static final long MEMBER_A_CLUB_MEMBER_ID = 981002L;
    private static final long MEMBER_B_CLUB_MEMBER_ID = 981003L;

    private static final String RAW_PASSWORD = "lock-order-test-only";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 20, 9, 0);

    private static final int WAIT_SECONDS = 30;
    /** 상대가 DB 잠금에서 막혀 있음을 확인하는 짧은 상한. 상대가 잠금을 잡은 뒤에만 쓴다. */
    private static final int BLOCKED_CONFIRM_SECONDS = 2;
    private static final int TERMINATION_SECONDS = 10;

    private static final ThreadFactory DAEMON_THREADS = runnable -> {
        Thread thread = new Thread(runnable, "apply-lock-order-test");
        thread.setDaemon(true);
        return thread;
    };

    @Autowired
    private MatchApplicationService matchApplicationService;

    @Autowired
    private MemberService memberService;

    @MockitoSpyBean
    private MemberAccountLockService accountLockService;

    @MockitoSpyBean
    private ClubService clubService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private boolean fixtureReady;

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

    @BeforeEach
    void prepareCommittedFixture() {
        String database = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        if (!"spoit_mat_test".equals(database)) {
            throw new IllegalStateException("전용 테스트 DB가 아니므로 fixture 생성을 중단합니다.");
        }
        String passwordHash = passwordEncoder.encode(RAW_PASSWORD);
        txTemplate().executeWithoutResult(status -> insertFixture(passwordHash));
        fixtureReady = true;
    }

    @AfterEach
    void deleteCommittedFixture() {
        if (!fixtureReady) {
            return;
        }
        txTemplate().executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    DELETE FROM notification
                     WHERE recipient_user_id BETWEEN ? AND ?
                        OR match_id BETWEEN ? AND ?
                        OR application_id IN (
                            SELECT application_id FROM match_application WHERE match_id BETWEEN ? AND ?
                        )
                    """, USER_ID_FIRST, USER_ID_LAST, MATCH_1_ID, MATCH_2_ID, MATCH_1_ID, MATCH_2_ID);
            // 팀 신청 행은 대표 행을 자기 참조하므로 묶음을 먼저 끊는다.
            jdbcTemplate.update("""
                    UPDATE match_application SET group_application_id = NULL WHERE match_id BETWEEN ? AND ?
                    """, MATCH_1_ID, MATCH_2_ID);
            jdbcTemplate.update("DELETE FROM match_participant WHERE match_id BETWEEN ? AND ?",
                    MATCH_1_ID, MATCH_2_ID);
            jdbcTemplate.update("DELETE FROM match_application WHERE match_id BETWEEN ? AND ?",
                    MATCH_1_ID, MATCH_2_ID);
            jdbcTemplate.update("DELETE FROM social_match WHERE match_id BETWEEN ? AND ?", MATCH_1_ID, MATCH_2_ID);
            jdbcTemplate.update("DELETE FROM club_member WHERE club_id = ?", CLUB_ID);
            jdbcTemplate.update("DELETE FROM club WHERE club_id = ?", CLUB_ID);
            jdbcTemplate.update("DELETE FROM operating_rule WHERE facility_id BETWEEN ? AND ?",
                    FACILITY_1_ID, FACILITY_2_ID);
            jdbcTemplate.update("DELETE FROM facility_sport WHERE facility_id BETWEEN ? AND ?",
                    FACILITY_1_ID, FACILITY_2_ID);
            jdbcTemplate.update("DELETE FROM facility_manager WHERE facility_id BETWEEN ? AND ?",
                    FACILITY_1_ID, FACILITY_2_ID);
            jdbcTemplate.update("DELETE FROM facility WHERE facility_id BETWEEN ? AND ?",
                    FACILITY_1_ID, FACILITY_2_ID);
            jdbcTemplate.update("DELETE FROM sport WHERE sport_id = ?", SPORT_ID);
            jdbcTemplate.update("DELETE FROM region WHERE region_id = ?", REGION_ID);
            jdbcTemplate.update("DELETE FROM app_user WHERE user_id BETWEEN ? AND ?", USER_ID_FIRST, USER_ID_LAST);
        });
        fixtureReady = false;
    }

    /* ---------------- 팀 신청 ↔ 회장 위임 ---------------- */

    @Test
    @DisplayName("팀 신청이 동호회·계정을 잡은 뒤 회장 위임이 시작되면, 위임은 동호회에서 기다렸다가 교착 없이 끝난다")
    void teamApplyFirstThenLeaderTransferBothComplete() throws Exception {
        CountDownLatch teamLockedAccounts = new CountDownLatch(1);
        CountDownLatch releaseTeam = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object locked = invocation.callRealMethod();
            teamLockedAccounts.countDown();
            await(releaseTeam);
            return locked;
        }).when(lockSpy()).lockAllById(anyList());

        ExecutorService pool = Executors.newFixedThreadPool(2, DAEMON_THREADS);
        try {
            Future<String> team = pool.submit(() -> matchApplicationService.applyTeam(
                    MATCH_2_ID, LEADER_USER_ID, CLUB_ID, List.of(MEMBER_A_CLUB_MEMBER_ID, MEMBER_B_CLUB_MEMBER_ID)));
            await(teamLockedAccounts);

            Future<?> transfer = pool.submit(() -> clubService.transferLeader(
                    LEADER_USER_ID, CLUB_ID, MEMBER_A_CLUB_MEMBER_ID));
            assertBlocked(transfer, "회장 위임은 팀 신청이 잡은 동호회 행에서 기다려야 한다");

            releaseTeam.countDown();
            awaitCompletion(team, transfer);
        } finally {
            releaseTeam.countDown();
            shutdown(pool);
        }

        assertThat(activeApplicants(MATCH_2_ID))
                .containsExactly(LEADER_USER_ID, MEMBER_A_USER_ID, MEMBER_B_USER_ID);
        assertThat(clubRole(MEMBER_A_CLUB_MEMBER_ID)).isEqualTo("동호회장");
        assertThat(clubRole(LEADER_CLUB_MEMBER_ID)).isEqualTo("동호회원");
    }

    @Test
    @DisplayName("회장 위임이 동호회를 잡은 뒤 팀 신청이 시작되면, 팀 신청은 동호회에서 기다렸다가 교착 없이 끝난다")
    void leaderTransferFirstThenTeamApplyBothComplete() throws Exception {
        CountDownLatch transferLockedClub = new CountDownLatch(1);
        CountDownLatch teamReachedClubLock = new CountDownLatch(1);
        CountDownLatch releaseTransfer = new CountDownLatch(1);
        // 위임은 동호회 행을 잠근 다음 위임 대상 계정을 잠그러 온다. 계정을 잠그기 직전에 멈춘다.
        doAnswer(invocation -> {
            transferLockedClub.countDown();
            await(releaseTransfer);
            return invocation.callRealMethod();
        }).when(lockSpy()).lockById(MEMBER_A_USER_ID);
        doAnswer(invocation -> {
            teamReachedClubLock.countDown();
            return invocation.callRealMethod();
        }).when(clubSpy()).lockActiveClub(CLUB_ID);

        ExecutorService pool = Executors.newFixedThreadPool(2, DAEMON_THREADS);
        try {
            Future<?> transfer = pool.submit(() -> clubService.transferLeader(
                    LEADER_USER_ID, CLUB_ID, MEMBER_A_CLUB_MEMBER_ID));
            await(transferLockedClub);

            Future<String> team = pool.submit(() -> matchApplicationService.applyTeam(
                    MATCH_2_ID, LEADER_USER_ID, CLUB_ID, List.of(MEMBER_A_CLUB_MEMBER_ID, MEMBER_B_CLUB_MEMBER_ID)));
            await(teamReachedClubLock);
            // 예전 순서(계정 먼저)라면 이 시점에 팀 신청이 위임 대상 계정을 이미 잡고 있어 교착이 생긴다.
            assertBlocked(team, "팀 신청은 위임이 잡은 동호회 행에서 기다려야 한다");

            releaseTransfer.countDown();
            awaitCompletion(transfer, team);
        } finally {
            releaseTransfer.countDown();
            shutdown(pool);
        }

        assertThat(clubRole(MEMBER_A_CLUB_MEMBER_ID)).isEqualTo("동호회장");
        // 팀 신청 대표는 활성 동호회원이면 되므로 위임 뒤에도 신청이 접수된다.
        assertThat(activeApplicants(MATCH_2_ID))
                .containsExactly(LEADER_USER_ID, MEMBER_A_USER_ID, MEMBER_B_USER_ID);
    }

    /* ---------------- 같은 회원의 겹치는 개인·팀 신청 ---------------- */

    @Test
    @DisplayName("같은 회원이 다른 시설의 겹치는 매치에 개인·팀으로 동시에 신청하면 하나만 저장된다")
    void overlappingIndividualAndTeamApplyAtDifferentFacilitiesSavesOnlyOne() throws Exception {
        CountDownLatch individualLockedAccount = new CountDownLatch(1);
        CountDownLatch teamReachedAccountLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object locked = invocation.callRealMethod();
            individualLockedAccount.countDown();
            await(teamReachedAccountLock);
            return locked;
        }).when(lockSpy()).lockById(MEMBER_A_USER_ID);
        doAnswer(invocation -> {
            teamReachedAccountLock.countDown();
            return invocation.callRealMethod();
        }).when(lockSpy()).lockAllById(anyList());

        ExecutorService pool = Executors.newFixedThreadPool(2, DAEMON_THREADS);
        Future<String> individual;
        Future<String> team;
        try {
            individual = pool.submit(() -> matchApplicationService.applyIndividual(MATCH_1_ID, MEMBER_A_USER_ID));
            await(individualLockedAccount);
            team = pool.submit(() -> matchApplicationService.applyTeam(
                    MATCH_2_ID, LEADER_USER_ID, CLUB_ID, List.of(MEMBER_A_CLUB_MEMBER_ID)));

            individual.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThatThrownBy(() -> awaitCompletion(team))
                    .isInstanceOf(MatchApplyException.class)
                    .hasMessageContaining("이미 참가 신청한 다른 매치와 시간이 겹칩니다.");
        } finally {
            teamReachedAccountLock.countDown();
            shutdown(pool);
        }

        assertThat(activeApplicants(MATCH_1_ID)).containsExactly(MEMBER_A_USER_ID);
        assertThat(activeApplicants(MATCH_2_ID)).isEmpty();
    }

    /* ---------------- 회원 탈퇴 선커밋 ---------------- */

    @Test
    @DisplayName("회원 탈퇴가 계정을 먼저 잡고 커밋하면 개인 신청은 탈퇴 상태를 보고 신청 행을 만들지 않는다")
    void withdrawalCommittedFirstBlocksIndividualApply() throws Exception {
        CountDownLatch withdrawalLockedAccount = new CountDownLatch(1);
        CountDownLatch applyReachedAccountLock = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                // 첫 호출은 탈퇴: 계정을 잡은 채 신청이 같은 계정을 잠그러 올 때까지 기다린다.
                Object locked = invocation.callRealMethod();
                withdrawalLockedAccount.countDown();
                await(applyReachedAccountLock);
                return locked;
            }
            applyReachedAccountLock.countDown();
            return invocation.callRealMethod();
        }).when(lockSpy()).lockById(SOLO_USER_ID);

        ExecutorService pool = Executors.newFixedThreadPool(2, DAEMON_THREADS);
        try {
            Future<?> withdrawal = pool.submit(() -> memberService.withdraw(SOLO_USER_ID, RAW_PASSWORD));
            await(withdrawalLockedAccount);
            Future<String> apply = pool.submit(() -> matchApplicationService.applyIndividual(MATCH_1_ID, SOLO_USER_ID));

            withdrawal.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThatThrownBy(() -> awaitCompletion(apply))
                    .isInstanceOf(MatchApplyException.class)
                    .hasMessage("탈퇴한 회원은 신청할 수 없습니다.");
        } finally {
            applyReachedAccountLock.countDown();
            shutdown(pool);
        }

        assertThat(isWithdrawn(SOLO_USER_ID)).isTrue();
        assertThat(applicationCount(MATCH_1_ID, SOLO_USER_ID)).isZero();
    }

    /* ---------------- 동호회 탈퇴 ↔ 팀 신청 ---------------- */

    @Test
    @DisplayName("팀 신청이 동호회를 잡은 동안 팀원의 동호회 탈퇴는 기다리고, 신청 커밋 뒤 모집 중 팀 매치 사유로 거절된다")
    void leaveClubWaitsForTeamApplyHoldingClub() throws Exception {
        CountDownLatch teamLockedAccounts = new CountDownLatch(1);
        CountDownLatch releaseTeam = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object locked = invocation.callRealMethod();
            teamLockedAccounts.countDown();
            await(releaseTeam);
            return locked;
        }).when(lockSpy()).lockAllById(anyList());

        ExecutorService pool = Executors.newFixedThreadPool(2, DAEMON_THREADS);
        try {
            Future<String> team = pool.submit(() -> matchApplicationService.applyTeam(
                    MATCH_2_ID, LEADER_USER_ID, CLUB_ID, List.of(MEMBER_A_CLUB_MEMBER_ID, MEMBER_B_CLUB_MEMBER_ID)));
            await(teamLockedAccounts);

            Future<?> leave = pool.submit(() -> clubService.leaveClub(MEMBER_B_USER_ID, CLUB_ID));
            // 소속을 읽은 뒤 신청을 저장하는 동안 동호회원 변경이 끼어들지 못해야 한다.
            assertBlocked(leave, "동호회 탈퇴는 팀 신청이 잡은 동호회 행에서 기다려야 한다");

            releaseTeam.countDown();
            team.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThatThrownBy(() -> awaitCompletion(leave))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("모집 중인 팀 매치에 신청되어 있어 탈퇴할 수 없습니다.");
        } finally {
            releaseTeam.countDown();
            shutdown(pool);
        }

        assertThat(activeApplicants(MATCH_2_ID))
                .containsExactly(LEADER_USER_ID, MEMBER_A_USER_ID, MEMBER_B_USER_ID);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT left_at IS NULL FROM club_member WHERE club_member_id = ?",
                Boolean.class, MEMBER_B_CLUB_MEMBER_ID)).isTrue();
    }

    /* ---------------- fixture ---------------- */

    /**
     * 회원 5명(대표·팀원 A·팀원 B·개인 신청자·시설 관리자), 7요일 09:00~18:00 운영 시설 2곳, 같은 시간대의
     * 매칭방 2개(시설별 1개), 대표가 회장이고 A·B가 동호회원인 동호회 1개를 커밋한다.
     */
    private void insertFixture(String passwordHash) {
        insertUser(LEADER_USER_ID, "lock-order-leader", "잠금순서 대표", "일반 회원", passwordHash);
        insertUser(MEMBER_A_USER_ID, "lock-order-member-a", "잠금순서 팀원A", "일반 회원", passwordHash);
        insertUser(MEMBER_B_USER_ID, "lock-order-member-b", "잠금순서 팀원B", "일반 회원", passwordHash);
        insertUser(SOLO_USER_ID, "lock-order-solo", "잠금순서 개인", "일반 회원", passwordHash);
        insertUser(MANAGER_USER_ID, "lock-order-manager", "잠금순서 관리자", "시설 관리자", passwordHash);

        jdbcTemplate.update("INSERT INTO region (region_id, parent_region_id, name) VALUES (?, NULL, ?)",
                REGION_ID, "잠금순서 테스트 지역");
        jdbcTemplate.update("INSERT INTO sport (sport_id, name) VALUES (?, ?)", SPORT_ID, "잠금순서 테스트 종목");

        for (long facilityId = FACILITY_1_ID; facilityId <= FACILITY_2_ID; facilityId++) {
            jdbcTemplate.update("""
                    INSERT INTO facility (
                        facility_id, region_id, name, address, latitude, longitude,
                        concurrent_match_limit, status, deleted_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL)
                    """,
                    facilityId, REGION_ID, "잠금순서 시설 " + facilityId, "잠금순서 주소 " + facilityId,
                    new BigDecimal("37.5000000"), new BigDecimal("127.0000000"), 3, "운영 중");
            jdbcTemplate.update("INSERT INTO facility_manager (facility_id, user_id, assigned_at) VALUES (?, ?, ?)",
                    facilityId, MANAGER_USER_ID, CREATED_AT);
            jdbcTemplate.update("""
                    INSERT INTO facility_sport (
                        facility_id, sport_id, default_min, default_max, default_fee, default_skill
                    ) VALUES (?, ?, 1, 10, 0.00, '무관')
                    """, facilityId, SPORT_ID);
            for (int weekday = 1; weekday <= 7; weekday++) {
                jdbcTemplate.update("""
                        INSERT INTO operating_rule (
                            facility_id, weekday, open_time, close_time, end_next_day, is_open
                        ) VALUES (?, ?, '09:00:00', '18:00:00', FALSE, TRUE)
                        """, facilityId, weekday);
            }
        }
        insertMatch(MATCH_1_ID, FACILITY_1_ID);
        insertMatch(MATCH_2_ID, FACILITY_2_ID);

        jdbcTemplate.update("""
                INSERT INTO club (
                    club_id, sport_id, club_name, intro, max_members,
                    activity_days, activity_start_time, activity_end_time, created_at
                ) VALUES (?, ?, ?, ?, 10, '2,4', '19:00:00', '21:00:00', ?)
                """, CLUB_ID, SPORT_ID, "잠금순서 테스트 동호회", "잠금순서 테스트 소개", CREATED_AT);
        insertClubMember(LEADER_CLUB_MEMBER_ID, LEADER_USER_ID, "동호회장");
        insertClubMember(MEMBER_A_CLUB_MEMBER_ID, MEMBER_A_USER_ID, "동호회원");
        insertClubMember(MEMBER_B_CLUB_MEMBER_ID, MEMBER_B_USER_ID, "동호회원");
    }

    private void insertUser(long userId, String emailLocal, String nickname, String role, String passwordHash) {
        jdbcTemplate.update("""
                INSERT INTO app_user (user_id, email, password_hash, nickname, phone, role, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, userId, emailLocal + "@example.invalid", passwordHash, nickname,
                "010-9810-" + String.format("%04d", userId % 10000), role, CREATED_AT);
    }

    private void insertMatch(long matchId, long facilityId) {
        jdbcTemplate.update("""
                INSERT INTO social_match (
                    match_id, facility_id, sport_id, created_by, title, start_at, end_at,
                    min_participants, max_participants, team_a_capacity, team_b_capacity,
                    fee_per_person, required_skill, status, assignment_method, rating_status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                matchId, facilityId, SPORT_ID, MANAGER_USER_ID, "잠금순서 매칭방 " + matchId,
                MATCH_START, MATCH_END, 1, 10, 5, 5, new BigDecimal("0.00"), "무관",
                MatchStatus.RECRUITING.dbValue(), "MANUAL", "WAITING");
    }

    private void insertClubMember(long clubMemberId, long userId, String role) {
        jdbcTemplate.update("""
                INSERT INTO club_member (club_member_id, club_id, user_id, role, joined_at) VALUES (?, ?, ?, ?, ?)
                """, clubMemberId, CLUB_ID, userId, role, CREATED_AT.plusMinutes(clubMemberId % 1000));
    }

    /* ---------------- 조회·대기 도우미 ---------------- */

    private List<Long> activeApplicants(long matchId) {
        return jdbcTemplate.queryForList("""
                SELECT applicant_user_id FROM match_application
                 WHERE match_id = ? AND status IN (?, ?)
                 ORDER BY applicant_user_id
                """, Long.class, matchId, ApplicationStatus.WAITING.dbValue(), ApplicationStatus.CONFIRMED.dbValue());
    }

    private int applicationCount(long matchId, long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM match_application WHERE match_id = ? AND applicant_user_id = ?",
                Integer.class, matchId, userId);
        return count == null ? 0 : count;
    }

    private String clubRole(long clubMemberId) {
        return jdbcTemplate.queryForObject("SELECT role FROM club_member WHERE club_member_id = ?",
                String.class, clubMemberId);
    }

    private boolean isWithdrawn(long userId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM app_user WHERE user_id = ?", Boolean.class, userId));
    }

    /**
     * spy는 트랜잭션 프록시 안쪽에 있다. 프록시에 대고 스텁을 걸면 트랜잭션 인터셉터(MANDATORY 등)를 먼저 지나므로
     * 스텁은 프록시를 벗긴 spy 자체에 건다. 실제 호출은 여전히 프록시를 거친다.
     */
    private MemberAccountLockService lockSpy() {
        return AopTestUtils.getUltimateTargetObject(accountLockService);
    }

    private ClubService clubSpy() {
        return AopTestUtils.getUltimateTargetObject(clubService);
    }

    private TransactionTemplate txTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    /** 상대가 DB 잠금을 잡은 뒤에만 부른다. 짧은 시간 안에 끝나지 않으면 잠금에서 막혀 있는 것으로 본다. */
    private static void assertBlocked(Future<?> future, String description) throws Exception {
        try {
            future.get(BLOCKED_CONFIRM_SECONDS, TimeUnit.SECONDS);
            throw new AssertionError(description + " (기다리지 않고 먼저 끝났다)");
        } catch (TimeoutException expected) {
            // 잠금 대기 중
        } catch (ExecutionException exception) {
            throw new AssertionError(description + " (기다리지 않고 실패했다)", exception.getCause());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단되었습니다.", exception);
        }
    }

    /** 모든 future를 기다리고, 처음 실패한 원인을 그대로 던진다(교착 희생자 예외도 여기서 드러난다). */
    private static void awaitCompletion(Future<?>... futures) throws Exception {
        Throwable failure = null;
        for (Future<?> future : futures) {
            try {
                future.get(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                if (failure == null) {
                    failure = exception.getCause();
                }
            } catch (TimeoutException exception) {
                if (failure == null) {
                    failure = new IllegalStateException("동시성 테스트 스레드가 제한 시간 안에 끝나지 않았습니다.", exception);
                }
            }
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof Exception exception) {
            throw exception;
        }
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
    }

    private static void shutdown(ExecutorService pool) {
        pool.shutdownNow();
        try {
            pool.awaitTermination(TERMINATION_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
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
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }
}
