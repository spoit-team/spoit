package co.kr.spoit.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.kr.spoit.club.dto.ClubWithdrawalBlocks;
import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.dto.WithdrawalBlocks;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.service.MemberAccountLockService;

/** 탈퇴가 비밀번호 재확인 후 deleted_at 기록 1회로 끝나는지 검증한다. */
class MemberServiceImplWithdrawalTest {

    private static final Long USER_ID = 7L;
    private static final String RAW_PASSWORD = "spoit1234!";

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MemberMapper memberMapper;
    private ClubService clubService;
    private MatchApplicationService matchApplicationService;
    private MemberAccountLockService accountLockService;
    private MemberServiceImpl memberService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        clubService = mock(ClubService.class);
        matchApplicationService = mock(MatchApplicationService.class);
        accountLockService = mock(MemberAccountLockService.class);
        lenient().when(clubService.findWithdrawalBlocks(anyLong()))
                .thenReturn(ClubWithdrawalBlocks.empty());
        lenient().when(matchApplicationService.findActiveIndividualMatchTitles(anyLong())).thenReturn(List.of());
        memberService = new MemberServiceImpl(memberMapper, passwordEncoder, clubService,
                matchApplicationService, accountLockService);
    }

    @Test
    @DisplayName("비밀번호가 일치하면 탈퇴 시각을 기록한다")
    void recordsDeletedAt() {
        when(accountLockService.lockById(USER_ID)).thenReturn(member());
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
        when(accountLockService.lockById(USER_ID)).thenReturn(member());

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
        when(accountLockService.lockById(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> memberService.withdraw(USER_ID, RAW_PASSWORD))
                .isInstanceOf(IllegalStateException.class);

        verify(memberMapper, never()).recordWithdrawal(anyLong(), any(), any());
    }

    @Test
    @DisplayName("이미 탈퇴했거나 정지된 계정은 시각을 기록하지 않는다")
    void rejectsNonWithdrawableMember() {
        when(accountLockService.lockById(USER_ID)).thenReturn(member());
        when(memberMapper.recordWithdrawal(eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> memberService.withdraw(USER_ID, RAW_PASSWORD))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("동호회장으로 있으면 탈퇴 시각을 기록하지 않고 차단 예외를 던진다")
    void blocksLeaderWithdrawal() {
        when(accountLockService.lockById(USER_ID)).thenReturn(member());
        when(clubService.findWithdrawalBlocks(USER_ID))
                .thenReturn(ClubWithdrawalBlocks.of(List.of("강서 테니스회"), List.of()));

        assertThatThrownBy(() -> memberService.withdraw(USER_ID, RAW_PASSWORD))
                .isInstanceOf(WithdrawalBlockedException.class)
                .hasMessageContaining("탈퇴할 수 없습니다");

        verify(memberMapper, never()).recordWithdrawal(anyLong(), any(), any());
    }

    @Test
    @DisplayName("모집 중인 팀 매치에 신청 중이면 탈퇴 시각을 기록하지 않는다")
    void blocksRecruitingTeamMatchWithdrawal() {
        when(accountLockService.lockById(USER_ID)).thenReturn(member());
        when(clubService.findWithdrawalBlocks(USER_ID))
                .thenReturn(ClubWithdrawalBlocks.of(List.of(), List.of("주말 풋살 클럽")));

        assertThatThrownBy(() -> memberService.withdraw(USER_ID, RAW_PASSWORD))
                .isInstanceOf(WithdrawalBlockedException.class);

        verify(memberMapper, never()).recordWithdrawal(anyLong(), any(), any());
    }

    @Test
    @DisplayName("차단 사유가 없으면 그대로 탈퇴한다")
    void allowsWithdrawalWithoutBlocks() {
        when(accountLockService.lockById(USER_ID)).thenReturn(member());
        when(memberMapper.recordWithdrawal(eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);

        memberService.withdraw(USER_ID, RAW_PASSWORD);

        verify(memberMapper).recordWithdrawal(eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("탈퇴는 계정을 먼저 잠그고, 그 뒤 비밀번호 확인과 차단 사유 조회를 한다")
    void locksAccountBeforePasswordAndBlockerCheck() {
        when(accountLockService.lockById(USER_ID)).thenReturn(member());
        when(memberMapper.recordWithdrawal(eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);

        memberService.withdraw(USER_ID, RAW_PASSWORD);

        InOrder order = inOrder(accountLockService, clubService, matchApplicationService, memberMapper);
        order.verify(accountLockService).lockById(USER_ID);
        order.verify(clubService).findWithdrawalBlocks(USER_ID);
        order.verify(matchApplicationService).findActiveIndividualMatchTitles(USER_ID);
        order.verify(memberMapper).recordWithdrawal(eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(memberMapper, never()).findById(anyLong());
    }

    @Test
    @DisplayName("탈퇴 차단 사유 조회는 club·match 도메인 서비스를 조합하고 개인 매치 이름을 함께 담는다")
    void findWithdrawalBlocksComposesDomainServices() {
        when(clubService.findWithdrawalBlocks(USER_ID))
                .thenReturn(ClubWithdrawalBlocks.of(List.of("강서 테니스회"), List.of("주말 풋살 클럽")));
        when(matchApplicationService.findActiveIndividualMatchTitles(USER_ID))
                .thenReturn(List.of("화요일 저녁 복식"));

        WithdrawalBlocks blocks = memberService.findWithdrawalBlocks(USER_ID);

        assertThat(blocks.getLeaderClubNames()).containsExactly("강서 테니스회");
        assertThat(blocks.getTeamMatchClubNames()).containsExactly("주말 풋살 클럽");
        assertThat(blocks.getIndividualMatchNames()).containsExactly("화요일 저녁 복식");
        assertThat(blocks.isBlocked()).isTrue();
    }

    @Test
    @DisplayName("개인 매치 신청만 있으면 개인 카테고리 때문에 탈퇴가 막힌다")
    void blocksIndividualMatchWithdrawal() {
        when(accountLockService.lockById(USER_ID)).thenReturn(member());
        when(matchApplicationService.findActiveIndividualMatchTitles(USER_ID))
                .thenReturn(List.of("화요일 저녁 복식"));

        assertThatThrownBy(() -> memberService.withdraw(USER_ID, RAW_PASSWORD))
                .isInstanceOf(WithdrawalBlockedException.class);

        verify(memberMapper, never()).recordWithdrawal(anyLong(), any(), any());
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
