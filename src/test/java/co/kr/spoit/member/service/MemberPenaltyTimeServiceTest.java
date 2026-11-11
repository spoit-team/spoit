package co.kr.spoit.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.security.MemberSessionRevocationService;

/**
 * 제재 결과를 {@code app_user.penalty_time}에 반영하는 규칙을 검증한다.
 *
 * <p>연장은 이미 더 늦은 종료 시각이 있으면 그대로 두고, 실제로 늘어난 미래 종료 시각에만 세션 폐기를
 * 예약한다. 이미 지난 종료 시각은 저장만 하고 폐기를 예약하지 않는다. 덮어쓰기는 회원 행을 먼저 잠근
 * 뒤 컬럼을 바꾼다.</p>
 */
class MemberPenaltyTimeServiceTest {

    private static final long USER_ID = 11L;

    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final MemberAccountLockService accountLockService = mock(MemberAccountLockService.class);
    private final MemberSessionRevocationService revocationService =
            mock(MemberSessionRevocationService.class);
    private final MemberPenaltyTimeService service = new MemberPenaltyTimeService(memberMapper,
            accountLockService, revocationService);

    @BeforeEach
    void stubDefaults() {
        when(accountLockService.lockById(USER_ID)).thenReturn(member(null));
    }

    @Test
    @DisplayName("이미 더 늦은 종료 시각이 있으면 갱신도 세션 폐기도 하지 않는다")
    void keepsLaterExistingEnd() {
        LocalDateTime existing = LocalDateTime.now().plusDays(10);
        LocalDateTime requested = LocalDateTime.now().plusDays(3);
        when(accountLockService.lockById(USER_ID)).thenReturn(member(existing));

        LocalDateTime result = service.extendUntil(USER_ID, requested);

        assertThat(result).isEqualTo(existing);
        verify(memberMapper, never()).updatePenaltyTime(anyLong(), any());
        verify(revocationService, never()).revokeAfterCommit(anyLong());
    }

    @Test
    @DisplayName("미래 종료 시각으로 늘리면 컬럼을 갱신하고 세션 폐기를 예약한다")
    void updatesFutureEndAndSchedulesRevocation() {
        LocalDateTime requested = LocalDateTime.now().plusDays(7);

        LocalDateTime result = service.extendUntil(USER_ID, requested);

        assertThat(result).isEqualTo(requested);
        verify(memberMapper).updatePenaltyTime(USER_ID, requested);
        verify(revocationService).revokeAfterCommit(USER_ID);
    }

    @Test
    @DisplayName("이미 지난 종료 시각은 저장만 하고 세션 폐기를 예약하지 않는다")
    void expiredEndDoesNotRevoke() {
        LocalDateTime requested = LocalDateTime.now().minusDays(1);

        LocalDateTime result = service.extendUntil(USER_ID, requested);

        assertThat(result).isEqualTo(requested);
        verify(memberMapper).updatePenaltyTime(USER_ID, requested);
        verify(revocationService, never()).revokeAfterCommit(anyLong());
    }

    @Test
    @DisplayName("해제는 회원 행을 먼저 잠근 뒤 종료 시각 컬럼을 비운다")
    void overwriteNullLocksThenClears() {
        service.overwrite(USER_ID, null);

        InOrder order = inOrder(accountLockService, memberMapper);
        order.verify(accountLockService).lockById(USER_ID);
        order.verify(memberMapper).updatePenaltyTime(USER_ID, null);
        verify(revocationService, never()).revokeAfterCommit(anyLong());
    }

    private static Member member(LocalDateTime penaltyTime) {
        Member member = new Member();
        member.setUserId(USER_ID);
        member.setRole("일반 회원");
        member.setPenaltyTime(penaltyTime);
        return member;
    }
}
