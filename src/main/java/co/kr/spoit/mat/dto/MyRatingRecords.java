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
        /** 티어 색 키(bronze 등). 배치 중이면 null */
        private final String tierKey;
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
        /** "+38P"처럼 포인트 변동, 배치 경기면 "배치 1/5"나 "배치 완료" */
        private final String deltaText;
        /** up, down, zero, placement */
        private final String deltaKey;
        /** 화살표 앞: "실버 4 · 31P", 배치 경기면 "배치 경기 1/5"나 "배치 완료" */
        private final TierText ratingFrom;
        /** 화살표 뒤: "실버 4 · 93P". 배치 중인 경기면 null이라 화살표를 그리지 않는다. */
        private final TierText ratingTo;
        /** 승급, 강등. 단계가 그대로거나 배치 경기면 null */
        private final String divisionChange;
        private final String myTeam;
        /** 반영 시점 팀 평균의 티어 이름 */
        private final TierText averageA;
        private final TierText averageB;
        private final List<Fact> facts;
    }

    /** 티어 색과 함께 보여 줄 문구. tierKey가 null이면 배치 문구라 중립색이다. */
    @Getter
    @AllArgsConstructor
    public static class TierText {
        private final String text;
        private final String tierKey;
    }

    @Getter
    @AllArgsConstructor
    public static class Fact {
        private final String label;
        private final String value;
    }
}
