package co.kr.spoit.match.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.kr.spoit.match.dto.JoinPage;
import co.kr.spoit.match.dto.MatchListView;
import co.kr.spoit.match.dto.MatchSearchCondition;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.match.service.MatchService;
import co.kr.spoit.member.domain.MemberPrincipal;
import lombok.RequiredArgsConstructor;

/**
 * 매칭(=시설 예약) 화면 컨트롤러.
 *
 * <p>CorePageController에서 분리했다. RES-01 검색(카드형)·RES-02 상세·RES-03 개인 신청·RES-04 팀 신청.
 * 신청 흐름: 상세 다이얼로그 → 신청 내용(STEP 1) → 신청 완료(STEP 3). 결제 안내(STEP 2)는
 * 무료 결제만 지원하는 동안 건너뛴다.</p>
 */
@Controller
@RequestMapping("/matches")
@RequiredArgsConstructor
public class MatchController {

    private static final String LIST_PATH = "/matches";

    private final MatchService matchService;
    private final MatchApplicationService applicationService;

    /** RES-01 매칭방 검색 (카드형으로 고정, 목록형은 쓰지 않는다) */
    @GetMapping
    public String list(@ModelAttribute("search") MatchSearchCondition search,
                       BindingResult bindingResult,
                       Model model) {
        addListModel(search, LIST_PATH, model);
        return "matches/cards";
    }

    /** RES-02 상세 (매치 참가하기 → 이 화면에서 참가 방식 선택) */
    @GetMapping("/{matchId}")
    public String detail(@PathVariable long matchId,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         CsrfToken csrfToken,
                         Model model) {
        // 로그인 회원에게만 선택 폼이 보이므로, 그때만 렌더링 전에 토큰을 확정한다.
        if (principal != null) {
            csrfToken.getToken();
        }
        Long userId = principal == null ? null : principal.getUserId();
        matchService.findDetail(matchId, userId).ifPresentOrElse(
                match -> model.addAttribute("match", match),
                () -> {
                    model.addAttribute("unavailableMessage", "목록으로 돌아가 다른 매칭방을 선택해 주세요.");
                    model.addAttribute("match", null);
                });
        return "matches/detail";
    }

    /**
     * RES-03·04 상세 다이얼로그의 참가 방식 선택 → 신청 내용 화면.
     * 팀 신청은 고른 동호회·팀원을 쿼리로 넘기고, 신청 내용 화면과 접수 때 서버가 다시 검증한다.
     */
    @PostMapping("/{matchId}/selection")
    public String select(@PathVariable long matchId,
                         @RequestParam(defaultValue = "individual") String applicationType,
                         @RequestParam(required = false) Long clubId,
                         @RequestParam(required = false) List<Long> clubMemberIds,
                         RedirectAttributes redirect) {
        if ("individual".equals(applicationType)) {
            return "redirect:/matches/{matchId}/apply";
        }
        if (!"team".equals(applicationType) || clubId == null) {
            return "redirect:/matches/{matchId}";
        }
        addTeamSelection(redirect, clubId, clubMemberIds);
        return "redirect:/matches/{matchId}/apply/team";
    }

    /** RES-03 신청 내용 화면 (STEP 1) */
    @GetMapping("/{matchId}/apply")
    public String apply(@PathVariable long matchId,
                        @AuthenticationPrincipal MemberPrincipal principal,
                        CsrfToken csrfToken,
                        Model model) {
        // 지연 생성되는 CSRF 토큰을 템플릿 렌더링 전에 세션에 저장해 둔다.
        csrfToken.getToken();
        model.addAttribute("matchId", matchId);
        model.addAttribute("completed", false);
        applicationService.prepareIndividual(matchId, principal.getUserId()).ifPresentOrElse(
                page -> addJoinModel(page, model),
                () -> addUnavailableModel(model));
        return "matches/join";
    }

    /** RES-03 신청 접수. 무료 결제만 지원하므로 결제 동의 없이 운영 규칙 동의만 확인한다. */
    @PostMapping("/{matchId}/apply")
    public String submit(@PathVariable long matchId,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         @RequestParam(defaultValue = "false") boolean agreeRules,
                         RedirectAttributes redirect) {
        if (!agreeRules) {
            redirect.addFlashAttribute("error", "매치 운영 규칙과 취소·노쇼 안내에 동의해 주세요.");
            return "redirect:/matches/{matchId}/apply";
        }
        try {
            String receiptNo = applicationService.applyIndividual(matchId, principal.getUserId());
            redirect.addAttribute("receiptNo", receiptNo);
            return "redirect:/matches/{matchId}/apply/complete";
        } catch (MatchApplyException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/matches/{matchId}/apply";
        }
    }

    /** RES-04 팀 신청 내용 화면 (STEP 1) */
    @GetMapping("/{matchId}/apply/team")
    public String applyTeam(@PathVariable long matchId,
                            @RequestParam long clubId,
                            @RequestParam(required = false) List<Long> clubMemberIds,
                            @AuthenticationPrincipal MemberPrincipal principal,
                            CsrfToken csrfToken,
                            Model model) {
        csrfToken.getToken();
        model.addAttribute("matchId", matchId);
        model.addAttribute("completed", false);
        applicationService.prepareTeam(matchId, principal.getUserId(), clubId, nullToEmpty(clubMemberIds))
                .ifPresentOrElse(
                        page -> addJoinModel(page, model),
                        () -> addUnavailableModel(model));
        return "matches/join";
    }

