package co.kr.spoit.mat.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 마이페이지 #match-records 화면 모델. 문구와 숫자 형식은 MyRatingRecordService가 정한다. */
@Getter
@AllArgsConstructor
public class MyRatingRecords {

    private final List<SportCard> sports;
    private final boolean allSelected;
    private final int historyTotalCount;
    private final String historyFilterLabel;
    /** 선택한 범위의 반영 경기 수. 목록은 그중 최근 경기만 담는다. */
    private final int historyCount;
    private final List<HistoryItem> history;
    private final String historyEmptyNote;

    @Getter
    @AllArgsConstructor
    public static class SportCard {
        private final Long sportId;
        private final String name;
        private final String tierLabel;
        private final String mmrText;
        private final int ratedMatchCount;
        /** 배치를 마쳐 티어 진행도 막대를 그릴 수 있는지 */
        private final boolean placed;
        private final int tierPoints;
        private final boolean selected;
    }

    @Getter
    @AllArgsConstructor
    public static class HistoryItem {
        private final Long id;
        private final String date;
        private final String title;
        private final String sportName;
        private final String place;
        private final String applicationType;
        private final String resultLabel;
        /** win, draw, loss */
        private final String resultKey;
        private final String deltaText;
        /** up, down, zero */
        private final String deltaKey;
        private final String mmrBeforeText;
        private final String mmrAfterText;
        private final String myTeam;
        private final String averageAText;
        private final String averageBText;
        private final List<Fact> facts;
    }

    @Getter
    @AllArgsConstructor
    public static class Fact {
        private final String label;
        private final String value;
    }
}
