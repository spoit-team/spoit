package co.kr.spoit.facility.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 신청 지표 조회 결과.
 *
 * <p>{@code (match_id, applicant_user_id)}별 가장 최근 신청 한 건만 세므로, 팀 신청은 팀원이 각자
 * 한 건이고 같은 사람이 서로 다른 매칭방에 신청하면 각각 센다. 같은 매칭방에서 취소 후 재신청하면
 * 마지막 신청 상태만 남는다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class ApplicationCountSummary {

    /** 최신 신청 건수(대기 포함) */
    private long total;
    /** 개인 신청 수 */
    private long individualCount;
    /** 동호회 신청 수 */
    private long clubCount;
    /** 최신 신청이 확정인 수 */
    private long approvedCount;
    /** 최신 신청이 반려이거나 취소인 수 */
    private long cancelledCount;
}
