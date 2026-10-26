package co.kr.spoit.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;

/** 탈퇴가 비밀번호 재확인 후 deleted_at 기록 1회로 끝나는지 검증한다. */
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
    @DisplayName("비밀번호가 일치하면 탈퇴 시각을 기록한다")
    void recordsDeletedAt() {
        when(memberMapper.findById(USER_ID)).thenReturn(Optional.of(member()));
        when(memberMapper.recordWithdrawal(eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);

        memberService.withdraw(USER_ID, RAW_PASSWORD);

        ArgumentCaptor<LocalDateTime> deletedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(memberMapper).recordWithdrawal(eq(USER_ID), deletedAt.capture(), now.capture());
        assertThat(deletedAt.getValue()).isNotNull();
        assertThat(now.getValue()).isEqualTo(deletedAt.getValue());
    }

    @Test
    @DisplayName("비밀번호가 다르면 탈퇴 시각을 기록하지 않는다")
    void rejectsWrongPassword() {
        when(memberMapper.findById(USER_ID)).thenReturn(Optional.of(member()));

        assertThatThrownBy(() -> memberService.withdraw(USER_ID, "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비밀번호");

        verify(memberMapper, never()).recordWithdrawal(anyLong(), any(), any());
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

        verify(memberMapper, never()).recordWithdrawal(anyLong(), any(), any());
    }

    @Test
    @DisplayName("이미 탈퇴했거나 정지된 계정은 시각을 기록하지 않는다")
    void rejectsNonWithdrawableMember() {
        when(memberMapper.findById(USER_ID)).thenReturn(Optional.of(member()));
        when(memberMapper.recordWithdrawal(eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);

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
        return member;
    }
}
