package co.kr.spoit.preview.admin;

import static co.kr.spoit.preview.admin.AdminPreviewFixtures.adminNotificationSummary;
import static co.kr.spoit.preview.admin.AdminPreviewFixtures.adminNotifications;
import static co.kr.spoit.preview.admin.AdminPreviewFixtures.facilityAdmin;
import static co.kr.spoit.preview.admin.AdminPreviewFixtures.systemAdmin;
import static co.kr.spoit.preview.admin.AdminPreviewFixtures.unreadAdminNotificationCount;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminPreviewController {

    @GetMapping("/admin/facility")
    public String facility(@RequestParam(name = "view", required = false) String view, Model model) {
        addAdminShell(model, facilityAdmin());
        model.addAttribute("view", view == null ? "dashboard" : view);
        model.addAttribute("scopeName", "SPO:IT 강남 테니스센터");
        model.addAttribute("todayLabel", "2026년 10월 21일 (수)");
        model.addAttribute("chartCollectedAt", "2026-10-21 18:05");
        model.addAttribute("dashboard", AdminPreviewFixtures.facilityDashboard());

        model.addAttribute("facilities", AdminPreviewFixtures.facilities());
        model.addAttribute("selectedFacility", AdminPreviewFixtures.facilities().get(0));
        model.addAttribute("weeklyHours", AdminPreviewFixtures.weeklyHours());
        model.addAttribute("hourOptions", AdminPreviewFixtures.hourOptions());
        model.addAttribute("calendar", AdminPreviewFixtures.calendar());
        model.addAttribute("sports", AdminPreviewFixtures.sports());
        model.addAttribute("conditions", AdminPreviewFixtures.conditions());
        model.addAttribute("closures", AdminPreviewFixtures.closures());
        model.addAttribute("matches", AdminPreviewFixtures.matches());
        model.addAttribute("bookingFilters", AdminPreviewFixtures.bookingFilters());
        model.addAttribute("stats", AdminPreviewFixtures.facilityStats());
        model.addAttribute("requestMetrics", AdminPreviewFixtures.requestMetrics());
        model.addAttribute("requestFilters", AdminPreviewFixtures.requestFilters());
        model.addAttribute("requests", AdminPreviewFixtures.facilityRequests());
        model.addAttribute("maintenanceMetrics", AdminPreviewFixtures.maintenanceMetrics());
        model.addAttribute("maintenanceFilters", AdminPreviewFixtures.maintenanceFilters());
        model.addAttribute("maintenance", AdminPreviewFixtures.maintenance());
        model.addAttribute("notices", AdminPreviewFixtures.notices());
        model.addAttribute("rules", AdminPreviewFixtures.rules());
        model.addAttribute("roomEditorHourSlots", AdminPreviewFixtures.roomEditorHourSlots());
        return "admin/facility";
    }

    @GetMapping("/admin/operations")
    public String operations(@RequestParam(name = "matchId", required = false) Long matchId, Model model) {
        addAdminShell(model, facilityAdmin());
        model.addAttribute("match", AdminPreviewFixtures.matchOperations());
        return "admin/operations";
    }

    @GetMapping("/admin/system")
    public String system(@RequestParam(name = "view", required = false) String view, Model model) {
        addAdminShell(model, systemAdmin());
        model.addAttribute("view", view == null ? "dashboard" : view);
        model.addAttribute("dashboard", AdminPreviewFixtures.systemDashboard());
        model.addAttribute("errorMetrics", AdminPreviewFixtures.errorMetrics());
        model.addAttribute("errorFilters", AdminPreviewFixtures.errorFilters());
        model.addAttribute("errors", AdminPreviewFixtures.errors());
        model.addAttribute("reportMetrics", AdminPreviewFixtures.reportMetrics());
        model.addAttribute("reportFilters", AdminPreviewFixtures.reportFilters());
        model.addAttribute("reports", AdminPreviewFixtures.reports());
        model.addAttribute("reportPagination", AdminPreviewFixtures.reportPagination());
        model.addAttribute("penaltyMetrics", AdminPreviewFixtures.penaltyMetrics());
        model.addAttribute("penaltyFilters", AdminPreviewFixtures.penaltyFilters());
        model.addAttribute("penalties", AdminPreviewFixtures.penalties());
        model.addAttribute("userMetrics", AdminPreviewFixtures.userMetrics());
        model.addAttribute("userFilters", AdminPreviewFixtures.userFilters());
        model.addAttribute("users", AdminPreviewFixtures.users());
        model.addAttribute("userPagination", AdminPreviewFixtures.userPagination());
        model.addAttribute("cmsNotices", AdminPreviewFixtures.cmsNotices());
        model.addAttribute("cmsFaqs", AdminPreviewFixtures.cmsFaqs());
        model.addAttribute("cmsTerms", AdminPreviewFixtures.cmsTerms());
        model.addAttribute("settings", AdminPreviewFixtures.systemSettings());
        model.addAttribute("auditFilters", AdminPreviewFixtures.auditFilters());
        model.addAttribute("auditLogs", AdminPreviewFixtures.auditLogs());
        model.addAttribute("auditPagination", AdminPreviewFixtures.auditPagination());
        return "admin/system";
    }

    private void addAdminShell(Model model, AdminCommonModels.AdminUserView adminUser) {
        model.addAttribute("adminUser", adminUser);
        model.addAttribute("adminNotifications", adminNotifications());
        model.addAttribute("unreadAdminNotificationCount", unreadAdminNotificationCount());
        model.addAttribute("adminNotificationSummary", adminNotificationSummary());
    }
}
