package co.kr.spoit.mat.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 경기 결과 제출 화면 모델. 팀장이 아니면 captain이 false이고 경기 정보와 명단은 비워 둔다. */
@Getter
@AllArgsConstructor
public class MatchResultPage {

    private final long matchId;
    private final boolean captain;
    private final String title;
    private final String sportName;
    private final String facilityName;
    private final String scheduleLabel;
    private final String mySide;
    private final String deadlineLabel;
    /** 조회 API로는 제출할 수 없는 이유를 알 수 없을 때 화면이 보여 줄 안내 */
    private final String closedNote;
    private final List<Team> teams;

    public static MatchResultPage notCaptain(long matchId) {
        return new MatchResultPage(matchId, false, null, null, null, null, null, null, null, List.of());
    }

    @Getter
    @AllArgsConstructor
    public static class Team {

        private final String side;
        private final boolean mine;
        private final List<Member> members;
    }

    @Getter
    @AllArgsConstructor
    public static class Member {

        private final String nickname;
        private final boolean captain;
    }
}
