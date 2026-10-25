package co.kr.spoit.club.dto;

import co.kr.spoit.club.domain.ClubMember;
import lombok.Builder;
import lombok.Getter;

@Getter 
@Builder 
public class ClubMemberResponse {

    private final String nickname;
    private final String initials;
    private final boolean leader;

    public static ClubMemberResponse from(ClubMember member) {
        return ClubMemberResponse.builder()
                .nickname(member.getNickname())
                .initials(ClubFormat.initials(member.getNickname()))
                .leader("동호회장".equals(member.getRole()))
                .build();
    }

}
