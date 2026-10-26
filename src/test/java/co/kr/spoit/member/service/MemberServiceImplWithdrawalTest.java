package co.kr.spoit.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;

/** 탈퇴가 비밀번호 재확인 후 상태 변경 1회로 끝나는지 검증한다. 삭제 대신 논리 삭제(status)를 쓴다. */
class MemberServiceImplWithdrawalTest {

    private static final Long USER_ID = 7L;
    private static final String RAW_PASSWORD = "spoit1234!";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MemberMapper memberMapper;
    private MemberServiceImpl memberService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        memberService = new MemberServiceImpl(memberMapper, passwordEncoder);
    }

    @Test
    @DisplayName("비밀번호가 일치하면 상태를 탈퇴로 바꾼다")
    void marksStatusWithdrawn() {
        when(memberMapper.findById(USER_ID)).thenReturn(Optional.of(member()));
        when(memberMapper.updateStatus(eq(USER_ID), eq("탈퇴"), any())).thenReturn(1);

        memberService.withdraw(USER_ID, RAW_PASSWORD);

        verify(memberMapper).updateStatus(USER_ID, "탈퇴", List.of("활성", "ACTIVE", "NORMAL"));
    }

    @Test
    @DisplayName("비밀번호가 다르면 상태를 바꾸지 않는다")
    void rejectsWrongPassword() {
        when(memberMapper.findById(USER_ID)).thenReturn(Optional.of(member()));

        assertThatThrownBy(() -> memberService.withdraw(USER_ID, "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비밀번호");

        verify(memberMapper, never()).updateStatus(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("비밀번호가 비어 있으면 조회조차 하지 않는다")
    void rejectsBlankPassword() {
        assertThatThrownBy(() -> memberService.withdraw(USER_ID, "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(memberMapper);
    }

    @Test
    @DisplayName("회원이 없으면 예외를 던진다")
    void rejectsMissingMember() {
        when(memberMapper.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.withdraw(USER_ID, RAW_PASSWORD))
                .isInstanceOf(IllegalStateException.class);

        verify(memberMapper, never()).updateStatus(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("이미 탈퇴했거나 정지된 계정은 상태를 바꾸지 않는다")
    void rejectsNonWithdrawableStatus() {
        when(memberMapper.findById(USER_ID)).thenReturn(Optional.of(member()));
        when(memberMapper.updateStatus(eq(USER_ID), eq("탈퇴"), any())).thenReturn(0);

        assertThatThrownBy(() -> memberService.withdraw(USER_ID, RAW_PASSWORD))
                .isInstanceOf(IllegalStateException.class);
    }

    private Member member() {
        Member member = new Member();
        member.setUserId(USER_ID);
        member.setEmail("player@example.com");
        member.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        member.setNickname("플레이어");
        member.setPhone("010-1234-5678");
        member.setRole("일반 회원");
        member.setStatus("활성");
        return member;
    }
}
