package co.kr.spoit.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 시스템 관리자 화면 템플릿을 렌더링하는 GET 전용 페이지 컨트롤러이다.
 *
 * <p>서비스/DB 연동 전인 화면은 모델 속성을 빈 값·기본값으로 채워 "데이터가 없는 현재 상태"를
 * 표현한다. 시설 관리 화면은 {@code facility.controller.FacilityAdminController}, 매칭 참가자
 * 운영 화면은 {@code match.controller.MatchOperationsController}가 담당한다.</p>
 */
@Controller
public class CorePageController {

    /** 여러 화면이 공유하는 불변 빈 목록이다. */
    private static final List<Object> EMPTY_LIST = Collections.emptyList();

    /* ------------------------------------------------------------------
     * 인증 화면: 로그인은 MemberAuthController, 회원가입은 MemberSignupController가 담당한다.
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------
     * 마이페이지: 요약·프로필 수정 화면 모두 member.controller.MyPageController가 담당한다.
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------
     * 매칭 화면(목록/카드/상세/신청)은 MatchController가 담당한다.
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------
     * 시설 관리자 화면(시설 관리·운영)은 FacilityAdminController,
     * MatchOperationsController가 담당한다.
     * ------------------------------------------------------------------ */

    /* ------------------------------------------------------------------
     * 시스템 관리자 화면: 공통 셸(AdminShellModel) 위에 화면별 빈 모델을 얹는다.
     * ------------------------------------------------------------------ */

    /** 시스템 관리 화면을 렌더링한다. 모니터링 탭 속성을 빈 값으로 초기화하며 수집 전이므로 allHealthy는 false이다. */
    @GetMapping("/admin/system")
    public String adminSystem(Model model) {
        AdminShellModel.apply(model);
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
