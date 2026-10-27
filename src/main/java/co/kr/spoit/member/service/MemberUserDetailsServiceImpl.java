package co.kr.spoit.member.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import lombok.RequiredArgsConstructor;

/**
 * {@link MemberUserDetailsService} 구현. {@code app_user} 조회 결과를
 * 인증 주체로 변환하는 어댑터 역할만 한다.
 */
@Service
@RequiredArgsConstructor
public class MemberUserDetailsServiceImpl implements MemberUserDetailsService {

    private final MemberService memberService;

    /**
     * 회원이 없거나 비밀번호 해시가 비면 {@link UsernameNotFoundException}을 던진다.
     * 탈퇴 회원은 remember-me 자동 로그인에서도 인증되지 않도록 여기서 거부한다.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member member = memberService.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("회원을 찾을 수 없습니다."));
        if (member.getDeletedAt() != null) {
            throw new UsernameNotFoundException("탈퇴한 회원입니다.");
        }
        if (member.getPasswordHash() == null || member.getPasswordHash().isBlank()) {
            throw new UsernameNotFoundException("비밀번호가 설정되지 않은 계정입니다.");
        }
        return MemberPrincipal.from(member);
    }
}
