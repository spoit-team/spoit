package co.kr.spoit.club.dto;

import co.kr.spoit.club.domain.ClubSummary;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MyClubResponse {

    private final Long clubId;
    private final String clubName;
    private final Integer memberCount;
    private final boolean active;

    // 내 동호회 선택 버튼 응답
    public static MyClubResponse from(ClubSummary club, Long activeClubId) {
        return MyClubResponse.builder()
                .clubId(club.getClubId())
                .clubName(club.getClubName())
                .memberCount(club.getMemberCount())
                .active(club.getClubId().equals(activeClubId))
                .build();
    }
}