    /** RES-04 팀 신청 접수. 실패하면 같은 선택값으로 신청 내용 화면에 돌아가 사유를 보여 준다. */
    @PostMapping("/{matchId}/apply/team")
    public String submitTeam(@PathVariable long matchId,
                             @RequestParam long clubId,
                             @RequestParam(required = false) List<Long> clubMemberIds,
                             @AuthenticationPrincipal MemberPrincipal principal,
                             @RequestParam(defaultValue = "false") boolean agreeRules,
                             RedirectAttributes redirect) {
        List<Long> memberIds = nullToEmpty(clubMemberIds);
        if (!agreeRules) {
            redirect.addFlashAttribute("error", "매치 운영 규칙과 취소·노쇼 안내에 동의해 주세요.");
            addTeamSelection(redirect, clubId, memberIds);
            return "redirect:/matches/{matchId}/apply/team";
        }
        try {
            String receiptNo = applicationService.applyTeam(matchId, principal.getUserId(), clubId, memberIds);
            redirect.addAttribute("receiptNo", receiptNo);
            return "redirect:/matches/{matchId}/apply/complete";
        } catch (MatchApplyException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            addTeamSelection(redirect, clubId, memberIds);
            return "redirect:/matches/{matchId}/apply/team";
        }
    }

    private static void addTeamSelection(RedirectAttributes redirect, long clubId, List<Long> clubMemberIds) {
        redirect.addAttribute("clubId", clubId);
        if (clubMemberIds != null && !clubMemberIds.isEmpty()) {
            redirect.addAttribute("clubMemberIds", clubMemberIds);
        }
    }

    private static List<Long> nullToEmpty(List<Long> ids) {
        return ids == null ? List.of() : ids;
    }

    /** RES-03 신청 완료 (STEP 3, 새로고침해도 다시 접수되지 않는다) */
    @GetMapping("/{matchId}/apply/complete")
    public String complete(@PathVariable long matchId,
                           @RequestParam String receiptNo,
                           @AuthenticationPrincipal MemberPrincipal principal,
                           Model model) {
        model.addAttribute("matchId", matchId);
        applicationService.findCompletion(matchId, receiptNo, principal.getUserId()).ifPresentOrElse(
                page -> {
                    addJoinModel(page, model);
                    model.addAttribute("completed", true);
                },
                () -> {
                    model.addAttribute("completed", false);
                    addUnavailableModel(model);
                });
        return "matches/join";
    }

    /* error는 POST에서 넘어온 flash 값을 덮어쓰지 않도록 여기서 넣지 않는다. */
    private void addJoinModel(JoinPage page, Model model) {
        model.addAttribute("unavailableMessage", null);
        model.addAttribute("match", page.getMatch());
        model.addAttribute("selection", page.getSelection());
        model.addAttribute("canReserve", page.isCanReserve());
        model.addAttribute("capacityNotice", page.getCapacityNotice());
        model.addAttribute("feeTotalText", page.getFeeTotalText());
        model.addAttribute("feeSummaryNote", page.getFeeSummaryNote());
        model.addAttribute("completion", page.getCompletion());
    }

    private void addUnavailableModel(Model model) {
        model.addAttribute("unavailableMessage", "신청 내용을 확인할 수 없습니다.");
        model.addAttribute("match", null);
        model.addAttribute("selection", null);
        model.addAttribute("canReserve", false);
        model.addAttribute("capacityNotice", "");
        model.addAttribute("feeTotalText", "");
        model.addAttribute("feeSummaryNote", "");
        model.addAttribute("completion", null);
    }

    /*
     * BindingResult를 받아 두면 ?sportId=abc, ?regionId=전체 지역 같은 잘못된 값이 400 대신
     * null(전체)로 처리된다. 서비스에서 한 번 더 정규화한다.
     */
    private void addListModel(MatchSearchCondition search, String basePath, Model model) {
        MatchListView view = matchService.search(search, basePath);

        model.addAttribute("sports", view.getSports());
        model.addAttribute("districts", view.getDistricts());
        model.addAttribute("dates", view.getDates());
        model.addAttribute("hourOptions", view.getHourOptions());
        model.addAttribute("selectedHours", view.getSelectedHours());
        model.addAttribute("selectedHoursLabel", view.getSelectedHoursLabel());
        model.addAttribute("selectedSportName", view.getSelectedSportName());
        model.addAttribute("sportImageUrl", view.getSportImageUrl());
        model.addAttribute("totalRoomCount", view.getTotalRoomCount());
        model.addAttribute("roomCount", view.getRoomCount());
        model.addAttribute("recruitingCount", view.getRecruitingCount());
        model.addAttribute("matchedCount", view.getMatchedCount());
        model.addAttribute("activeFilters", view.getActiveFilters());
        model.addAttribute("matches", view.getMatches());
    }
}