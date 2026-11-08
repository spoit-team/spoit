package co.kr.spoit.admin.controller;

import java.util.Collections;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import co.kr.spoit.admin.common.AdminShellModel;
import co.kr.spoit.admin.dto.SystemDashboard;
import co.kr.spoit.admin.service.SystemMonitoringService;

/**
 * 시스템 관리자 화면 템플릿을 렌더링하는 페이지 컨트롤러이다.
 *
 * <p>모니터링 dashboard는 {@link SystemMonitoringService}가 실제 측정치로 구성한 모델을 받아
 * 전달만 한다. 페이지 전체 재로드 없이 최신 상태를 갱신할 수 있도록 같은 dashboard를 JSON으로
 * 돌려주는 POST endpoint({@code /admin/system/monitoring})도 함께 제공한다. 나머지 화면
 * (신고·사용자·CMS·설정·감사)은 서비스/DB 연동 전이므로 모델 속성을 빈 값·기본값으로 채워
 * "데이터가 없는 현재 상태"를 표현한다. 시설 관리 화면은
 * {@code facility.controller.FacilityAdminController}, 매칭 참가자 운영 화면은
 * {@code match.controller.MatchOperationsController}가 담당한다.</p>
 */
@Controller
public class SystemAdminController {

    /** 여러 화면이 공유하는 불변 빈 목록이다. */
    private static final List<Object> EMPTY_LIST = Collections.emptyList();

    private final SystemMonitoringService systemMonitoringService;

    public SystemAdminController(SystemMonitoringService systemMonitoringService) {
        this.systemMonitoringService = systemMonitoringService;
    }

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

    /** 시스템 관리 화면을 렌더링한다. 모니터링 탭은 실제 측정치로 구성한 dashboard 모델을 사용한다. */
    @GetMapping("/admin/system")
    public String adminSystem(Model model) {
        AdminShellModel.apply(model);
        model.addAttribute("view", "dashboard");
        model.addAttribute("dashboard", systemMonitoringService.currentDashboard());
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
     * 모니터링 dashboard 최신 상태를 JSON으로 돌려준다.
     *
     * <p>페이지 전체 재로드 없이 15초마다 갱신하는 프런트가 호출한다. 이전 측정치가 캐시에서
     * 재사용되지 않도록 no-store 계열 헤더만 붙인다. 인증·역할 검사는 SecurityConfig의
     * {@code /admin/system/**} 규칙이, 요청 위조 방지는 기존 CSRF 필터가 그대로 담당한다.</p>
     */
    @PostMapping("/admin/system/monitoring")
    @ResponseBody
    public ResponseEntity<SystemDashboard> monitoring() {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store, no-cache, must-revalidate")
                .header("Pragma", "no-cache")
                .body(systemMonitoringService.currentDashboard());
    }
}
