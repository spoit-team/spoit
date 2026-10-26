package co.kr.spoit.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 인증 주체의 화면 값과 계정 상태 판정만 확인한다. */
class MemberPrincipalTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 12, 0);

    static Stream<Arguments> penaltyCases() {
        return Stream.of(
                Arguments.of(null, true),
                Arguments.of(NOW.minusSeconds(1), true),
                Arguments.of(NOW, true),
                Arguments.of(NOW.plusSeconds(1), false),
                Arguments.of(NOW.plusDays(1), false));
    }

    @ParameterizedTest
    @MethodSource("penaltyCases")
    @DisplayName("이용정지 종료 시각이 미래일 때만 정지로 판정한다")
    void judgesPenaltyTime(LocalDateTime penaltyTime, boolean enabled) {
        Member member = member("플레이어");
        member.setPenaltyTime(penaltyTime);

        assertThat(MemberPrincipal.from(member, NOW).isEnabled()).isEqualTo(enabled);
    }

    @Test
    @DisplayName("탈퇴 시각이 있으면 정지가 만료됐어도 로그인할 수 없다")
    void rejectsWithdrawnMemberEvenWhenPenaltyExpired() {
        Member withdrawn = member("플레이어");
        withdrawn.setDeletedAt(NOW.minusHours(1));
        withdrawn.setPenaltyTime(NOW.minusDays(1));

        assertThat(MemberPrincipal.from(withdrawn, NOW).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("화면 값과 권한을 회원 정보에서 채운다")
    void exposesViewProperties() {
        MemberPrincipal principal = MemberPrincipal.from(member("플레이어"), NOW);

        assertThat(principal.getNickname()).isEqualTo("플레이어");
        assertThat(principal.getInitial()).isEqualTo("플");
        assertThat(principal.getRoleLabel()).isEqualTo("일반 회원");
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_MEMBER");

        assertThat(MemberPrincipal.from(member(" "), NOW).getInitial()).isEqualTo("?");
    }

    @Test
    @DisplayName("자격 증명 삭제 후 비밀번호 해시는 남지 않는다")
    void eraseCredentialsClearsPassword() {
        MemberPrincipal principal = MemberPrincipal.from(member("플레이어"), NOW);
        assertThat(principal.getPassword()).isNotBlank();

        principal.eraseCredentials();

        assertThat(principal.getPassword()).isNull();
    }

    private static Member member(String nickname) {
        Member member = new Member();
        member.setUserId(1L);
        member.setEmail("player@example.com");
        member.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        member.setNickname(nickname);
        member.setPhone("010-1234-5678");
        member.setRole("일반 회원");
        return member;
    }
}
