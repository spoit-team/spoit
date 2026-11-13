package co.kr.spoit.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.PasswordResetChallenge;
import co.kr.spoit.member.domain.PasswordResetRateLimitBucket;
import co.kr.spoit.member.dto.PasswordResetChallengeStatus;
import co.kr.spoit.member.dto.PasswordResetRequestResult;
import co.kr.spoit.member.dto.PasswordResetVerificationResult;
import co.kr.spoit.member.mail.PasswordResetEmailException;
import co.kr.spoit.member.mail.PasswordResetEmailSender;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.mapper.PasswordResetMapper;
import co.kr.spoit.member.security.MemberSessionRevocationService;

/** 이메일 인증번호 발급·확인과 일회용 권한을 통한 비밀번호 변경을 수행한다. */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[0-9]{6}$");
    private static final int EMAIL_MAX_LENGTH = 255;
    private static final int CODE_TTL_MINUTES = 10;
    private static final int GRANT_TTL_MINUTES = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_CHALLENGE_ATTEMPTS = 5;
    private static final int MAX_BUCKET_FAILURES = 10;
    private static final int MAX_EMAIL_REQUESTS = 5;
    private static final int MAX_IP_REQUESTS = 20;
    private static final Duration RATE_WINDOW = Duration.ofHours(1);
    private static final Duration RATE_BUCKET_RETENTION = Duration.ofDays(1);

    private final PasswordResetMapper passwordResetMapper;
    private final MemberMapper memberMapper;
    private final MemberAccountLockService accountLockService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailSender emailSender;
    private final MemberSessionRevocationService sessionRevocationService;
    private final TransactionTemplate transactions;
    private final byte[] codeSecret = new byte[32];
    private final SecureRandom secureRandom = new SecureRandom();
    private final ThreadPoolExecutor emailExecutor = new ThreadPoolExecutor(
            1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32), mailThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    public PasswordResetService(PasswordResetMapper passwordResetMapper, MemberMapper memberMapper,
            MemberAccountLockService accountLockService, PasswordEncoder passwordEncoder,
            PasswordResetEmailSender emailSender, MemberSessionRevocationService sessionRevocationService,
            PlatformTransactionManager transactionManager) {
        this.passwordResetMapper = passwordResetMapper;
        this.memberMapper = memberMapper;
        this.accountLockService = accountLockService;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.sessionRevocationService = sessionRevocationService;
        this.transactions = new TransactionTemplate(transactionManager);
        this.secureRandom.nextBytes(this.codeSecret);
    }

    /** Gmail API sender가 준비된 경우에만 새 코드를 발급할 수 있다. */
    public boolean isConfigured() {
        return emailSender.isConfigured();
    }

    /**
     * 코드 발급 요청. 전역 정리는 email/IP 버킷을 잠그기 전에 별도 트랜잭션으로 제한된 양만 수행한다.
     * Gmail API 전송은 DB 트랜잭션 커밋 뒤 제한된 executor에서 호출한다.
     */
    public PasswordResetRequestResult requestCode(String email, String requestIp) {
        if (!isConfigured()) {
            return new PasswordResetRequestResult(null, false, false, 0, 0);
        }
        String normalizedEmail = normalizeAndValidateEmail(email);
        Long targetUserId = this.passwordResetMapper.findActiveUserIdByEmail(normalizedEmail);
        LocalDateTime now = now();

        this.transactions.executeWithoutResult(status -> this.passwordResetMapper.deleteExpiredChallenges(now()));
        this.transactions.executeWithoutResult(status -> this.passwordResetMapper.deleteExpiredRateLimitBuckets(
                now().minus(RATE_BUCKET_RETENTION)));

        IssuedChallenge issued = this.transactions.execute(status ->
                issueCodeInTransaction(normalizedEmail, normalizeIp(requestIp), targetUserId, now));
        if (issued == null) {
            throw new IllegalStateException("비밀번호 재설정 요청을 처리하지 못했습니다.");
        }
        if (issued.email() != null) {
            dispatchEmail(issued.challengeId(), issued.email(), issued.code());
        }
        return issued.result();
    }

    /** 세션의 challenge ID와 grant가 같은 유효 도전에 속하는지 확인해 화면 단계를 복원한다. */
    public PasswordResetChallengeStatus status(String challengeId, String rawGrant) {
        if (challengeId == null || challengeId.isBlank()) {
            return new PasswordResetChallengeStatus(false, false, 0, 0);
        }
        PasswordResetChallenge challenge = this.passwordResetMapper.findChallenge(challengeId);
        if (challenge == null || challenge.getConsumedAt() != null || challenge.getAttemptCount() >= MAX_CHALLENGE_ATTEMPTS) {
            return new PasswordResetChallengeStatus(false, false, 0, 0);
        }
        LocalDateTime now = now();
        int resendInSeconds = remainingSeconds(challenge.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS), now);

        if (rawGrant != null && challenge.getVerifiedAt() != null && challenge.getGrantExpiresAt() != null
                && challenge.getGrantExpiresAt().isAfter(now)
                && constantTimeEquals(challenge.getGrantHmac(), grantHmac(challengeId, rawGrant))) {
            return new PasswordResetChallengeStatus(true, true,
                    remainingSeconds(challenge.getGrantExpiresAt(), now), resendInSeconds);
        }
        if (challenge.getVerifiedAt() == null && challenge.getExpiresAt().isAfter(now)) {
            return new PasswordResetChallengeStatus(true, false,
                    remainingSeconds(challenge.getExpiresAt(), now), resendInSeconds);
        }
        return new PasswordResetChallengeStatus(false, false, 0, resendInSeconds);
    }

    /** 인증번호 검증은 틀린 횟수를 커밋한 뒤 결과를 반환해 실패 횟수가 롤백되지 않게 한다. */
    public PasswordResetVerificationResult verifyCode(String challengeId, String email, String code,
            String requestIp) {
        String normalizedEmail = normalizeAndValidateEmail(email);
        if (challengeId == null || challengeId.isBlank()) {
            return invalidVerification(0, 0);
        }
        PasswordResetChallenge initial = this.passwordResetMapper.findChallenge(challengeId);
        String emailFingerprint = initial == null
                ? fingerprint("email-bucket", normalizedEmail)
                : initial.getEmailFingerprint();
        return this.transactions.execute(status -> verifyCodeInTransaction(challengeId, normalizedEmail,
                emailFingerprint, normalizeIp(requestIp), code));
    }

    /** 검증 완료 서버 권한으로만 비밀번호를 바꾸고 코드·grant 소비도 같은 트랜잭션에서 커밋한다. */
    public boolean resetPassword(String challengeId, String rawGrant, String password, String passwordConfirm) {
        MemberPasswordPolicy.validate(password, passwordConfirm);
        if (challengeId == null || challengeId.isBlank() || rawGrant == null || rawGrant.isBlank()) {
            return false;
        }
        String passwordHash = this.passwordEncoder.encode(password);
        Boolean changed = this.transactions.execute(status -> resetPasswordInTransaction(
                challengeId, rawGrant, passwordHash));
        return Boolean.TRUE.equals(changed);
    }

    private IssuedChallenge issueCodeInTransaction(String email, String requestIp, Long targetUserId,
            LocalDateTime now) {
        Long activeUserId = targetUserId;
        if (activeUserId != null) {
            // Insert/update paths holding a challenge never acquire a member lock after the challenge lock.
            Member currentMember = this.accountLockService.lockById(activeUserId);
            if (currentMember == null || currentMember.getDeletedAt() != null
                    || !email.equals(currentMember.getEmail())) {
                activeUserId = null;
            }
        }
        String emailFingerprint = fingerprint("email-bucket", email);
        String ipFingerprint = fingerprint("ip-bucket", requestIp);
        List<PasswordResetRateLimitBucket> buckets = lockRateBuckets(emailFingerprint, ipFingerprint, now);
        LocalDateTime issueAt = now();
        PasswordResetRateLimitBucket emailBucket = buckets.get(0);
        PasswordResetRateLimitBucket ipBucket = buckets.get(1);
        boolean windowsChanged = resetExpiredWindows(buckets, issueAt);

        boolean cooledDown = emailBucket.getLastRequestedAt() != null
                && emailBucket.getLastRequestedAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(issueAt);
        boolean requestLimitReached = emailBucket.getRequestCount() >= MAX_EMAIL_REQUESTS
                || ipBucket.getRequestCount() >= MAX_IP_REQUESTS;
        boolean failureLimitReached = emailBucket.getFailureCount() >= MAX_BUCKET_FAILURES
                || ipBucket.getFailureCount() >= MAX_BUCKET_FAILURES;

        emailBucket.setRequestCount(Math.min(MAX_EMAIL_REQUESTS + 1, emailBucket.getRequestCount() + 1));
        ipBucket.setRequestCount(Math.min(MAX_IP_REQUESTS + 1, ipBucket.getRequestCount() + 1));
        boolean accepted = !cooledDown && !requestLimitReached && !failureLimitReached;
        if (accepted) {
            emailBucket.setLastRequestedAt(issueAt);
            ipBucket.setLastRequestedAt(issueAt);
        }
        if (windowsChanged || accepted || requestLimitReached || failureLimitReached || cooledDown) {
            persistRateBuckets(buckets);
        }
        if (!accepted) {
            int resendWait = rateLimitWait(emailBucket, ipBucket, cooledDown, issueAt);
            return new IssuedChallenge(
                    new PasswordResetRequestResult(null, true, false, 0, resendWait), null, null, null);
        }

        this.passwordResetMapper.invalidateActiveChallengesByEmailFingerprint(emailFingerprint, issueAt);
        String challengeId = randomToken();
        String code = String.format(Locale.ROOT, "%06d", this.secureRandom.nextInt(1_000_000));

        PasswordResetChallenge challenge = new PasswordResetChallenge();
        challenge.setChallengeId(challengeId);
        challenge.setUserId(activeUserId);
        challenge.setEmailFingerprint(emailFingerprint);
        challenge.setRequestIpFingerprint(ipFingerprint);
        challenge.setCodeHmac(codeHmac(challengeId, code));
        challenge.setCreatedAt(issueAt);
        challenge.setExpiresAt(issueAt.plusMinutes(CODE_TTL_MINUTES));
        if (this.passwordResetMapper.insertChallenge(challenge) != 1) {
            throw new IllegalStateException("비밀번호 재설정 인증번호를 저장하지 못했습니다.");
        }

        PasswordResetRequestResult result = new PasswordResetRequestResult(
                challengeId, true, true, CODE_TTL_MINUTES * 60, RESEND_COOLDOWN_SECONDS);
        return new IssuedChallenge(result, activeUserId == null ? null : email, code, challengeId);
    }

    private PasswordResetVerificationResult verifyCodeInTransaction(String challengeId, String email,
            String emailFingerprint, String requestIp, String code) {
        LocalDateTime beforeLock = now();
        String ipFingerprint = fingerprint("ip-bucket", requestIp);
        List<PasswordResetRateLimitBucket> buckets = lockRateBuckets(emailFingerprint, ipFingerprint, beforeLock);
        PasswordResetChallenge challenge = this.passwordResetMapper.lockChallenge(challengeId);
        LocalDateTime now = now();
        boolean windowsChanged = resetExpiredWindows(buckets, now);
        PasswordResetRateLimitBucket emailBucket = buckets.get(0);
        PasswordResetRateLimitBucket ipBucket = buckets.get(1);

        // 서버 재시작 등으로 키가 달라진 기존 인증 요청은 새 인증번호를 받도록 폐기한다.
        if (challenge != null && challenge.getConsumedAt() == null
                && !constantTimeEquals(challenge.getEmailFingerprint(), fingerprint("email-bucket", email))) {
            this.passwordResetMapper.invalidateChallenge(challengeId, now);
            if (windowsChanged) {
                persistRateBuckets(buckets);
            }
            return invalidVerification(0, 0);
        }

        if (emailBucket.getFailureCount() >= MAX_BUCKET_FAILURES
                || ipBucket.getFailureCount() >= MAX_BUCKET_FAILURES) {
            persistRateBuckets(buckets);
            return invalidVerification(codeSeconds(challengeId, now), resendSeconds(challengeId, now));
        }

        if (challenge == null || challenge.getConsumedAt() != null || challenge.getVerifiedAt() != null
                || challenge.getAttemptCount() >= MAX_CHALLENGE_ATTEMPTS
                || !challenge.getExpiresAt().isAfter(now)) {
            if (windowsChanged) {
                persistRateBuckets(buckets);
            }
            return invalidVerification(0, 0);
        }

        boolean codeMatches = code != null && CODE_PATTERN.matcher(code).matches()
                && constantTimeEquals(challenge.getCodeHmac(), codeHmac(challengeId, code));
        if (!codeMatches || challenge.getUserId() == null) {
            this.passwordResetMapper.recordChallengeFailure(challengeId, now);
            emailBucket.setFailureCount(Math.min(MAX_BUCKET_FAILURES, emailBucket.getFailureCount() + 1));
            ipBucket.setFailureCount(Math.min(MAX_BUCKET_FAILURES, ipBucket.getFailureCount() + 1));
            persistRateBuckets(buckets);
            return invalidVerification(remainingSeconds(challenge.getExpiresAt(), now),
                    remainingSeconds(challenge.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS), now));
        }

        String rawGrant = randomToken();
        LocalDateTime grantExpiresAt = now.plusMinutes(GRANT_TTL_MINUTES);
        int changed = this.passwordResetMapper.markChallengeVerified(challengeId, grantHmac(challengeId, rawGrant),
                now, grantExpiresAt);
        persistRateBuckets(buckets);
        if (changed != 1) {
            return invalidVerification(remainingSeconds(challenge.getExpiresAt(), now),
                    remainingSeconds(challenge.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS), now));
        }
        return new PasswordResetVerificationResult(true, rawGrant,
                remainingSeconds(grantExpiresAt, now),
                remainingSeconds(challenge.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS), now));
    }

    private boolean resetPasswordInTransaction(String challengeId, String rawGrant, String passwordHash) {
        PasswordResetChallenge initial = this.passwordResetMapper.findChallenge(challengeId);
        if (initial == null || initial.getUserId() == null) {
            return false;
        }

        // Match the account-first lock order used by the rest of the member domain.
        Member member = this.accountLockService.lockById(initial.getUserId());
        if (member == null || member.getDeletedAt() != null) {
            return false;
        }
        PasswordResetChallenge challenge = this.passwordResetMapper.lockChallenge(challengeId);
        LocalDateTime now = now();
        if (challenge == null || !initial.getUserId().equals(challenge.getUserId())
                || challenge.getConsumedAt() != null || challenge.getVerifiedAt() == null
                || challenge.getGrantExpiresAt() == null || !challenge.getGrantExpiresAt().isAfter(now)
                || !constantTimeEquals(challenge.getGrantHmac(), grantHmac(challengeId, rawGrant))) {
            return false;
        }

        if (this.memberMapper.updatePasswordHash(member.getUserId(), passwordHash) != 1) {
            return false;
        }
        if (this.passwordResetMapper.consumeChallenge(challengeId, challenge.getGrantHmac(), now) != 1) {
            throw new IllegalStateException("비밀번호 재설정 권한을 소비하지 못했습니다.");
        }
        // Do not alter penalty_time: password recovery must not lift an account restriction.
        this.sessionRevocationService.revokeAfterCommit(member.getUserId());
        return true;
    }

    private List<PasswordResetRateLimitBucket> lockRateBuckets(String emailFingerprint,
            String ipFingerprint, LocalDateTime now) {
        this.passwordResetMapper.ensureRateLimitBucket("EMAIL", emailFingerprint, now);
        this.passwordResetMapper.ensureRateLimitBucket("IP", ipFingerprint, now);
        PasswordResetRateLimitBucket emailBucket = this.passwordResetMapper
                .lockRateLimitBucket("EMAIL", emailFingerprint);
        PasswordResetRateLimitBucket ipBucket = this.passwordResetMapper
                .lockRateLimitBucket("IP", ipFingerprint);
        if (emailBucket == null || ipBucket == null) {
            throw new IllegalStateException("비밀번호 재설정 제한 버킷을 불러오지 못했습니다.");
        }
        List<PasswordResetRateLimitBucket> buckets = new ArrayList<>(2);
        buckets.add(emailBucket);
        buckets.add(ipBucket);
        return buckets;
    }

    private boolean resetExpiredWindows(List<PasswordResetRateLimitBucket> buckets, LocalDateTime now) {
        boolean changed = false;
        for (PasswordResetRateLimitBucket bucket : buckets) {
            LocalDateTime windowEnd = bucket.getWindowStartedAt().plus(RATE_WINDOW);
            if (!windowEnd.isAfter(now)) {
                bucket.setWindowStartedAt(now);
                bucket.setRequestCount(0);
                bucket.setFailureCount(0);
                bucket.setLastRequestedAt(null);
                changed = true;
            }
        }
        return changed;
    }

    private void persistRateBuckets(List<PasswordResetRateLimitBucket> buckets) {
        for (PasswordResetRateLimitBucket bucket : buckets) {
            if (this.passwordResetMapper.updateRateLimitBucket(bucket) != 1) {
                throw new IllegalStateException("비밀번호 재설정 제한을 저장하지 못했습니다.");
            }
        }
    }

    private static int rateLimitWait(PasswordResetRateLimitBucket emailBucket,
            PasswordResetRateLimitBucket ipBucket, boolean cooledDown, LocalDateTime now) {
        int wait = 0;
        if (cooledDown && emailBucket.getLastRequestedAt() != null) {
            wait = Math.max(wait, remainingSeconds(
                    emailBucket.getLastRequestedAt().plusSeconds(RESEND_COOLDOWN_SECONDS), now));
        }
        if (emailBucket.getRequestCount() >= MAX_EMAIL_REQUESTS
                || emailBucket.getFailureCount() >= MAX_BUCKET_FAILURES) {
            wait = Math.max(wait, remainingSeconds(emailBucket.getWindowStartedAt().plus(RATE_WINDOW), now));
        }
        if (ipBucket.getRequestCount() >= MAX_IP_REQUESTS
                || ipBucket.getFailureCount() >= MAX_BUCKET_FAILURES) {
            wait = Math.max(wait, remainingSeconds(ipBucket.getWindowStartedAt().plus(RATE_WINDOW), now));
        }
        return wait;
    }

    private int codeSeconds(String challengeId, LocalDateTime now) {
        PasswordResetChallenge challenge = this.passwordResetMapper.findChallenge(challengeId);
        return challenge == null || challenge.getExpiresAt() == null ? 0
                : remainingSeconds(challenge.getExpiresAt(), now);
    }

    private int resendSeconds(String challengeId, LocalDateTime now) {
        PasswordResetChallenge challenge = this.passwordResetMapper.findChallenge(challengeId);
        return challenge == null || challenge.getCreatedAt() == null ? 0
                : remainingSeconds(challenge.getCreatedAt().plusSeconds(RESEND_COOLDOWN_SECONDS), now);
    }

    private static PasswordResetVerificationResult invalidVerification(int expiresInSeconds,
            int resendInSeconds) {
        return new PasswordResetVerificationResult(false, null, expiresInSeconds, resendInSeconds);
    }

    private void dispatchEmail(String challengeId, String email, String code) {
        try {
            this.emailExecutor.execute(() -> {
                try {
                    this.emailSender.sendCode(email, code, CODE_TTL_MINUTES);
                } catch (PasswordResetEmailException exception) {
                    this.passwordResetMapper.invalidateChallenge(challengeId, now());
                    // Log only bounded safe diagnostics: no recipient, code, body, or credentials.
                    log.error("Password reset Gmail API delivery failed; challenge invalidated"
                                    + " (code={}, status={}, reason={}, cause={}).",
                            exception.getCode(), exception.getHttpStatus(), exception.getProviderReason(),
                            exception.getCauseType());
                } catch (RuntimeException exception) {
                    this.passwordResetMapper.invalidateChallenge(challengeId, now());
                    // Unexpected failures: fixed category and class only, never message or stacktrace.
                    log.error("Password reset Gmail API delivery failed unexpectedly; challenge invalidated"
                                    + " (cause={}).", exception.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException exception) {
            this.passwordResetMapper.invalidateChallenge(challengeId, now());
            log.error("Password reset Gmail API queue is full; challenge invalidated.");
        }
    }

    @PreDestroy
    void shutdownEmailExecutor() {
        this.emailExecutor.shutdown();
        try {
            if (!this.emailExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                this.emailExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.emailExecutor.shutdownNow();
        }
    }

    private String codeHmac(String challengeId, String code) {
        return fingerprint("code", challengeId + ":" + code);
    }

    private String grantHmac(String challengeId, String rawGrant) {
        return fingerprint("grant", challengeId + ":" + rawGrant);
    }

    private String fingerprint(String purpose, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(this.codeSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal((purpose + ':' + value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("비밀번호 재설정 HMAC을 계산하지 못했습니다.");
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        this.secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static String normalizeAndValidateEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > EMAIL_MAX_LENGTH
                || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("올바른 이메일 형식으로 입력해 주세요.");
        }
        return normalized;
    }

    private static String normalizeIp(String requestIp) {
        return requestIp == null || requestIp.isBlank() ? "unknown" : requestIp.trim();
    }

    private static int remainingSeconds(LocalDateTime until, LocalDateTime now) {
        long remainingMillis = Duration.between(now, until).toMillis();
        if (remainingMillis <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, (remainingMillis + 999) / 1000);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    private static ThreadFactory mailThreadFactory() {
        AtomicInteger threadNumber = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "password-reset-gmail-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record IssuedChallenge(PasswordResetRequestResult result, String email, String code,
            String challengeId) {
        @Override
        public String toString() {
            return "IssuedChallenge{accepted=" + result.accepted() + ", challengePresent="
                    + (challengeId != null) + ", emailPresent=" + (email != null) + ", codePresent="
                    + (code != null) + '}';
        }
    }
}
