package co.kr.spoit.report.controller;

import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import co.kr.spoit.admin.common.AdminShellModel;
import co.kr.spoit.global.common.FlashFeedback;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.report.domain.ReportCategory;
import co.kr.spoit.report.dto.FacilityReportPage;
import co.kr.spoit.report.service.FacilityReportService;
import co.kr.spoit.report.service.ReportAccessDeniedException;

/**
 * 시설 관리자 신고 화면의 진입점이다.
 *
 * <p>{@code GET /admin/facility/reports}는 담당 활성 시설의 이용 완료 경기·확정 참가자·본인이
 * 접수한 신고 목록을 offset 페이지로 모아 {@code admin/report-facility} 뷰로 넘긴다. {@code POST}는
 * 신고를 접수하고 같은 화면으로 되돌린다(Post/Redirect/Get). 관리자 식별자는 인증 주체에서만 얻는다.</p>
 *
 * <p>모델 계약(UI 고정): {@code matches}(matchId, facilityName, title, startAt),
 * {@code participants}(userId, nickname), {@code reports}(reportId, matchId, facilityName,
 * matchTitle, targetName, category, status, createdAt, resolution), {@code selectedMatchId},
 * {@code selectedTargetUserId}, {@code selectedReport}(nullable), {@code matchPage},
 * {@code hasNextMatches}, {@code reportPage}, {@code hasNextReports}, 공용 안내 {@code message}/{@code errorTitle}·{@code error}
 * flash. 쿼리 파라미터는 {@code matchId}·{@code targetUserId}·{@code reportId}·{@code matchPage}·
 * {@code reportPage} 모두 선택이다. 페이지는 0부터 시작하며 잘못된 입력은 정규화한다.</p>
 */
@Controller
public class FacilityReportController {

    private static final String VIEW = "admin/report-facility";
    private static final String REDIRECT_LOGIN = "redirect:/login";
    /** 접수 실패 안내에 덧붙이는 문구. 작성하던 내용은 flash로 양식에 되돌려 준다. */
    public static final String KEPT_INPUT_NOTICE = "작성한 내용과 선택은 양식에 그대로 남아 있습니다.";

    private final FacilityReportService facilityReportService;

    public FacilityReportController(FacilityReportService facilityReportService) {
        this.facilityReportService = facilityReportService;
    }

