package co.kr.spoit.report.dto;

/** 페널티 관리 화면의 요약 지표다. */
public class PenaltyMetrics {

    private final Integer activeCount;
    private final Integer warningCount;
    private final Integer monthlyCount;

    public PenaltyMetrics(Integer activeCount, Integer warningCount, Integer monthlyCount) {
        this.activeCount = activeCount;
        this.warningCount = warningCount;
        this.monthlyCount = monthlyCount;
    }

    /** 현재 적용 중인 이용 제한 건수다. */
    public Integer getActiveCount() {
        return activeCount;
    }

    /** 누적 경고 기록 건수다. */
    public Integer getWarningCount() {
        return warningCount;
    }

    /** 이번 달에 부여된 제재 건수다. */
    public Integer getMonthlyCount() {
        return monthlyCount;
    }
}
