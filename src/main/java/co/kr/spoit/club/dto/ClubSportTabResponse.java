package co.kr.spoit.club.dto;

import co.kr.spoit.club.domain.ClubSportCount;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClubSportTabResponse {

    private final String name;
    private final String sportKey;
    private final int count;
    private final String url;
    private final boolean active;

    // 전체 탭: 조건 없음
    public static ClubSportTabResponse all(int total, Long selectedSportId) {
        return ClubSportTabResponse.builder()
                .name("전체").sportKey("etc").count(total).url("/teams")
                .active(selectedSportId == null)
                .build();
    }

    // 종목 탭: 주소에 sportId만 있어서 누르면 다른조건은 초기화
    public static ClubSportTabResponse from(ClubSportCount sport, Long selectedSportId) {
        return ClubSportTabResponse.builder()
                .name(sport.getName())
                .sportKey(ClubFormat.sportKey(sport.getSportId()))
                .count(sport.getClubCount())
                .url("/teams?sportId=" + sport.getSportId())
                .active(sport.getSportId().equals(selectedSportId))
                .build();
    }
}
