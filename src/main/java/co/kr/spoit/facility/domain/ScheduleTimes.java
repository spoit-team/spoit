package co.kr.spoit.facility.domain;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 예약 운영 화면이 주고받는 시간 문자열을 해석·표현한다.
 *
 * <p>형식은 {@code HH:mm}이며 예약 단위가 1시간이므로 분은 {@code 00}만 허용한다.
 * 종료 시각에 한해 {@code 24:00}을 다음 날 {@code 00:00} 종료 경계로 해석한다.</p>
 */
public final class ScheduleTimes {

    private static final Pattern HOUR_MINUTE = Pattern.compile("^(\\d{2}):(\\d{2})$");
    private static final String UNIT_MESSAGE = "시간은 1시간 단위로 선택해 주세요.";

    private ScheduleTimes() {
    }

    /** 시작 시각을 해석한다. {@code 24:00}은 허용하지 않는다. */
    public static LocalTime parseStart(String value, String label) {
        int minutes = parseMinutes(value, label, false);
        return LocalTime.of(minutes / 60, 0);
    }

    /**
     * 종료 시각을 해석한다. {@code 24:00}은 다음 날 00:00 종료, 즉 {@code 00:00}과
     * {@code nextDay=true}로 바꾼다.
     */
    public static EndTime parseEnd(String value, String label) {
        int minutes = parseMinutes(value, label, true);
        if (minutes == 24 * 60) {
            return new EndTime(LocalTime.MIDNIGHT, true);
        }
        return new EndTime(LocalTime.of(minutes / 60, 0), false);
    }

    private static int parseMinutes(String value, String label, boolean allow24) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "을(를) 선택해 주세요.");
        }
        Matcher matcher = HOUR_MINUTE.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(label + " 형식이 올바르지 않습니다. 예: 09:00");
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        if (minute != 0) {
            throw new IllegalArgumentException(label + "은(는) " + UNIT_MESSAGE);
        }
        if (hour == 24 && allow24) {
            return 24 * 60;
        }
        if (hour > 23) {
            throw new IllegalArgumentException(label + "은(는) 00:00부터 23:00 사이여야 합니다.");
        }
        return hour * 60;
    }

    /** {@code HH:mm} 표시 문자열로 만든다. */
    public static String format(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    /** {@code HH:mm}을 0~23시 슬롯 번호로 바꾼다. */
    public static int toHour(String value, String label) {
        try {
            LocalTime time = parseStart(value, label);
            return time.getHour();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(label + " 형식이 올바르지 않습니다. 예: 09:00", exception);
        }
    }

    /** 해석한 종료 시각과 다음 날 여부다. */
    public record EndTime(LocalTime time, boolean nextDay) {
    }
}
