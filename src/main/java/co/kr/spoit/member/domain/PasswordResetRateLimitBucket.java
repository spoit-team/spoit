package co.kr.spoit.member.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** HMAC 지문으로 식별되는 이메일/IP별 발급·실패 누적 제한 행. */
@Getter
@Setter
public class PasswordResetRateLimitBucket {

    private String bucketType;
    private String bucketFingerprint;
    private LocalDateTime windowStartedAt;
    private int requestCount;
    private int failureCount;
    private LocalDateTime lastRequestedAt;
}
