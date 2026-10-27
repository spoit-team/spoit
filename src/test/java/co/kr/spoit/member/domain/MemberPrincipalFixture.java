package co.kr.spoit.member.domain;

/**
 * 인증·권한·화면 렌더링 테스트에서 로그인 주체를 만드는 공통 픽스처다.
 *
 * <p>역할 문자열은 화면 노출 조건({@code roleLabel})과 같은 값을 사용하므로
 * 테스트마다 다른 값을 만들지 않도록 한 곳에서 관리한다.</p>
 */
public final class MemberPrincipalFixture {

    private MemberPrincipalFixture() {
    }

    public static MemberPrincipal member() {
        return principal("일반 회원");
    }

    public static MemberPrincipal facilityManager() {
        return principal("시설 관리자");
    }

    public static MemberPrincipal admin() {
        return principal("시스템 관리자");
    }

    private static MemberPrincipal principal(String role) {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("user@example.com");
        member.setNickname("회원");
        member.setPhone("010-1234-5678");
        member.setRole(role);
        return MemberPrincipal.from(member);
    }
}
