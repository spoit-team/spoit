package co.kr.spoit.facility.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 신청 지표 조회 결과 (대표 행 기준) */
@Getter
@Setter
@NoArgsConstructor
public class ApplicationCountSummary {

    /** 모든 상태의 대표 신청 행 수 */
    private long total;
    /** 개인 신청 수 */
    private long individualCount;
    /** 동호회 신청 수 */
    private long clubCount;
    /** 확정 상태 신청 수 */
    private long approvedCount;
}
