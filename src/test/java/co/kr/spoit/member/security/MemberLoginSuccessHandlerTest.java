package co.kr.spoit.member.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 로그인 성공 후 이동 경로 검증 규칙을 확인한다.
 */
class MemberLoginSuccessHandlerTest {

    static Stream<Arguments> targetCases() {
        return Stream.of(
                Arguments.of(null, "/matches"),
                Arguments.of("  ", "/matches"),
                Arguments.of("/mypage", "/mypage"),
                Arguments.of("/matches/7?tab=member", "/matches/7?tab=member"),
                Arguments.of("//evil.example/login", "/matches"),
                Arguments.of("https://evil.example", "/matches"),
                Arguments.of("/\\evil.example", "/matches"),
                Arguments.of("mypage", "/matches"),
                Arguments.of("/login", "/matches"),
                Arguments.of("/logout", "/matches"),
                Arguments.of("/signup", "/matches"),
                Arguments.of("/login?redirect=/mypage", "/matches"));
    }

    @ParameterizedTest
    @MethodSource("targetCases")
    @DisplayName("사내 상대 경로만 유지하고 외부 URL과 인증 화면 자기 참조는 기본 화면으로 대체한다")
    void resolvesOnlySafeTarget(String requested, String expected) {
        assertThat(MemberLoginSuccessHandler.resolveTarget(requested)).isEqualTo(expected);
    }
}
