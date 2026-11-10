package co.kr.spoit.member.domain;

import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /** 서버가 UTC여도 한국 시간 기준으로 정지 종료 시각을 비교한다. */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

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
        return from(member, LocalDateTime.now(ZONE));
    }

    /** 테스트에서 기준 시각을 고정할 수 있도록 분리한 변환 메서드다. */
    static MemberPrincipal from(Member member, LocalDateTime now) {
        return new MemberPrincipal(
                member.getUserId(),
                member.getEmail(),
                member.getNickname(),
                member.getPhone(),
                MemberRole.from(member.getRole()),
                isUsable(member.getDeletedAt(), member.getPenaltyTime(), now),
                member.getPasswordHash());
    }

    /**
     * 계정이 로그인 가능한지 판정한다. 탈퇴 시각이 있으면 다른 값과 관계없이 거부하고,
     * 이용정지 종료 시각이 아직 지나지 않았으면 정지 상태로 본다.
     */
    static boolean isUsable(LocalDateTime deletedAt, LocalDateTime penaltyTime, LocalDateTime now) {
        if (deletedAt != null) {
            return false;
        }
        return penaltyTime == null || !penaltyTime.isAfter(now);
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

    /**
     * 회원 번호가 같으면 같은 주체로 본다.
     *
     * <p>세션 추적({@code SessionRegistry})이 주체를 키로 세션을 묶으므로, 역할 변경·닉네임 수정 등
     * 다른 필드가 달라도 같은 회원의 세션이 하나로 모여야 한다. 로그인 시점과 이후 시점의 스냅샷이
     * 서로 다른 인스턴스여도 같은 회원으로 식별되게 하려는 의도다.</p>
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MemberPrincipal principal)) {
            return false;
        }
        return userId != null && userId.equals(principal.userId);
    }

    /** {@link #equals(Object)}와 같은 기준(회원 번호)을 쓴다. */
    @Override
    public int hashCode() {
        return userId == null ? 0 : userId.hashCode();
    }
}
