package co.kr.spoit.member.domain;

import java.util.Locale;

/**
 * {@code app_user.role} 값을 스프링 시큐리티 권한과 화면 라벨로 변환한다.
 * 스키마가 코드값을 확정하지 않아 한글 표기와 영문 코드를 모두 수용하고,
 * 규약이 정해지면 이 클래스만 좁히면 된다.
 *
 * <p>정확 일치만 인정하고 부분 문자열은 해석하지 않는다. 부분 매칭을 허용하면
 * {@code NOT_ADMIN} 같은 값이 관리자로 승격되는 권한 상승 경로가 생긴다.</p>
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
     * DB의 역할 문자열을 해석한다. 앞뒤 공백과 {@code ROLE_} 접두사를 제거한 뒤
     * 정확히 일치하는 값만 인정하며, 알 수 없는 값은 가장 제한적인 {@link #MEMBER}로 처리한다.
     */
    public static MemberRole from(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEMBER;
        }
        String value = raw.trim();
        if (value.regionMatches(true, 0, "ROLE_", 0, "ROLE_".length())) {
            value = value.substring("ROLE_".length()).trim();
        }
        if ("일반 회원".equals(value)) {
            return MEMBER;
        }
        if ("시설 관리자".equals(value)) {
            return FACILITY_MANAGER;
        }
        if ("시스템 관리자".equals(value)) {
            return ADMIN;
        }
        String code = value.toUpperCase(Locale.ROOT);
        return switch (code) {
            case "MEMBER", "NORMAL" -> MEMBER;
            case "FACILITY_MANAGER" -> FACILITY_MANAGER;
            case "ADMIN" -> ADMIN;
            default -> MEMBER;
        };
    }
}
