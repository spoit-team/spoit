package co.kr.spoit.facility.dto;

import lombok.Getter;

/**
 * 요일별 운영 시간 편집 표의 한 행이다.
 *
 * <p>{@code start}·{@code end}는 {@code HH:mm} 표시 문자열이며, 휴무 행은 둘 다 {@code 00:00}이다.
 * 화면 비교는 문자열 동등성으로 하므로 형식을 고정한다.</p>
 */
@Getter
public class WeeklyHourRow {

    private static final String[] DAY_LABELS = {"", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"};

    private final int day;
    private final String dayLabel;
    private final boolean open;
    private final String start;
    private final String end;

    private WeeklyHourRow(int day, boolean open, String start, String end) {
        this.day = day;
        this.dayLabel = DAY_LABELS[day];
        this.open = open;
        this.start = start;
        this.end = end;
    }

    public static WeeklyHourRow of(int day, boolean open, String start, String end) {
        return new WeeklyHourRow(day, open, start, end);
    }
}
