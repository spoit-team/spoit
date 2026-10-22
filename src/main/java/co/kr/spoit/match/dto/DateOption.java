package co.kr.spoit.match.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 날짜 선택지: idx는 오늘 기준 일수 */
@Getter
@AllArgsConstructor
public class DateOption {

    private final int idx;
    private final LocalDate date;
    /** 오늘, 내일, 10/24(토) */
    private final String label;
}
