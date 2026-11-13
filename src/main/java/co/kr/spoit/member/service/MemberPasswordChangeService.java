package co.kr.spoit.member.service;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.mapper.PasswordResetMapper;
import co.kr.spoit.member.security.MemberSessionRevocationService;
import lombok.RequiredArgsConstructor;

/** 로그인한 회원의 기존 비밀번호를 확인한 후 새 비밀번호로 변경한다. */
@Service
@RequiredArgsConstructor
public class MemberPasswordChangeService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MemberAccountLockService accountLockService;
    private final MemberMapper memberMapper;
    private final PasswordResetMapper passwordResetMapper;
    private final PasswordEncoder passwordEncoder;
    private final MemberSessionRevocationService sessionRevocationService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void changePassword(Long userId, String currentPassword, String newPassword, String newPasswordConfirm) {
        if (currentPassword == null || currentPassword.isBlank()) {
            throw new IllegalArgumentException("현재 비밀번호를 입력해 주세요.");
        }
        Member member = accountLockService.lockById(userId);
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (member == null || member.getDeletedAt() != null
                || (member.getPenaltyTime() != null && member.getPenaltyTime().isAfter(now))) {
            throw new IllegalStateException("비밀번호를 변경할 수 없는 계정입니다.");
        }
        if (member.getPasswordHash() == null || member.getPasswordHash().isBlank()
                || !passwordEncoder.matches(currentPassword, member.getPasswordHash())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }
        MemberPasswordPolicy.validate(newPassword, newPasswordConfirm);
        if (memberMapper.updatePasswordHash(userId, passwordEncoder.encode(newPassword)) != 1) {
            throw new IllegalStateException("비밀번호를 변경하지 못했습니다.");
        }
        // 기존 메일 인증으로 변경한 비밀번호를 다시 덮어쓰지 못하도록 같은 트랜잭션에서 폐기한다.
        passwordResetMapper.invalidateActiveChallengesByUserId(userId, now);
        sessionRevocationService.revokeAfterCommit(userId);
    }
}
