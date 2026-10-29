package co.kr.spoit.club.dto;

import java.time.format.DateTimeFormatter;

import co.kr.spoit.club.domain.ClubMember;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClubMemberResponse {

    private final String nickname;
    private final String initials;
    private final boolean leader;
    private final boolean current;
    private final String joined;

    // 동호회 둘러보기 상세 화면 용
    public static ClubMemberResponse from(ClubMember member) {
        return from(member, null);
    }

    // 내 동호회 관리 화면 용
    public static ClubMemberResponse from(ClubMember member, Long userId) {
        return ClubMemberResponse.builder()
                .nickname(member.getNickname())
                .initials(ClubFormat.initials(member.getNickname()))
                .leader("동호회장".equals(member.getRole()))
                .current(userId != null && userId.equals(member.getUserId()))
                .joined(member.getJoinedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                .build();
    }

}
