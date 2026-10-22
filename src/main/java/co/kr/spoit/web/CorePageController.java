package co.kr.spoit.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 주요 화면 템플릿을 렌더링하는 GET 전용 페이지 컨트롤러이다.
 *
 * <p>서비스/DB 연동 전이라 각 화면이 요구하는 모델 속성을 빈 값·기본값으로 채워
 * "데이터가 없는 현재 상태"를 표현한다. 실제 조회와 상태 변경(POST)은 이후 서비스 계층에서 담당한다.</p>
 */
@Controller
public class CorePageController {

    /** 여러 화면이 공유하는 불변 빈 목록이다. */
    private static final List<Object> EMPTY_LIST = Collections.emptyList();

    /* ------------------------------------------------------------------
     * 인증 화면: 로그인은 MemberAuthController가 담당하고, 여기서는 회원가입 폼만 렌더링한다.
     * ------------------------------------------------------------------ */

    /** 회원가입 화면을 렌더링한다. 폼 객체와 지역 목록은 아직 조회하지 않아 null/빈 목록으로 전달한다. */
    @GetMapping("/signup")
    public String signup(Model model) {
        // 폼 객체가 아직 없으므로 null을 넘겨 템플릿의 빈 폼을 사용한다.
        model.addAttribute("signupForm", null);
        model.addAttribute("districts", EMPTY_LIST);
        model.addAttribute("error", null);
        model.addAttribute("message", null);
        return "auth/signup";
    }

    /* ------------------------------------------------------------------
     * 마이페이지: 로그인 회원이 연결되지 않아 모든 값이 빈 상태이며, 빈 문자열·0은 템플릿의 널 안전 표시용이다.
     * ------------------------------------------------------------------ */

    /** 마이페이지 요약 화면을 렌더링한다. 회원·활동·기록·예약·알림을 모두 빈 상태로 전달한다. */
    @GetMapping("/mypage")
    public String myPage(Model model) {
        // 비로그인/미연동 상태에서도 템플릿이 참조할 수 있는 빈 회원 표현이다.
        model.addAttribute("member", map(
                "initial", "",
                "nickname", "",
                "roleLabel", "",
                "email", "",
                "regionText", ""));
        model.addAttribute("summary", map(
                "representativeRatingText", "",
                "representativeSportId", "",
                "representativeSportName", "",
                "skillNote", "",
                "confirmedCount", 0,
                "completedCount", 0,
                "waitingCount", 0,
                "otherSportsNote", ""));
        model.addAttribute("otherSports", EMPTY_LIST);
        model.addAttribute("sports", EMPTY_LIST);
        model.addAttribute("selectedSport", "all");
        model.addAttribute("historyTotalCount", 0);
        model.addAttribute("historyFilterLabel", "전체 종목");
        model.addAttribute("historyCount", 0);
        model.addAttribute("history", EMPTY_LIST);
        model.addAttribute("historyEmptyNote", "확정된 경기 기록이 없습니다.");
        model.addAttribute("currentReservations", EMPTY_LIST);
        model.addAttribute("pastReservations", EMPTY_LIST);
        model.addAttribute("notifications", EMPTY_LIST);
        model.addAttribute("message", null);
        return "mypage/index";
    }

    /** 프로필 수정 화면을 렌더링한다. 프로필은 null, 종목·지역 선택지는 빈 목록으로 전달한다. */
    @GetMapping("/mypage/profile")
    public String profile(Model model) {
        model.addAttribute("profile", null);
        model.addAttribute("sports", EMPTY_LIST);
        model.addAttribute("districts", EMPTY_LIST);
        model.addAttribute("error", null);
        model.addAttribute("message", null);
        return "mypage/profile";
    }

    /* ------------------------------------------------------------------
     * 매칭 화면(목록/카드/상세/신청)은 MatchController가 담당한다.
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------
     * 팀/클럽 화면: 목록·상세·관리 탭을 렌더링하며 클럽 데이터는 아직 조회하지 않는다.
     * ------------------------------------------------------------------ */

    /** 팀 목록 화면을 렌더링한다. */
    @GetMapping("/teams")
    public String teams(Model model) {
        model.addAttribute("sportTabs", EMPTY_LIST);
        model.addAttribute("statistics", EMPTY_LIST);
        // 필터 객체가 아직 없어 템플릿 기본 필터 UI를 사용한다.
        model.addAttribute("filters", null);
        model.addAttribute("districts", EMPTY_LIST);
        model.addAttribute("resultCount", 0);
        model.addAttribute("clubs", EMPTY_LIST);
        return "teams/list";
    }

    /** 팀 상세 화면을 렌더링한다. 클럽을 조회하지 않아 club은 null이다. */
    @GetMapping("/teams/{clubId}")
    public String teamDetail(@PathVariable long clubId, Model model) {
        model.addAttribute("club", null);
        return "teams/detail";
    }

    /** 팀 관리 화면을 렌더링한다. 기본 탭은 team이며 권한 확인·소유 클럽 조회는 아직 없다. */
    @GetMapping("/teams/manage")
    public String teamManage(Model model) {
        model.addAttribute("tab", "team");
        model.addAttribute("managedClubs", EMPTY_LIST);
        model.addAttribute("managedClub", null);
        model.addAttribute("sports", EMPTY_LIST);
        model.addAttribute("districts", EMPTY_LIST);
        return "teams/manage";
    }

