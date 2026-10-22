package co.kr.spoit.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;

class CurrentMemberDtoTest {

    @Test
    @DisplayName("화면 회원 DTO는 인증 주체의 화면용 값만 복사한다")
    void copiesViewProperties() {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("player@example.com");
        member.setNickname("플레이어");
        member.setPhone("010-1234-5678");
        member.setRole("일반 회원");
        member.setStatus("활성");
        member.setPasswordHash("not-for-view");

        CurrentMemberDto dto = CurrentMemberDto.from(MemberPrincipal.from(member));

        assertThat(dto.getUserId()).isEqualTo(7L);
        assertThat(dto.getEmail()).isEqualTo("player@example.com");
        assertThat(dto.getNickname()).isEqualTo("플레이어");
        assertThat(dto.getPhone()).isEqualTo("010-1234-5678");
        assertThat(dto.getInitial()).isEqualTo("플");
        assertThat(dto.getRoleLabel()).isEqualTo("일반 회원");
    }
}
