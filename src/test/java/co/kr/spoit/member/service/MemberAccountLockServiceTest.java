package co.kr.spoit.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.mapper.MemberMapper;

/** 계정 행 잠금 서비스가 식별자 오름차순으로 잠그고 잘못된 식별자를 걸러내는지 검증한다. */
class MemberAccountLockServiceTest {

    private MemberMapper memberMapper;
    private MemberAccountLockService service;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        service = new MemberAccountLockService(memberMapper);
    }

    @Test
    @DisplayName("여러 계정은 요청 순서와 무관하게 식별자 오름차순으로 잠근다")
    void locksInAscendingIdOrder() {
        given(memberMapper.lockById(3L)).willReturn(member(3L));
        given(memberMapper.lockById(21L)).willReturn(member(21L));
        given(memberMapper.lockById(7L)).willReturn(member(7L));

        List<Member> locked = service.lockAllById(List.of(21L, 3L, 7L, 21L));

        assertThat(locked).extracting(Member::getUserId).containsExactly(3L, 7L, 21L);
    }

    @Test
    @DisplayName("없는 계정은 결과에서 빠지고 나머지는 그대로 잠근다")
    void skipsMissingAccounts() {
        given(memberMapper.lockById(3L)).willReturn(null);
        given(memberMapper.lockById(7L)).willReturn(member(7L));

        assertThat(service.lockAllById(List.of(3L, 7L))).extracting(Member::getUserId).containsExactly(7L);
    }

    @Test
    @DisplayName("빈 목록이나 잘못된 식별자만 있으면 잠금 조회를 하지 않는다")
    void skipsEmptyOrInvalidIds() {
        assertThat(service.lockAllById(List.of())).isEmpty();
        assertThat(service.lockAllById(java.util.Arrays.asList(0L, -1L, null))).isEmpty();

        verifyNoInteractions(memberMapper);
    }

    @Test
    @DisplayName("단일 잠금은 잘못된 식별자에 조회를 보내지 않는다")
    void singleLockRejectsInvalidId() {
        assertThat(service.lockById(null)).isNull();
        assertThat(service.lockById(0L)).isNull();

        verifyNoInteractions(memberMapper);
    }

    @Test
    @DisplayName("단일 잠금은 매퍼의 잠금 조회 결과를 그대로 돌려준다")
    void singleLockReturnsLockedRow() {
        Member member = member(7L);
        given(memberMapper.lockById(7L)).willReturn(member);

        assertThat(service.lockById(7L)).isSameAs(member);
        verify(memberMapper, never()).findById(anyLong());
    }

    private static Member member(Long userId) {
        Member member = new Member();
        member.setUserId(userId);
        return member;
    }
}