    /* ------------------------------------------------------------------
     * 관리자 화면: 공통 셸(addAdminShell) 위에 화면별 빈 모델을 얹는다. 관리자 인증은 아직 없다.
     * ------------------------------------------------------------------ */

    /** 시설 관리 화면을 렌더링한다. 여러 탭이 쓰는 속성을 빈 값으로 초기화하며 기본 보기는 dashboard이다. */
    @GetMapping("/admin/facility")
    public String adminFacility(Model model) {
        addAdminShell(model);
        // view는 서버 상태가 아니라 뷰의 기본 선택값이다.
        model.addAttribute("view", "dashboard");
        model.addAttribute("scopeName", null);
        model.addAttribute("todayLabel", null);
        model.addAttribute("chartCollectedAt", null);
        model.addAttribute("dashboard", null);
        model.addAttribute("facilities", EMPTY_LIST);
        model.addAttribute("selectedFacility", null);
        model.addAttribute("weeklyHours", EMPTY_LIST);
        model.addAttribute("hourOptions", EMPTY_LIST);
        model.addAttribute("calendar", null);
        model.addAttribute("sports", EMPTY_LIST);
        model.addAttribute("conditions", null);
        model.addAttribute("closures", EMPTY_LIST);
        model.addAttribute("matches", EMPTY_LIST);
        model.addAttribute("bookingFilters", null);
        model.addAttribute("stats", null);
        model.addAttribute("requestMetrics", null);
        model.addAttribute("requestFilters", null);
        model.addAttribute("requests", EMPTY_LIST);
        model.addAttribute("maintenanceMetrics", null);
        model.addAttribute("maintenanceFilters", null);
        model.addAttribute("maintenance", EMPTY_LIST);
        model.addAttribute("notices", EMPTY_LIST);
        model.addAttribute("rules", EMPTY_LIST);
        model.addAttribute("roomEditorHourSlots", EMPTY_LIST);
        return "admin/facility";
    }

    /** 운영 관리 화면을 렌더링한다. 관리자 셸과 match=null만 전달한다. */
    @GetMapping("/admin/operations")
    public String adminOperations(Model model) {
        addAdminShell(model);
        model.addAttribute("match", null);
        return "admin/operations";
    }

    /** 시스템 관리 화면을 렌더링한다. 모니터링 탭 속성을 빈 값으로 초기화하며 수집 전이므로 allHealthy는 false이다. */
    @GetMapping("/admin/system")
    public String adminSystem(Model model) {
        addAdminShell(model);
        model.addAttribute("view", "dashboard");
        model.addAttribute("dashboard", map(
                "collectedAt", null,
                "apiStatus", null,
                "apiUptime", null,
                "cpuPercent", 0,
                "cpuPeak", null,
                "memoryPercent", 0,
                "memoryCapacity", null,
                "diskPercent", 0,
                "recentErrorCount", 0,
                "recentErrorAttention", null,
                // 상태를 확인하지 않았으므로 전체 정상(true)으로 표시하지 않는다.
                "allHealthy", false,
                "bars", EMPTY_LIST,
                "services", EMPTY_LIST,
                "recentErrors", EMPTY_LIST,
                "recentReports", EMPTY_LIST));
        model.addAttribute("errorMetrics", null);
        model.addAttribute("errorFilters", null);
        model.addAttribute("errors", EMPTY_LIST);
        model.addAttribute("reportMetrics", null);
        model.addAttribute("reportFilters", null);
        model.addAttribute("reports", EMPTY_LIST);
        model.addAttribute("reportPagination", null);
        model.addAttribute("penaltyMetrics", null);
        model.addAttribute("penaltyFilters", null);
        model.addAttribute("penalties", EMPTY_LIST);
        model.addAttribute("userMetrics", null);
        model.addAttribute("userFilters", null);
        model.addAttribute("users", EMPTY_LIST);
        model.addAttribute("userPagination", null);
        model.addAttribute("cmsNotices", EMPTY_LIST);
        model.addAttribute("cmsFaqs", EMPTY_LIST);
        model.addAttribute("cmsTerms", EMPTY_LIST);
        model.addAttribute("settings", null);
        model.addAttribute("auditFilters", null);
        model.addAttribute("auditLogs", EMPTY_LIST);
        model.addAttribute("auditPagination", null);
        return "admin/system";
    }

    /** 관리자 화면 공통 셸 속성을 구성한다. 관리자 인증이 없어 adminUser는 null이고 알림도 비어 있다. */
    private void addAdminShell(Model model) {
        model.addAttribute("adminUser", null);
        model.addAttribute("adminNotifications", EMPTY_LIST);
        model.addAttribute("unreadAdminNotificationCount", 0);
        model.addAttribute("adminNotificationSummary", "");
    }

    /**
     * 키/값을 번갈아 받아 순서를 보존한 맵을 만든다.
     *
     * <p>인자는 반드시 쌍으로 전달해야 하며, 홀수이면 {@link ArrayIndexOutOfBoundsException},
     * 키가 {@link String}이 아니면 {@link ClassCastException}이 발생한다.</p>
     */
    private static Map<String, Object> map(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put((String) entries[i], entries[i + 1]);
        }
        return values;
    }
}
