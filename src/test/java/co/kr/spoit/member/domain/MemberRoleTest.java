package co.kr.spoit.member.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** 역할 문자열 해석 규칙만 확인한다. */
class MemberRoleTest {

    static Stream<Arguments> roleCases() {
        return Stream.of(
                Arguments.of("일반 회원", MemberRole.MEMBER),
                Arguments.of("시설 관리자", MemberRole.FACILITY_MANAGER),
                Arguments.of("시스템 관리자", MemberRole.ADMIN),
                Arguments.of("ROLE_FACILITY_MANAGER", MemberRole.FACILITY_MANAGER),
                Arguments.of("ROLE_ADMIN", MemberRole.ADMIN),
                Arguments.of("무언가", MemberRole.MEMBER),
                Arguments.of(null, MemberRole.MEMBER),
                Arguments.of("NOT_ADMIN", MemberRole.MEMBER),
                Arguments.of("xADMIN", MemberRole.MEMBER),
                Arguments.of("FACILITY_ADMIN", MemberRole.MEMBER),
                Arguments.of("SUPER_USER", MemberRole.MEMBER),
                Arguments.of("ADMIN", MemberRole.ADMIN),
                Arguments.of("admin", MemberRole.ADMIN));
    }

    @ParameterizedTest
    @MethodSource("roleCases")
    @DisplayName("한글·영문 역할을 해석하고 알 수 없는 값은 최소 권한으로 처리한다")
    void resolvesRole(String raw, MemberRole expected) {
        assertThat(MemberRole.from(raw)).isEqualTo(expected);
    }
}
