package co.kr.spoit.member.dto;

/** 인증 결과. grant 원문은 검증 성공 시 서버 컨트롤러가 세션에만 저장한다. */
public record PasswordResetVerificationResult(
        boolean verified,
        String rawGrant,
        int expiresInSeconds,
        int resendInSeconds) {

    @Override
    public String toString() {
        return "PasswordResetVerificationResult{verified=" + verified
                + ", grantPresent=" + (rawGrant != null) + '}';
    }
}