    /** 신고 작성·조회 화면을 렌더링한다. 모든 조회는 인증 주체의 담당 시설 범위로 제한된다. */
    @GetMapping("/admin/facility/reports")
    public String facilityReports(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "matchId", required = false) Long matchId,
            @RequestParam(name = "targetUserId", required = false) Long targetUserId,
            @RequestParam(name = "reportId", required = false) Long reportId,
            @RequestParam(name = "matchPage", required = false) String matchPage,
            @RequestParam(name = "reportPage", required = false) String reportPage,
            Model model) {
        AdminShellModel.apply(model);
        Long userId = principal == null ? null : principal.getUserId();
        FacilityReportPage page = facilityReportService.loadPage(userId, matchId, targetUserId, reportId,
                ReportPageParameter.parse(matchPage), ReportPageParameter.parse(reportPage));

        model.addAttribute("matches", page.getMatches());
        model.addAttribute("participants", page.getParticipants());
        model.addAttribute("reports", page.getReports());
        model.addAttribute("selectedMatchId", page.getSelectedMatchId());
        model.addAttribute("selectedTargetUserId", page.getSelectedTargetUserId());
        model.addAttribute("selectedReport", page.getSelectedReport());
        model.addAttribute("matchPage", page.getMatchPage());
        model.addAttribute("hasNextMatches", page.isHasNextMatches());
        model.addAttribute("reportPage", page.getReportPage());
        model.addAttribute("hasNextReports", page.isHasNextReports());
        return VIEW;
    }

    /**
     * 신고를 접수하고 화면으로 되돌린다(PRG).
     *
     * <p>성공하면 생성된 신고 번호로 본인 상세를 열고, 실패하면 검증 대상({@code matchId},
     * {@code targetUserId})과 페이지를 리다이렉트 쿼리로 보존한다. 작성한 {@code summary}·{@code category}는
     * URL에 싣지 않고, 입력 상한으로 자른 flash 모델 {@code submittedSummary}/{@code submittedCategory}로만
     * 되돌려 준다. 출력 escaping은 템플릿({@code th:text})이 담당하며, 이 메서드는 민감 텍스트를 로그로
     * 남기지 않는다.</p>
     *
     * <p>리다이렉트 값은 모두 서버가 정규화한 값만 쓴다. 클라이언트가 보낸 reporter·manager 식별자는
     * 신뢰하지 않고 인증 주체 ID만 서비스에 넘긴다.</p>
     */
    @PostMapping("/admin/facility/reports")
    public String submitReport(@AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(name = "matchId", required = false) Long matchId,
            @RequestParam(name = "targetUserId", required = false) Long targetUserId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "summary", required = false) String summary,
            @RequestParam(name = "matchPage", required = false) String matchPage,
            @RequestParam(name = "reportPage", required = false) String reportPage,
            RedirectAttributes redirect) {
        if (principal == null) {
            return REDIRECT_LOGIN;
        }
        int safeMatchPage = ReportPageParameter.parse(matchPage);
        int safeReportPage = ReportPageParameter.parse(reportPage);
        try {
            long reportId = facilityReportService.submit(principal.getUserId(), matchId, targetUserId,
                    category, summary);
            FlashFeedback.success(redirect, "신고가 접수되었습니다.");
            // 성공하면 방금 만든 본인 신고 상세로 이동해 처리 상태를 바로 확인하게 한다.
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/facility/reports")
                    .queryParam("reportId", reportId)
                    .queryParam("matchPage", safeMatchPage)
                    .queryParam("reportPage", safeReportPage);
            return "redirect:" + builder.build().encode().toUriString() + "#report-selected";
        } catch (IllegalArgumentException | ReportAccessDeniedException exception) {
            // 대상 조건 위반은 권한·상태 안내로 처리하고, 존재 여부를 404로 흘리지 않는다.
            // 작성 내용은 URL이 아니라 길이 제한 flash로만 보존한다. escaping은 템플릿이 맡는다.
            boolean kept = addSubmittedFlash(redirect, summary, category);
            String reason = exception.getMessage();
            FlashFeedback.failure(redirect, "신고를 접수할 수 없습니다",
                    kept && reason != null && !reason.isBlank() ? reason + "\n" + KEPT_INPUT_NOTICE
                            : reason);
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin/facility/reports");
        if (matchId != null && matchId > 0) {
            builder.queryParam("matchId", matchId);
        }
        if (targetUserId != null && targetUserId > 0) {
            builder.queryParam("targetUserId", targetUserId);
        }
        builder.queryParam("matchPage", safeMatchPage);
        builder.queryParam("reportPage", safeReportPage);
        return "redirect:" + builder.build().encode().toUriString();
    }

    /**
     * 실패 시 작성 내용을 flash로 되돌린다. 신고 원문은 입력 상한에서 자르고, 유형은 허용 값으로 해석되는
     * 경우에만 남긴다(입력 유효성과 무관하게 임의 문자열을 화면에 되돌리지 않는다). 빈 값은 모델에 넣지
     * 않아 템플릿이 빈 상자를 그리지 않게 한다.
     */
    private static boolean addSubmittedFlash(RedirectAttributes redirect, String summary, String category) {
        boolean kept = false;
        String summaryValue = limitSummary(summary);
        if (!summaryValue.isEmpty()) {
            redirect.addFlashAttribute("submittedSummary", summaryValue);
            kept = true;
        }
        // 허용 유형만 되돌린다. 알 수 없는 값은 화면에 반사하지 않는다.
        Optional<ReportCategory> submittedCategory = ReportCategory.from(category);
        submittedCategory.ifPresent(value -> redirect.addFlashAttribute("submittedCategory", value.dbValue()));
        return kept || submittedCategory.isPresent();
    }

    /** 신고 원문을 입력 상한으로 자른다. null·공백은 빈 문자열이며, 로그로 남기지 않는다. */
    static String limitSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return "";
        }
        String value = summary.strip();
        return value.length() > FacilityReportService.SUMMARY_MAX_LENGTH
                ? value.substring(0, FacilityReportService.SUMMARY_MAX_LENGTH)
                : value;
    }
}
