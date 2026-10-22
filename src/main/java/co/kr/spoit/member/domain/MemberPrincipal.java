package co.kr.spoit.member.domain;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 로그인한 회원의 인증 주체(principal)이다. 세션에는 엔티티나 비밀번호 해시
 * 대신 이 객체를 저장하고, 화면 값은 프로퍼티로 직접 노출한다. 해시는
 * 인증 비교에만 필요하므로 생성 직후 {@link #eraseCredentials()}로 지운다.
 */
public class MemberPrincipal implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    /** 로그인을 허용하는 계정 상태 코드 목록이다. */
    private static final List<String> ALLOWED_STATUS_CODES = List.of(
            "활성", "ACTIVE", "NORMAL");

    private final Long userId;
    private final String email;
    private final String nickname;
    private final String phone;
    private final MemberRole role;
    private final boolean enabled;

    /** 인증에만 사용하고 곧 지우는 비밀번호 해시이다. */
    private String passwordHash;

    private MemberPrincipal(Long userId, String email, String nickname, String phone,
            MemberRole role, boolean enabled, String passwordHash) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.phone = phone;
        this.role = role;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    public static MemberPrincipal from(Member member) {
        return new MemberPrincipal(
                member.getUserId(),
                member.getEmail(),
                member.getNickname(),
                member.getPhone(),
                MemberRole.from(member.getRole()),
                isUsable(member.getStatus()),
                member.getPasswordHash());
    }

    /**
     * 계정 상태가 로그인 가능한지 판정한다. 상태 코드가 확정되지 않았으므로
     * 허용 목록과 정확히 일치할 때만 통과시키고, 값이 없으면 거부한다(fail-closed).
     */
    private static boolean isUsable(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String value = status.strip().toUpperCase(java.util.Locale.ROOT);
        return ALLOWED_STATUS_CODES.contains(value);
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPhone() {
        return phone;
    }

    public String getRoleLabel() {
        return role.label();
    }

    /** 아바타에 표시할 첫 글자를 만들고, 닉네임이 없으면 {@code "?"}를 반환한다. */
    public String getInitial() {
        if (nickname == null || nickname.isBlank()) {
            return "?";
        }
        return nickname.strip().substring(0, 1);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 인증이 끝난 뒤 비밀번호 해시를 제거한다. {@code ProviderManager}가
     * 자동으로 호출하며, 재호출해도 안전하다.
     */
    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
