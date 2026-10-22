package co.kr.spoit.member.dto;

import co.kr.spoit.member.domain.MemberPrincipal;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 화면에서 사용하는 로그인 회원 정보. 인증 내부 정보는 포함하지 않는다. */
@Getter
@RequiredArgsConstructor
public final class CurrentMemberDto {

    private final Long userId;
    private final String email;
    private final String nickname;
    private final String phone;
    private final String initial;
    private final String roleLabel;

    /** 인증 주체에서 화면에 필요한 값만 복사한다. */
    public static CurrentMemberDto from(MemberPrincipal principal) {
        return new CurrentMemberDto(
                principal.getUserId(),
                principal.getEmail(),
                principal.getNickname(),
                principal.getPhone(),
                principal.getInitial(),
                principal.getRoleLabel());
    }
}
