package co.kr.spoit.facility.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 날짜별 예외 헤더와 그 날짜에 선택한 1시간 슬롯이다.
 *
 * <p>헤더가 없으면 주간 규칙을 상속하고, 헤더는 있는데 슬롯이 0개면 그 날짜 전체가
 * 휴무다. 슬롯 값은 0~23시이며 그 자연일의 {@code [h시, h+1시)} 구간을 뜻한다.
 * 자정을 넘는 시간은 다음 날짜의 슬롯으로 나누어 저장한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class DateOverride {

    private Long overrideId;
    private Long facilityId;
    private LocalDate date;
    private String note;
    private List<Integer> hours = List.of();

    public static DateOverride of(LocalDate date, List<Integer> hours, String note) {
        DateOverride override = new DateOverride();
        override.date = date;
        override.hours = hours == null ? List.of() : new ArrayList<>(hours);
        override.note = note;
        return override;
    }

    public boolean isClosed() {
        return hours == null || hours.isEmpty();
    }
}
