package co.kr.spoit.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.mapper.PasswordResetMapper;
import co.kr.spoit.member.security.MemberSessionRevocationService;

class MemberPasswordChangeServiceTest {
    private final MemberAccountLockService accountLock = mock(MemberAccountLockService.class);
    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final PasswordResetMapper resetMapper = mock(PasswordResetMapper.class);
    private final MemberSessionRevocationService revocation = mock(MemberSessionRevocationService.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final MemberPasswordChangeService service = new MemberPasswordChangeService(
            accountLock, memberMapper, resetMapper, encoder, revocation);
    private Member member;

    @BeforeEach
    void setUp() {
        member = new Member();
        member.setUserId(7L);
        member.setPasswordHash(encoder.encode("Current123"));
        when(accountLock.lockById(7L)).thenReturn(member);
    }

    @Test
    void updatesBcryptHashInvalidatesResetAndRevokesSessions() {
        when(memberMapper.updatePasswordHash(eq(7L), any())).thenReturn(1);
        service.changePassword(7L, "Current123", "NewPassword456", "NewPassword456");

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(accountLock, memberMapper, resetMapper, revocation);
        order.verify(accountLock).lockById(7L);
        order.verify(memberMapper).updatePasswordHash(eq(7L), hash.capture());
        order.verify(resetMapper).invalidateActiveChallengesByUserId(eq(7L), any());
        order.verify(revocation).revokeAfterCommit(7L);
        assertThat(encoder.matches("NewPassword456", hash.getValue())).isTrue();
        assertThat(hash.getValue()).isNotEqualTo("NewPassword456");
    }

    @Test
    void rejectsWrongCurrentPasswordAndInvalidNewPasswords() {
        assertThatThrownBy(() -> service.changePassword(7L, "wrong", "NewPassword456", "NewPassword456"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("현재 비밀번호");
        assertThatThrownBy(() -> service.changePassword(7L, "Current123", "weak", "weak"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.changePassword(7L, "Current123", "NewPassword456", "different"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("일치");
        assertThatThrownBy(() -> service.changePassword(7L, "Current123", "abc12345" + "한".repeat(23),
                "abc12345" + "한".repeat(23))).isInstanceOf(IllegalArgumentException.class);
        verify(memberMapper, never()).updatePasswordHash(any(), any());
        verify(resetMapper, never()).invalidateActiveChallengesByUserId(any(), any());
        verify(revocation, never()).revokeAfterCommit(any());
    }

    @Test
    void rejectsDeletedOrSuspendedAccount() {
        member.setDeletedAt(LocalDateTime.of(2020, 1, 1, 0, 0));
        assertThatThrownBy(() -> service.changePassword(7L, "Current123", "NewPassword456", "NewPassword456"))
                .isInstanceOf(IllegalStateException.class);
        member.setDeletedAt(null);
        member.setPenaltyTime(LocalDateTime.of(2099, 1, 1, 0, 0));
        assertThatThrownBy(() -> service.changePassword(7L, "Current123", "NewPassword456", "NewPassword456"))
                .isInstanceOf(IllegalStateException.class);
        verify(memberMapper, never()).updatePasswordHash(any(), any());
        verify(revocation, never()).revokeAfterCommit(any());
    }
}
