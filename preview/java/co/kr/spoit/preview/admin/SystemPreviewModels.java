package co.kr.spoit.preview.admin;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

public final class SystemPreviewModels {

    private SystemPreviewModels() {
    }

    @Getter
    @AllArgsConstructor
    public static class SystemDashboardView {
        private final String collectedAt;
        private final String apiStatus;
        private final String apiUptime;
        private final int cpuPercent;
        private final String cpuPeak;
        private final int memoryPercent;
        private final String memoryCapacity;
        private final int recentErrorCount;
        private final String recentErrorAttention;
        private final int diskPercent;
        private final boolean allHealthy;
        private final List<DashboardBarView> bars;
        private final List<ServiceStatusView> services;
        private final List<RecentErrorView> recentErrors;
        private final List<ReportRowView> recentReports;
    }

    @Getter
    @AllArgsConstructor
    public static class DashboardBarView {
        private final int cpuHeight;
        private final int memoryHeight;
        private final String label;
    }

    @Getter
    @AllArgsConstructor
    public static class ServiceStatusView {
        private final String tone;
        private final String name;
        private final String detail;
    }

    @Getter
    @AllArgsConstructor
    public static class RecentErrorView {
        private final String time;
        private final String severityTone;
        private final String severityLabel;
        private final String service;
        private final String summary;
        private final String code;
        private final int count;
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorMetricsView {
        private final int count;
        private final int uncheckedCount;
        private final int warningCount;
        private final int openCount;
        private final String averageResponse;
        private final String lastCollected;
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorFiltersView {
        private final String q;
        private final String severity;
        private final int period;
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorLogView {
        private final String time;
        private final String severityTone;
        private final String severityLabel;
        private final String service;
        private final String code;
        private final String summary;
        private final int count;
        private final String detail;
    }

    @Getter
    @AllArgsConstructor
    public static class ReportMetricsView {
        private final int openCount;
        private final int highCount;
        private final int sanctionCount;
        private final int repeatCount;
    }

    @Getter
    @AllArgsConstructor
    public static class ReportFiltersView {
        private final String q;
        private final String status;
        private final String priority;
    }

    @Getter
    @AllArgsConstructor
    public static class ReportRowView {
        private final String reportId;
        private final String type;
        private final String target;
        private final String reporter;
        private final String receivedAt;
        private final int relatedLogs;
        private final String priorityTone;
        private final String priority;
        private final String statusTone;
        private final String statusLabel;
        private final String summary;
        private final String detail;
        private final List<EvidenceView> evidence;
        private final List<ReportHistoryView> history;
    }

    @Getter
    @AllArgsConstructor
    public static class EvidenceView {
        private final String time;
        private final String tone;
        private final String title;
        private final String detail;
    }

    @Getter
    @AllArgsConstructor
    public static class ReportHistoryView {
        private final String date;
        private final String type;
        private final String status;
    }

    @Getter
    @AllArgsConstructor
    public static class PaginationView {
        private final Integer totalPages;
        private final int page;
        private final String prevUrl;
        private final String nextUrl;
    }

    @Getter
    @AllArgsConstructor
    public static class PenaltyMetricsView {
        private final int activeCount;
        private final int warningCount;
        private final int monthlyCount;
    }

    @Getter
    @AllArgsConstructor
    public static class PenaltyFiltersView {
        private final String q;
        private final String status;
    }

    @Getter
    @AllArgsConstructor
    public static class PenaltyView {
        private final Long penaltyId;
        private final String account;
        private final String email;
        private final String action;
        private final String startAt;
        private final String endAt;
        private final String reportId;
        private final String reason;
        private final String statusTone;
        private final String statusLabel;
        private final boolean active;
    }

    @Getter
    @AllArgsConstructor
    public static class UserMetricsView {
        private final int totalCount;
        private final int pendingCount;
        private final int adminCount;
        private final int systemAdminCount;
        private final int facilityAdminCount;
        private final int suspendedCount;
    }

    @Getter
    @AllArgsConstructor
    public static class UserFiltersView {
        private final String q;
        private final String role;
        private final String status;
    }

    @Getter
    @AllArgsConstructor
    public static class UserView {
        private final String userId;
        private final String nickname;
        private final String email;
        private final String roleValue;
        private final String roleTone;
        private final String roleLabel;
        private final String approvalTone;
        private final String approvalLabel;
        private final String statusTone;
        private final String statusLabel;
        private final String sanction;
        private final String joinedAt;
        private final String lastActive;
        private final boolean pending;
        private final boolean suspended;
    }

    @Getter
    @AllArgsConstructor
    public static class CmsNoticeView {
        private final String title;
        private final String type;
        private final String statusTone;
        private final String statusLabel;
        private final String author;
        private final String updatedAt;
        private final String periodLabel;
    }

    @Getter
    @AllArgsConstructor
    public static class CmsFaqView {
        private final String question;
        private final String category;
        private final String statusTone;
        private final String statusLabel;
        private final int order;
        private final String updatedAt;
    }

    @Getter
    @AllArgsConstructor
    public static class CmsTermView {
        private final String title;
        private final String version;
        private final String effectiveAt;
        private final String statusTone;
        private final String statusLabel;
        private final String author;
        private final String documentUrl;
    }

    @Getter
    @AllArgsConstructor
    public static class SystemSettingsView {
        private final boolean reservationsEnabled;
        private final boolean maintenanceBanner;
        private final boolean signupEnabled;
        private final boolean adminTwoFactor;
        private final int adminSessionMinutes;
        private final boolean passwordResetNotice;
        private final boolean incidentAlerts;
        private final boolean approvalDelayAlerts;
        private final boolean dailyDigest;
        private final String supportEmail;
        private final String supportHours;
        private final String timezone;
        private final String lastChangedLabel;
    }

    @Getter
    @AllArgsConstructor
    public static class AuditFiltersView {
        private final String q;
        private final String action;
        private final int period;
    }

    @Getter
    @AllArgsConstructor
    public static class AuditLogView {
        private final String time;
        private final String actor;
        private final String actorRole;
        private final String actionLabel;
        private final String target;
        private final String resultTone;
        private final String resultLabel;
        private final String requestId;
        private final String detail;
    }
}
