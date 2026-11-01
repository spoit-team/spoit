package co.kr.spoit.facility.domain;

import java.time.LocalTime;

import lombok.Getter;

/**
 * 요일별 운영 시간 규칙 한 행이다.
 *
 * <p>{@code weekday}는 1=월 ~ 7=일(java.time.DayOfWeek)이다. 휴무 행도 시간 값은
 * {@code NOT NULL} 제약 때문에 00:00으로 보관하며, 실제 운영 여부는 {@code open}으로만
 * 판단한다. {@code endNextDay}가 참이면 {@code closeTime}이 다음 날 시각이라는 뜻이다.</p>
 */
@Getter
public class OperatingRule {

    private int weekday;
    private boolean open = true;
    private LocalTime openTime = LocalTime.MIDNIGHT;
    private LocalTime closeTime = LocalTime.MIDNIGHT;
    private boolean endNextDay;

    public static OperatingRule closed(int weekday) {
        OperatingRule rule = new OperatingRule();
        rule.weekday = weekday;
        rule.open = false;
        rule.openTime = LocalTime.MIDNIGHT;
        rule.closeTime = LocalTime.MIDNIGHT;
        rule.endNextDay = false;
        return rule;
    }

    public static OperatingRule open(int weekday, LocalTime openTime, LocalTime closeTime, boolean endNextDay) {
        OperatingRule rule = new OperatingRule();
        rule.weekday = weekday;
        rule.open = true;
        rule.openTime = openTime;
        rule.closeTime = closeTime;
        rule.endNextDay = endNextDay;
        return rule;
    }

    public void setWeekday(int weekday) {
        this.weekday = weekday;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public void setOpenTime(LocalTime openTime) {
        this.openTime = openTime;
    }

    public void setCloseTime(LocalTime closeTime) {
        this.closeTime = closeTime;
    }

    public void setEndNextDay(boolean endNextDay) {
        this.endNextDay = endNextDay;
    }
}
