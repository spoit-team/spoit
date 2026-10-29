package co.kr.spoit.match.dto;

import java.util.List;

import lombok.Getter;

/**
 * 시설 관리자 매칭방 목록 화면 모델이다.
 *
 * <p>세 목록은 항상 null이 아닌 리스트이며, 필터 선택 상태는 정규화된 값을 그대로 돌려준다.
 * 담당 시설이나 매칭방이 없으면 목록은 비어 있고, 종목 선택지는 담당 시설이 제공하는
 * 종목에서 나오므로 비어 있지 않을 수 있다.</p>
 */
@Getter
public class RoomManagementPage {

    private final List<Match> matches;
    private final List<Sport> sports;
    private final Filters filters;

    private RoomManagementPage(List<Match> matches, List<Sport> sports, Filters filters) {
        this.matches = List.copyOf(matches);
        this.sports = List.copyOf(sports);
        this.filters = filters;
    }

    /** 담당 시설이 없거나 관리자 식별자가 없을 때 쓰는 빈 페이지 */
    public static RoomManagementPage empty() {
        return new RoomManagementPage(List.of(), List.of(), Filters.none());
    }

    public static RoomManagementPage of(List<Match> matches, List<Sport> sports, Filters filters) {
        return new RoomManagementPage(matches, sports, filters);
    }

    /** 목록 표 한 행 */
    @Getter
    public static class Match {

        private final Long matchId;
        private final String title;
        private final String facilityName;
        private final String sportName;
        /** "3 / 8명" */
        private final String participation;
        /** "10/29(수) 18:00~20:00" (자정을 넘기면 종료 일시까지 표시) */
        private final String dateLabel;
        private final String statusLabel;
        private final String statusTone;

        public Match(Long matchId, String title, String facilityName, String sportName,
                String participation, String dateLabel, String statusLabel, String statusTone) {
            this.matchId = matchId;
            this.title = title;
            this.facilityName = facilityName;
            this.sportName = sportName;
            this.participation = participation;
            this.dateLabel = dateLabel;
            this.statusLabel = statusLabel;
            this.statusTone = statusTone;
        }
    }

    /** 종목 필터 선택지 */
    @Getter
    public static class Sport {

        private final Long sportId;
        private final String name;

        public Sport(Long sportId, String name) {
            this.sportId = sportId;
            this.name = name;
        }
    }

    /** 정규화된 필터 선택 상태. status는 all/recruiting/matched/completed/cancelled 중 하나다. */
    @Getter
    public static class Filters {

        private final String q;
        private final String status;
        private final Long sportId;

        public Filters(String q, String status, Long sportId) {
            this.q = q;
            this.status = status;
            this.sportId = sportId;
        }

        public static Filters none() {
            return new Filters(null, "all", null);
        }
    }
}
