package co.kr.spoit.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.ui.ExtendedModelMap;

import co.kr.spoit.member.mail.PasswordResetEmailSender;
import co.kr.spoit.member.mail.PasswordResetEmailException;
import co.kr.spoit.member.domain.PasswordResetSessionState;
import co.kr.spoit.member.service.PasswordResetService;

/** 실제 템플릿, HTTP 세션과 전용 임시 MySQL 트랜잭션을 통과하는 비밀번호 재설정 검증. */
@EnabledIfEnvironmentVariable(named = "MAT_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    private static final Pattern TEST_DATABASE_URL = Pattern.compile(
            "jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):([0-9]{1,5})/spoit_mat_test");
    private static final String JDBC_OPTIONS = "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            + "&allowPublicKeyRetrieval=true&useSSL=false";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);
    private static final String OLD_PASSWORD = "Original123";
    private static final String NEW_PASSWORD = "Replacement456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private PasswordResetController passwordResetController;

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    @MockitoBean
    private PasswordResetEmailSender emailSender;

    private BlockingQueue<String> deliveredCodes;
    private String email;
    private String ip;
    private long userId;

    @DynamicPropertySource
    static void configureTestDatabase(DynamicPropertyRegistry registry) {
        String url = requiredEnvironmentVariable("MAT_TEST_DB_URL");
        var match = TEST_DATABASE_URL.matcher(url);
        if (!match.matches()) {
            throw new IllegalArgumentException("MAT_TEST_DB_URL은 명시적 포트의 전용 loopback 테스트 DB여야 합니다.");
        }
        int port = Integer.parseInt(match.group(1));
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("전용 테스트 DB 포트가 올바르지 않습니다.");
        }
        String username = requiredEnvironmentVariable("MAT_TEST_DB_USERNAME");
        String password = requiredEnvironmentVariable("MAT_TEST_DB_PASSWORD");
        registry.add("spring.datasource.url", () -> url + JDBC_OPTIONS);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeEach
    void createIsolatedMember() {
        assertTestDatabase();
        email = "password-reset-" + UUID.randomUUID() + "@example.invalid";
        ip = "10.20.0." + NEXT_IP.getAndIncrement();
        jdbcTemplate.update("INSERT INTO app_user (email, password_hash, nickname, phone, role) VALUES (?, ?, ?, ?, ?)",
                email, passwordEncoder.encode(OLD_PASSWORD), "비밀번호 테스트", "010-0000-0000", "MEMBER");
        userId = jdbcTemplate.queryForObject("SELECT user_id FROM app_user WHERE email = ?", Long.class, email);
        deliveredCodes = new LinkedBlockingQueue<>();
        when(emailSender.isConfigured()).thenReturn(true);
        doAnswer(invocation -> {
            deliveredCodes.add(invocation.getArgument(1));
            return null;
        }).when(emailSender).sendCode(anyString(), anyString(), anyInt());
    }

    @AfterEach
    void removeIsolatedMember() {
        if (userId == 0) {
            return;
        }
        assertTestDatabase();
        jdbcTemplate.update("DELETE FROM password_reset_challenge WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM app_user WHERE user_id = ? AND email = ?", userId, email);
    }

    @Test
    @DisplayName("재시작 후 기존 번호는 폐기되고 새 번호를 다시 발급받을 수 있다")
    void restartedServiceRejectsOldCodeAndRequestsNewOne() throws Exception {
        assertThat(passwordResetService.isConfigured()).isTrue();
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(sendCode(session, email)).andExpect(model().attribute("step", "VERIFY"));
        String originalId = sessionState(session).challengeId();
        String originalCode = deliveredCode();
        assertThat(passwordResetService.requestCode(email, ip).accepted()).isFalse();

        PasswordResetService restarted = beanFactory.createBean(PasswordResetService.class);
        try {
            assertThat(restarted.isConfigured()).isTrue();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(ip);
            ExtendedModelMap model = new ExtendedModelMap();
            new PasswordResetController(restarted).verifyCode(null, originalCode, request, session, model);
            assertThat(model.get("step")).isEqualTo("REQUEST");
            assertThat((String) model.get("error")).contains("다시 요청");
            assertThat(restarted.status(originalId, null).active()).isFalse();

            // 재시작 시 새로운 키로 이메일/IP 지문을 만들므로 기존 제한 버킷은 이어지지 않는다.
            var fresh = restarted.requestCode(email, ip);
            assertThat(fresh.accepted()).isTrue();
            assertThat(restarted.verifyCode(fresh.challengeId(), email, deliveredCode(), ip).verified()).isTrue();
        } finally {
            beanFactory.destroyBean(restarted);
        }
    }

    @Test
    @DisplayName("재시작 전 인증된 재설정 권한은 새 인스턴스에서 사용할 수 없다")
    void restartedServiceRejectsPreviousVerifiedGrant() throws Exception {
        var issued = passwordResetService.requestCode(email, ip);
        assertThat(issued.accepted()).isTrue();
        var verified = passwordResetService.verifyCode(issued.challengeId(), email, deliveredCode(), ip);
        assertThat(verified.verified()).isTrue();
        assertThat(passwordResetService.status(issued.challengeId(), verified.rawGrant()).verified()).isTrue();

        PasswordResetService restarted = beanFactory.createBean(PasswordResetService.class);
        try {
            assertThat(restarted.status(issued.challengeId(), verified.rawGrant()).verified()).isFalse();
            assertThat(restarted.resetPassword(issued.challengeId(), verified.rawGrant(),
                    NEW_PASSWORD, NEW_PASSWORD)).isFalse();
            assertThat(passwordEncoder.matches(OLD_PASSWORD, passwordHash())).isTrue();
        } finally {
            beanFactory.destroyBean(restarted);
        }
    }

    @Test
    @DisplayName("실제 화면은 CSRF로 보호되고 인증한 세션에서만 재설정하며 기존 로그인은 폐기된다")
    void resetsOnlyInVerifiedSessionAndRevokesExistingLogin() throws Exception {
        MockHttpSession recoverySession = new MockHttpSession();
        mockMvc.perform(get("/login/find-password").session(recoverySession))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/find-password"))
                .andExpect(model().attribute("step", "REQUEST"))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mockMvc.perform(post("/login/find-password/send-code").session(recoverySession).param("email", email))
                .andExpect(status().isForbidden());

        MockHttpSession loggedInSession = (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", OLD_PASSWORD))
                .andExpect(redirectedUrl("/matches"))
                .andReturn().getRequest().getSession(false);
        assertThat(loggedInSession).isNotNull();

        MvcResult sent = mockMvc.perform(sendCode(recoverySession, email))
                .andExpect(status().isOk())
                .andExpect(model().attribute("step", "VERIFY"))
                .andExpect(content().string(containsString("name=\"code\""))).andReturn();
        String code = deliveredCode();
        assertThat(sent.getResponse().getContentAsString()).doesNotContain(code);
        mockMvc.perform(get("/login/find-password").session(recoverySession))
                .andExpect(status().isOk()).andExpect(model().attribute("step", "VERIFY"))
                .andExpect(content().string(containsString(email)));

        MockHttpSession otherSession = new MockHttpSession();
        MvcResult unauthorized = mockMvc.perform(verifyCode(otherSession, code))
                .andExpect(status().isOk()).andReturn();
        assertThat(unauthorized.getModelAndView().getModel().get("step")).isNotEqualTo("RESET");
        assertThat(passwordEncoder.matches(OLD_PASSWORD, passwordHash())).isTrue();

        mockMvc.perform(withIp(post("/login/find-password/verify-code").with(csrf())
                        .session(recoverySession).param("code", code)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("step", "RESET"))
                .andExpect(content().string(containsString("name=\"passwordConfirm\"")));
        PasswordResetSessionState originalGrant = sessionState(recoverySession);
        mockMvc.perform(verifyCode(recoverySession, code))
                .andExpect(status().isOk()).andExpect(model().attribute("step", "RESET"));
        assertThat(sessionState(recoverySession)).isEqualTo(originalGrant);
        mockMvc.perform(get("/login/find-password").session(recoverySession))
                .andExpect(status().isOk()).andExpect(model().attribute("step", "RESET"))
                .andExpect(content().string(containsString(email)));
        PasswordResetSessionState usedGrant = sessionState(recoverySession);
        mockMvc.perform(reset(recoverySession, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?message=*"));
        assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHash())).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, passwordHash())).isFalse();

        mockMvc.perform(get("/mypage").session(loggedInSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login?error=*"));
        assertThat(passwordResetService.resetPassword(usedGrant.challengeId(), usedGrant.rawGrant(),
                "Another789", "Another789")).isFalse();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHash())).isTrue();
        mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", OLD_PASSWORD))
                .andExpect(redirectedUrlPattern("/login?error=*"));
        mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", NEW_PASSWORD))
                .andExpect(redirectedUrl("/matches"));
    }

    @Test
    @DisplayName("잘못된 인증번호 횟수는 요청마다 커밋되고 다섯 번 뒤 올바른 번호도 거부된다")
    void commitsFailedAttemptsAndRejectsTheCorrectCodeAfterTheLimit() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(sendCode(session, email)).andExpect(model().attribute("step", "VERIFY"));
        String actualCode = deliveredCode();
        String wrongCode = "000000".equals(actualCode) ? "000001" : "000000";
        for (int count = 1; count <= 5; count++) {
            MvcResult failed = mockMvc.perform(verifyCode(session, wrongCode))
                    .andExpect(status().isOk()).andReturn();
            assertThat(failed.getResponse().getContentAsString()).doesNotContain(wrongCode);
            Integer attempts = jdbcTemplate.queryForObject(
                    "SELECT attempt_count FROM password_reset_challenge WHERE user_id = ?", Integer.class, userId);
            assertThat(attempts).isEqualTo(count);
        }
        MvcResult rejected = mockMvc.perform(verifyCode(session, actualCode))
                .andExpect(status().isOk()).andReturn();
        assertThat(rejected.getModelAndView().getModel().get("step")).isNotEqualTo("RESET");
        assertThat(passwordEncoder.matches(OLD_PASSWORD, passwordHash())).isTrue();
    }

    @Test
    @DisplayName("만료된 도전과 재발급 전 세션의 인증번호는 사용할 수 없다")
    void expirationAndResendInvalidateThePreviousChallenge() throws Exception {
        MockHttpSession firstSession = new MockHttpSession();
        mockMvc.perform(sendCode(firstSession, email)).andExpect(model().attribute("step", "VERIFY"));
        String oldCode = deliveredCode();
        jdbcTemplate.update("UPDATE password_reset_challenge SET expires_at = ? WHERE user_id = ?",
                LocalDateTime.of(2000, 1, 1, 0, 0), userId);
        MvcResult expired = mockMvc.perform(verifyCode(firstSession, oldCode))
                .andExpect(status().isOk()).andReturn();
        assertThat(expired.getModelAndView().getModel().get("step")).isNotEqualTo("RESET");

        releaseCooldown();
        MockHttpSession secondSession = new MockHttpSession();
        mockMvc.perform(sendCode(secondSession, email)).andExpect(model().attribute("step", "VERIFY"));
        String newCode = deliveredCode();
        MvcResult oldSession = mockMvc.perform(verifyCode(firstSession, oldCode))
                .andExpect(status().isOk()).andReturn();
        assertThat(oldSession.getModelAndView().getModel().get("step")).isNotEqualTo("RESET");
        mockMvc.perform(verifyCode(secondSession, newCode)).andExpect(model().attribute("step", "RESET"));
        PasswordResetSessionState replacedGrant = sessionState(secondSession);

        // 발급된 재설정 권한도 다른 브라우저에서 재발급하면 즉시 폐기된다.
        releaseCooldown();
        MockHttpSession thirdSession = new MockHttpSession();
        mockMvc.perform(sendCode(thirdSession, email)).andExpect(model().attribute("step", "VERIFY"));
        String latestCode = deliveredCode();
        MvcResult replaced = mockMvc.perform(verifyCode(secondSession, newCode))
                .andExpect(status().isOk()).andReturn();
        assertThat(replaced.getModelAndView().getModel().get("step")).isNotEqualTo("RESET");
        assertThat(passwordResetService.resetPassword(replacedGrant.challengeId(), replacedGrant.rawGrant(),
                "Invalidated123", "Invalidated123")).isFalse();
        mockMvc.perform(verifyCode(thirdSession, latestCode)).andExpect(model().attribute("step", "RESET"));
    }

    @Test
    @DisplayName("재발급해도 이메일의 잘못된 번호 누적 제한은 초기화되지 않는다")
    void resendingDoesNotResetTheCumulativeFailureLimit() throws Exception {
        MockHttpSession current = null;
        String actualCode = null;
        for (int attemptCount : new int[] {4, 4, 2}) {
            if (current != null) {
                releaseCooldown();
            }
            current = new MockHttpSession();
            mockMvc.perform(sendCode(current, email)).andExpect(model().attribute("step", "VERIFY"));
            actualCode = deliveredCode();
            String wrongCode = "000000".equals(actualCode) ? "000001" : "000000";
            for (int count = 0; count < attemptCount; count++) {
                mockMvc.perform(verifyCode(current, wrongCode)).andExpect(status().isOk());
            }
        }
        Integer failureCount = jdbcTemplate.queryForObject(
                "SELECT failure_count FROM password_reset_rate_limit WHERE bucket_type = 'EMAIL' "
                        + "AND bucket_fingerprint IN (SELECT email_fingerprint FROM password_reset_challenge "
                        + "WHERE user_id = ?)", Integer.class, userId);
        assertThat(failureCount).isEqualTo(10);
        MvcResult blocked = mockMvc.perform(verifyCode(current, actualCode)).andExpect(status().isOk()).andReturn();
        assertThat(blocked.getModelAndView().getModel().get("step")).isNotEqualTo("RESET");
    }

    @Test
    @DisplayName("비밀번호 정책을 서버에서 확인하며 이용정지 종료 시각은 변경하지 않는다")
    void validatesPasswordAndPreservesSuspension() throws Exception {
        LocalDateTime suspendedUntil = LocalDateTime.of(2099, 12, 1, 0, 0);
        jdbcTemplate.update("UPDATE app_user SET penalty_time = ? WHERE user_id = ?", suspendedUntil, userId);
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(sendCode(session, email)).andExpect(model().attribute("step", "VERIFY"));
        mockMvc.perform(verifyCode(session, deliveredCode())).andExpect(model().attribute("step", "RESET"));

        MvcResult weak = mockMvc.perform(reset(session, "shortP1", "shortP1"))
                .andExpect(status().isOk()).andExpect(model().attribute("step", "RESET")).andReturn();
        assertThat(weak.getResponse().getContentAsString()).doesNotContain("shortP1");
        MvcResult mismatch = mockMvc.perform(reset(session, NEW_PASSWORD, "Different789"))
                .andExpect(status().isOk()).andExpect(model().attribute("step", "RESET")).andReturn();
        assertThat(mismatch.getResponse().getContentAsString()).doesNotContain(NEW_PASSWORD, "Different789");
        mockMvc.perform(reset(session, "abc12345" + "한".repeat(23), "abc12345" + "한".repeat(23)))
                .andExpect(status().isOk()).andExpect(model().attribute("step", "RESET"));
        assertThat(passwordEncoder.matches(OLD_PASSWORD, passwordHash())).isTrue();

        mockMvc.perform(reset(session, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/login?message=*"));
        assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHash())).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT penalty_time FROM app_user WHERE user_id = ?",
                LocalDateTime.class, userId)).isEqualTo(suspendedUntil);
    }

    @Test
    @DisplayName("탈퇴 회원과 미가입 이메일은 같은 안내를 받고 인증번호를 발송하지 않는다")
    void deletedAndUnknownAddressesHaveTheSamePublicResponse() throws Exception {
        jdbcTemplate.update("UPDATE app_user SET deleted_at = ? WHERE user_id = ?",
                LocalDateTime.of(2025, 1, 1, 0, 0), userId);
        MvcResult deleted = mockMvc.perform(sendCode(new MockHttpSession(), email))
                .andExpect(status().isOk()).andExpect(model().attribute("step", "VERIFY")).andReturn();
        MvcResult unknown = mockMvc.perform(sendCode(new MockHttpSession(),
                        "unknown-" + UUID.randomUUID() + "@example.invalid"))
                .andExpect(status().isOk()).andExpect(model().attribute("step", "VERIFY")).andReturn();

        assertThat(deleted.getModelAndView().getModel().get("message"))
                .isEqualTo(unknown.getModelAndView().getModel().get("message"));
        assertThat(deliveredCodes.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, passwordHash())).isTrue();
    }

    @Test
    @DisplayName("Gmail 발송 실패 후 도전을 폐기하고 인증번호를 더 이상 사용할 수 없다")
    void emailDeliveryFailureInvalidatesTheChallenge() throws Exception {
        doAnswer(invocation -> {
            deliveredCodes.add(invocation.getArgument(1));
            throw new PasswordResetEmailException("TEST_FAILURE", "Test delivery failed.");
        }).when(emailSender).sendCode(anyString(), anyString(), anyInt());
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(sendCode(session, email)).andExpect(status().isOk());
        String code = deliveredCode();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT consumed_at FROM password_reset_challenge WHERE user_id = ?",
                        LocalDateTime.class, userId)).isNotNull());
        MvcResult rejected = mockMvc.perform(verifyCode(session, code)).andReturn();
        assertThat(rejected.getModelAndView().getModel().get("step")).isNotEqualTo("RESET");
        assertThat(passwordEncoder.matches(OLD_PASSWORD, passwordHash())).isTrue();
    }

    @Test
    @DisplayName("이미 로그인한 사용자가 비밀번호를 바꾸면 현재 세션의 인증도 지운다")
    void resetFromAnAuthenticatedSessionLogsOutThatSession() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", OLD_PASSWORD))
                .andExpect(redirectedUrl("/matches"))
                .andReturn().getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(sendCode(session, email)).andExpect(model().attribute("step", "VERIFY"));
        mockMvc.perform(verifyCode(session, deliveredCode())).andExpect(model().attribute("step", "RESET"));
        mockMvc.perform(reset(session, NEW_PASSWORD, NEW_PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("/login?message=*"))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().maxAge("remember-me", 0));
        assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHash())).isTrue();
        if (!session.isInvalid()) {
            assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        }
    }

    @Test
    @DisplayName("같은 세션의 인증 확인이 동시에 제출되어도 발급된 grant가 유지된다")
    void concurrentVerificationRequestsKeepTheVerifiedSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(sendCode(session, email)).andExpect(model().attribute("step", "VERIFY"));
        String code = deliveredCode();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> first = executor.submit(() -> verifyDirectlyAfter(start, session, code));
            Future<Object> second = executor.submit(() -> verifyDirectlyAfter(start, session, code));
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("RESET");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("RESET");
            PasswordResetSessionState state = sessionState(session);
            assertThat(state.rawGrant()).isNotBlank();
            assertThat(passwordResetService.status(state.challengeId(), state.rawGrant()).verified()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    private Object verifyDirectlyAfter(CountDownLatch start, MockHttpSession session, String code)
            throws InterruptedException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        ExtendedModelMap model = new ExtendedModelMap();
        start.await();
        passwordResetController.verifyCode(null, code, request, session, model);
        return model.get("step");
    }

    @Test
    @DisplayName("동시에 같은 재설정 권한을 사용해도 비밀번호 변경은 한 번만 커밋된다")
    void concurrentUseOfGrantChangesPasswordOnlyOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(sendCode(session, email)).andExpect(model().attribute("step", "VERIFY"));
        mockMvc.perform(verifyCode(session, deliveredCode())).andExpect(model().attribute("step", "RESET"));
        PasswordResetSessionState state = sessionState(session);
        assertThat(state.rawGrant()).isNotBlank();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return passwordResetService.resetPassword(state.challengeId(), state.rawGrant(),
                        "FirstReset123", "FirstReset123");
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return passwordResetService.resetPassword(state.challengeId(), state.rawGrant(),
                        "SecondReset456", "SecondReset456");
            });
            start.countDown();
            boolean firstChanged = first.get(10, TimeUnit.SECONDS);
            boolean secondChanged = second.get(10, TimeUnit.SECONDS);
            assertThat(firstChanged).isNotEqualTo(secondChanged);
            assertThat(passwordEncoder.matches(firstChanged ? "FirstReset123" : "SecondReset456", passwordHash()))
                    .isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("마이페이지 비밀번호 변경은 이전 이메일 재설정 권한과 기존 세션을 폐기한다")
    void passwordChangeInvalidatesPriorResetGrantAndSessions() throws Exception {
        MockHttpSession recovery = new MockHttpSession();
        mockMvc.perform(sendCode(recovery, email)).andExpect(model().attribute("step", "VERIFY"));
        mockMvc.perform(verifyCode(recovery, deliveredCode())).andExpect(model().attribute("step", "RESET"));
        PasswordResetSessionState grant = Collections.list(recovery.getAttributeNames()).stream()
                .map(recovery::getAttribute).filter(PasswordResetSessionState.class::isInstance)
                .map(PasswordResetSessionState.class::cast).findFirst().orElseThrow();

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", OLD_PASSWORD))
                .andExpect(redirectedUrl("/matches")).andReturn().getRequest().getSession(false);
        MockHttpSession otherSession = (MockHttpSession) mockMvc.perform(post("/login").with(csrf())
                        .param("email", email).param("password", OLD_PASSWORD))
                .andExpect(redirectedUrl("/matches")).andReturn().getRequest().getSession(false);

        mockMvc.perform(post("/mypage/password").session(session)
                        .param("currentPassword", OLD_PASSWORD).param("newPassword", NEW_PASSWORD)
                        .param("newPasswordConfirm", NEW_PASSWORD))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/mypage/password").with(csrf()).session(session)
                        .param("currentPassword", "wrong").param("newPassword", NEW_PASSWORD)
                        .param("newPasswordConfirm", NEW_PASSWORD))
                .andExpect(redirectedUrlPattern("/mypage/profile?passwordError=*#password-change"));
        assertThat(passwordEncoder.matches(OLD_PASSWORD, passwordHash())).isTrue();

        mockMvc.perform(post("/mypage/password").with(csrf()).session(session)
                        .param("currentPassword", OLD_PASSWORD).param("newPassword", NEW_PASSWORD)
                        .param("newPasswordConfirm", NEW_PASSWORD))
                .andExpect(redirectedUrlPattern("/login?message=*"))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().maxAge("remember-me", 0));
        assertThat(session.isInvalid()).isTrue();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, passwordHash())).isTrue();
        assertThat(passwordResetService.resetPassword(grant.challengeId(), grant.rawGrant(),
                "Another789", "Another789")).isFalse();
        mockMvc.perform(get("/mypage").session(otherSession))
                .andExpect(redirectedUrlPattern("/login?error=*"));
        mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", OLD_PASSWORD))
                .andExpect(redirectedUrlPattern("/login?error=*"));
        mockMvc.perform(post("/login").with(csrf()).param("email", email).param("password", NEW_PASSWORD))
                .andExpect(redirectedUrl("/matches"));
        mockMvc.perform(post("/mypage/password").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private MockHttpServletRequestBuilder sendCode(MockHttpSession session, String recipient) {
        return withIp(post("/login/find-password/send-code").with(csrf()).session(session).param("email", recipient));
    }

    private static PasswordResetSessionState sessionState(MockHttpSession session) {
        return Collections.list(session.getAttributeNames()).stream()
                .map(session::getAttribute)
                .filter(PasswordResetSessionState.class::isInstance)
                .map(PasswordResetSessionState.class::cast)
                .findFirst().orElseThrow();
    }

    private MockHttpServletRequestBuilder verifyCode(MockHttpSession session, String code) {
        return withIp(post("/login/find-password/verify-code").with(csrf()).session(session)
                .param("email", email).param("code", code));
    }

    private MockHttpServletRequestBuilder reset(MockHttpSession session, String password, String confirmation) {
        return withIp(post("/login/find-password/reset").with(csrf()).session(session)
                .param("email", email).param("password", password).param("passwordConfirm", confirmation));
    }

    private MockHttpServletRequestBuilder withIp(MockHttpServletRequestBuilder request) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(ip);
            return servletRequest;
        });
    }

    private String deliveredCode() throws InterruptedException {
        String code = deliveredCodes.poll(5, TimeUnit.SECONDS);
        assertThat(code).as("비동기 Gmail 발송의 인증번호").matches("[0-9]{6}");
        return code;
    }

    private String passwordHash() {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM app_user WHERE user_id = ?", String.class, userId);
    }

    private void releaseCooldown() {
        jdbcTemplate.update("UPDATE password_reset_rate_limit SET last_requested_at = ? "
                        + "WHERE (bucket_type = 'EMAIL' AND bucket_fingerprint IN "
                        + "(SELECT email_fingerprint FROM password_reset_challenge WHERE user_id = ?)) "
                        + "OR (bucket_type = 'IP' AND bucket_fingerprint IN "
                        + "(SELECT request_ip_fingerprint FROM password_reset_challenge WHERE user_id = ?))",
                LocalDateTime.of(2000, 1, 1, 0, 0), userId, userId);
    }

    private void assertTestDatabase() {
        if (!"spoit_mat_test".equals(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))) {
            throw new IllegalStateException("전용 테스트 DB가 아니므로 테스트를 중단합니다.");
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경변수가 필요합니다.");
        }
        return value;
    }
}
