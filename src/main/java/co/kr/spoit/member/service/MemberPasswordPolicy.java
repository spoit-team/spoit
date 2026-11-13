package co.kr.spoit.member.service;

import java.nio.charset.StandardCharsets;

/** 회원가입과 비밀번호 재설정에 공통으로 적용하는 비밀번호 정책. */
final class MemberPasswordPolicy {

    private static final int MAX_BCRYPT_BYTES = 72;

    private MemberPasswordPolicy() {
    }

    static void validate(String password, String confirmation) {
        if (password == null || password.length() < 8 || !hasAsciiLetterAndDigit(password)) {
            throw new IllegalArgumentException("비밀번호는 영문과 숫자를 포함해 8자 이상 입력해 주세요.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_BYTES) {
            throw new IllegalArgumentException("비밀번호는 72바이트 이하로 입력해 주세요.");
        }
        if (!password.equals(confirmation)) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
    }

    private static boolean hasAsciiLetterAndDigit(String value) {
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if ((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z')) {
                letter = true;
            } else if (character >= '0' && character <= '9') {
                digit = true;
            }
        }
        return letter && digit;
    }
}
