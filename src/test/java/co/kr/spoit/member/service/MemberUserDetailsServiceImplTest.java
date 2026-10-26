package co.kr.spoit.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import co.kr.spoit.member.domain.Member;

/** 탈퇴 회원이 로그인·remember-me 사용자 조회 단계에서 거부되는지 검증한다. */
class MemberUserDetailsServiceImplTest {

    private static final String EMAIL = "player@example.com";

    private MemberService memberService;
    private MemberUserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        userDetailsService = new MemberUserDetailsServiceImpl(memberService);
    }

    @Test
    @DisplayName("탈퇴 시각이 있는 회원은 상태가 활성이어도 사용자 조회에서 거부한다")
    void rejectsWithdrawnMember() {
        Member member = member();
        member.setStatus("활성");
        member.setDeletedAt(LocalDateTime.of(2026, 10, 26, 12, 0));
        given(memberService.findByEmail(EMAIL)).willReturn(Optional.of(member));

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(EMAIL))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("미탈퇴 회원은 인증 주체로 변환한다")
    void loadsActiveMember() {
        given(memberService.findByEmail(EMAIL)).willReturn(Optional.of(member()));

        assertThat(userDetailsService.loadUserByUsername(EMAIL).isEnabled()).isTrue();
    }

    private static Member member() {
        Member member = new Member();
        member.setUserId(1L);
        member.setEmail(EMAIL);
        member.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        member.setNickname("플레이어");
        member.setPhone("010-1234-5678");
        member.setRole("일반 회원");
        member.setStatus("활성");
        return member;
    }
}
