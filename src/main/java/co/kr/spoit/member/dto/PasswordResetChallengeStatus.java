package co.kr.spoit.member.dto;

/** 세션의 도전이 아직 이어질 수 있는지 화면에 필요한 최소한의 상태만 담는다. */
public record PasswordResetChallengeStatus(
        boolean active,
        boolean verified,
        int expiresInSeconds,
        int resendInSeconds) {
}
