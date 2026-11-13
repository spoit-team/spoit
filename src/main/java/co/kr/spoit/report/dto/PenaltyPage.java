package co.kr.spoit.report.dto;

import java.util.List;

/**
 * 페널티 관리 화면 모델(지표·검색 조건·이력 행)이다.
 *
 * <p>목록은 {@code pageSize + 1}건 조회로 만든 offset 페이지이며, {@code hasNextPenalties}는 그
 * 초과분으로 판정한 다음 페이지 존재 여부다. 화면에는 {@code pageSize}건만 노출한다.</p>
 */
public class PenaltyPage {

    private final PenaltyMetrics metrics;
    private final PenaltyFilters filters;
    private final List<PenaltyListItem> penalties;
    private final boolean hasNextPenalties;

    public PenaltyPage(PenaltyMetrics metrics, PenaltyFilters filters,
            List<PenaltyListItem> penalties, boolean hasNextPenalties) {
        this.metrics = metrics;
        this.filters = filters;
        this.penalties = List.copyOf(penalties);
        this.hasNextPenalties = hasNextPenalties;
    }

    /** 아직 조회하지 않은 빈 화면 모델이다. */
    public static PenaltyPage empty() {
        return new PenaltyPage(new PenaltyMetrics(0, 0, 0), new PenaltyFilters(null, "all", 0),
                List.of(), false);
    }

    public PenaltyMetrics getMetrics() {
        return metrics;
    }

    public PenaltyFilters getFilters() {
        return filters;
    }

    public List<PenaltyListItem> getPenalties() {
        return penalties;
    }

    public boolean isHasNextPenalties() {
        return hasNextPenalties;
    }
}
