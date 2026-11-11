package co.kr.spoit.member.dto;

/** 사용자 관리 화면의 요약 지표다. */
public class AdminUserMetrics {

    private final Integer totalCount;
    private final Integer pendingCount;
    private final Integer adminCount;
    private final Integer systemAdminCount;
    private final Integer facilityAdminCount;
    private final Integer suspendedCount;

    public AdminUserMetrics(Integer totalCount, Integer pendingCount, Integer adminCount,
            Integer systemAdminCount, Integer facilityAdminCount, Integer suspendedCount) {
        this.totalCount = totalCount;
        this.pendingCount = pendingCount;
        this.adminCount = adminCount;
        this.systemAdminCount = systemAdminCount;
        this.facilityAdminCount = facilityAdminCount;
        this.suspendedCount = suspendedCount;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public Integer getPendingCount() {
        return pendingCount;
    }

    public Integer getAdminCount() {
        return adminCount;
    }

    public Integer getSystemAdminCount() {
        return systemAdminCount;
    }

    public Integer getFacilityAdminCount() {
        return facilityAdminCount;
    }

    public Integer getSuspendedCount() {
        return suspendedCount;
    }
}
