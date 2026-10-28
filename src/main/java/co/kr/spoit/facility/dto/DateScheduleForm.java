package co.kr.spoit.facility.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 날짜별 운영 시간 저장 요청이다.
 *
 * <p>화면 폼 계약은 {@code facilityId, date(ISO), hours(반복 HH:mm start), note}이다.
 * {@code hours}가 0건이면 그 날짜 전체 휴무를 뜻하며, 이때도 예외 헤더 행을 남겨
 * 주간 규칙 상속과 구분한다.</p>
 */
@Getter
@Setter
public class DateScheduleForm {

    private Long facilityId;

    /** ISO 날짜 문자열(yyyy-MM-dd)이다. 서비스가 파싱·검증한다. */
    private String date;

    /** 1시간 슬롯 시작 시각 목록("HH:mm", 00:00~23:00). */
    private List<String> hours = List.of();

    private String note;
}
