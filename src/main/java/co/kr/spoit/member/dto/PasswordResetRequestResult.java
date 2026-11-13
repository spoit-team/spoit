package co.kr.spoit.member.dto;

/** 코드 발급 요청의 화면 상태. challenge ID는 서버 세션에만 저장되며 화면에 전달하지 않는다. */
public record PasswordResetRequestResult(
        String challengeId,
        boolean available,
        boolean accepted,
        int expiresInSeconds,
        int resendInSeconds) {
}
