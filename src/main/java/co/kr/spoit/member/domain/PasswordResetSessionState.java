package co.kr.spoit.member.domain;

import java.io.Serial;
import java.io.Serializable;

/** 서버 세션에만 보관하는 현재 도전 ID·이메일·1회용 재설정 권한. 회원 ID는 저장하지 않는다. */
public record PasswordResetSessionState(String challengeId, String rawGrant, String email) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return "PasswordResetSessionState{challengeIdPresent=" + (challengeId != null)
                + ", emailPresent=" + (email != null) + ", grantPresent=" + (rawGrant != null) + '}';
    }
}
