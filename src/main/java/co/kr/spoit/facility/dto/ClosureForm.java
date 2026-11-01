package co.kr.spoit.facility.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 휴관 등록 요청이다.
 *
 * <p>화면 폼 계약은 {@code facilityId, date, start, end, reason}이다. 종료 시각이 시작보다
 * 이르거나 {@code 24:00}이면 다음 날로 이어진다.</p>
 */
@Getter
@Setter
public class ClosureForm {

    private Long facilityId;

    /** ISO 날짜 문자열(yyyy-MM-dd)이다. */
    private String date;

    private String start;

    private String end;

    private String reason;
}
