package co.kr.spoit.member.domain;

import java.util.Locale;

/**
 * {@code app_user.role} 값을 스프링 시큐리티 권한과 화면 라벨로 변환한다.
 * 스키마가 코드값을 확정하지 않아 한글 표기와 영문 코드를 모두 수용하고,
 * 규약이 정해지면 이 클래스만 좁히면 된다.
 */
public enum MemberRole {

    /** 일반 회원 권한이다. */
    MEMBER("ROLE_MEMBER", "일반 회원"),

    /** 시설 관리자 권한이다. */
    FACILITY_MANAGER("ROLE_FACILITY_MANAGER", "시설 관리자"),

    /** 시스템 관리자 권한이다. */
    ADMIN("ROLE_ADMIN", "시스템 관리자");

    private final String authority;
    private final String label;

    MemberRole(String authority, String label) {
        this.authority = authority;
        this.label = label;
    }

    public String authority() {
        return authority;
    }

    public String label() {
        return label;
    }

    /**
     * DB의 역할 문자열을 해석한다. 한글 키워드를 먼저 보고 영문 코드를
     * 확인하며, 알 수 없는 값은 가장 제한적인 {@link #MEMBER}로 처리한다.
     */
    public static MemberRole from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEMBER;
        }
        String value = raw.trim();
        if (value.contains("시스템")) {
            return ADMIN;
        }
        if (value.contains("시설")) {
            return FACILITY_MANAGER;
        }
        String code = value.toUpperCase(Locale.ROOT);
        if (code.startsWith("ROLE_")) {
            code = code.substring("ROLE_".length());
        }
        if (code.contains("ADMIN")) {
            return ADMIN;
        }
        if (code.contains("FACILITY")) {
            return FACILITY_MANAGER;
        }
        return MEMBER;
    }
}
