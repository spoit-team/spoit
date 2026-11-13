package co.kr.spoit.member.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 비밀번호 재설정 도전의 DB 행이다. 이메일·인증번호·재설정 권한 원문은 보관하지 않는다. */
@Getter
@Setter
public class PasswordResetChallenge {

    private String challengeId;
    private Long userId;
    private String emailFingerprint;
    private String requestIpFingerprint;
    private String codeHmac;
    private String grantHmac;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime grantExpiresAt;
    private LocalDateTime consumedAt;
    private int attemptCount;
}
