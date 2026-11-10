package co.kr.spoit.facility.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 종료 매칭의 요일·시작 시간대별 최신 신청 상태 건수 조회 결과.
 *
 * <p>같은 매칭방에 같은 사람이 여러 번 신청했으면 가장 최근 신청 한 건만 센다. 대기 건수는
 * {@code totalCount - approvedCount - cancelledCount}이며, 이 값은 승인율·취소율 분모에서 빠진다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class WeekdayHourApplicationStatusCount {

    /** MySQL DAYOFWEEK: 1=일, 2=월, ... 7=토 */
    private int weekday;
    /** 0 ~ 23 */
    private int hour;
    /** 최신 신청이 확정인 건수 */
    private long approvedCount;
    /** 최신 신청이 반려 또는 취소인 건수 */
    private long cancelledCount;
    /** 최신 신청 건수(대기 포함) */
    private long totalCount;
}
