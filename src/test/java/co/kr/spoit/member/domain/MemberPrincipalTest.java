package co.kr.spoit.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 인증 주체의 화면 값과 계정 상태 판정만 확인한다. */
class MemberPrincipalTest {

    static Stream<Arguments> statusCases() {
        return Stream.of(
                Arguments.of("활성", true),
                Arguments.of("ACTIVE", true),
                Arguments.of(" NORMAL ", true),
                Arguments.of("정지", false),
                Arguments.of("검토중", false),
                Arguments.of("비활성", false),
                Arguments.of(null, false),
                Arguments.of("   ", false));
    }

    @ParameterizedTest
    @MethodSource("statusCases")
    @DisplayName("허용 목록의 상태만 활성으로 판정한다")
    void judgesAccountStatus(String status, boolean enabled) {
        assertThat(MemberPrincipal.from(member(status)).isEnabled()).isEqualTo(enabled);
    }

    @Test
    @DisplayName("화면 값과 권한을 회원 정보에서 채운다")
    void exposesViewProperties() {
        MemberPrincipal principal = MemberPrincipal.from(member("활성"));

        assertThat(principal.getNickname()).isEqualTo("플레이어");
        assertThat(principal.getInitial()).isEqualTo("플");
        assertThat(principal.getRoleLabel()).isEqualTo("일반 회원");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_MEMBER");

        assertThat(MemberPrincipal.from(member("활성", " ")).getInitial()).isEqualTo("?");
    }

    @Test
    @DisplayName("자격 증명 삭제 후 비밀번호 해시는 남지 않는다")
    void eraseCredentialsClearsPassword() {
        MemberPrincipal principal = MemberPrincipal.from(member("활성"));
        assertThat(principal.getPassword()).isNotBlank();

        principal.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
    }

    private static Member member(String status) {
        return member(status, "플레이어");
    }

    private static Member member(String status, String nickname) {
        Member member = new Member();
        member.setUserId(1L);
        member.setEmail("player@example.com");
        member.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        member.setNickname(nickname);
        member.setPhone("010-1234-5678");
        member.setRole("일반 회원");
        member.setStatus(status);
        return member;
    }
}
