package co.kr.spoit.club.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubCreateForm;
import co.kr.spoit.club.dto.ClubManageResponse;
import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.member.domain.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/teams")
@RequiredArgsConstructor
@Slf4j
public class ClubController {

    private final ClubService clubService;

    // 동호회 목록 화면(검색,필터,통계 미구현)
    @GetMapping
    public String list(Model model) {
        List<ClubCardResponse> clubs = clubService.findClubCards();
        model.addAttribute("clubs", clubs);
        model.addAttribute("resultCount", clubs.size());
        model.addAttribute("sportTabs", List.of());
        model.addAttribute("statistics", List.of());
        model.addAttribute("districts", List.of());
        model.addAttribute("filters", null);
        return "teams/list";
    }

    // 동호회 상세 화면
    @GetMapping("/{clubId}")
    public String detail(@PathVariable("clubId") Long clubId,
            @AuthenticationPrincipal MemberPrincipal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Long userId = principal.getUserId();
        log.info("동호회 상세 조회: clubId={}, userId={}", clubId, userId);
        model.addAttribute("club", clubService.findClubDetail(clubId, userId));
        return "teams/detail";
    }

    // 동호회 관리 화면: 내 동호회 목록 + 선택한 동호회
    @GetMapping("/manage")
    public String teamManage(@RequestParam(value = "clubId", required = false) Long clubId,
            @RequestParam(value = "tab", defaultValue = "team") String tab,
            @AuthenticationPrincipal MemberPrincipal principal,
            Model model, RedirectAttributes redirectAttributes) {
        // SecurityConfig 이후 재확인
        if (principal == null) {
            return "redirect:/login";
        }
        Long userId = principal.getUserId();
        log.info("동호회 관리 화면 조회: userId={}, clubId={}, tab={}", userId, clubId, tab);

        ClubManageResponse managedClub;
        try {
            managedClub = clubService.findManagedClub(userId, clubId);
        } catch (IllegalArgumentException exception) {
            // 내 동호회 아니면 번호 없이 돌아감
            log.warn("동호회 관리 거부: userId={}, clubId={}", userId, clubId);
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/teams/manage";
        }

        // 선택된 동호회 버튼 강조
        Long activeClubId = managedClub == null ? null : managedClub.getClubId();

        model.addAttribute("managedClubs", clubService.findMyClubs(userId, activeClubId));
        model.addAttribute("managedClub", managedClub);
        String activeTab = "team";
        if ("members".equals(tab) || "applications".equals(tab)) {
            activeTab = tab;
        }
        model.addAttribute("tab", activeTab);
        model.addAttribute("sports", clubService.findSportOptions());
        model.addAttribute("districts", clubService.findRegionOptions());
        return "teams/manage";
    }

    // 동호회 생성
    @PostMapping
    public String create(ClubCreateForm form, BindingResult bindingResult,
            @AuthenticationPrincipal MemberPrincipal principal, RedirectAttributes redirectAttributes) {
        log.info("동호회 생성 요청: {}", form);
        // SecurityConfig 이후 재확인
        if (principal == null) {
            return "redirect:/login";
        }
        // 형식이 맞지 않으면 관리 화면으로 보냄(숫자 칸에 글자가 오는 등)
        if (bindingResult.hasErrors()) {
            log.info("동호회 생성 입력 형식 오류: {}", bindingResult.getAllErrors());
            redirectAttributes.addFlashAttribute("error", "입력 정보를 확인해 주세요.");
            return "redirect:/teams/manage";
        }

        Long clubId;
        try {
            clubId = clubService.createClub(principal.getUserId(), form);
        } catch (ClubService.ClubNameExistException exception) {
            log.warn("동호회 생성 거부: 이름 중복 {}", form.getClubName());
            redirectAttributes.addFlashAttribute("error", "이미 사용 중인 이름입니다.");
            return "redirect:/teams/manage";
        } catch (IllegalArgumentException exception) {
            log.warn("동호회 생성 거부: {}", exception.getMessage());
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/teams/manage";
        }

        log.info("동호회 생성 완료: clubId={}", clubId);
        redirectAttributes.addFlashAttribute("message", "팀을 만들었습니다.");
        return "redirect:/teams/manage?clubId=" + clubId;
    }

    // 가입 신청: 결과는 상세 화면에서 보여줌
    @PostMapping("/{clubId}/applications")
    public String apply(@PathVariable("clubId") Long clubId,
            @RequestParam(value = "message", required = false) String message,
            @AuthenticationPrincipal MemberPrincipal principal, RedirectAttributes redirectAttributes) {
        Long userId = principal.getUserId();
        log.info("가입 신청 요청: clubId={}, userId={}", clubId, userId);

        try {
            clubService.applyJoin(userId, clubId, message);
        } catch (IllegalArgumentException exception) {
            // 상세 화면이 대화상자를 다시 열어 이유를 보여 줌
            log.warn("가입 신청 거부: clubId={}, userId={}, {}", clubId, userId, exception.getMessage());
            redirectAttributes.addFlashAttribute("applicationError", exception.getMessage());
            return "redirect:/teams/" + clubId;
        }

        redirectAttributes.addFlashAttribute("message", "가입 신청을 보냈습니다.");
        return "redirect:/teams/" + clubId;
    }

    // 가입 신청 승인
    @PostMapping("/{clubId}/applications/{joinRequestId}/approve")
    public String approve(@PathVariable("clubId") Long clubId, @PathVariable("joinRequestId") Long joinRequestId,
            @AuthenticationPrincipal MemberPrincipal principal, RedirectAttributes redirectAttributes) {
        Long userId = principal.getUserId();
        log.info("가입 신청 승인 요청: clubId={}, requestId={}, userId={}", clubId, joinRequestId, userId);
        try {
            clubService.approveJoin(userId, clubId, joinRequestId);
            redirectAttributes.addFlashAttribute("message", "가입 신청을 승인했습니다.");
        } catch (IllegalArgumentException exception) {
            log.warn("가입 신청 승인 거부: clubId={}, requestId={}, {}", clubId, joinRequestId, exception.getMessage());
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/teams/manage?clubId=" + clubId + "&tab=applications";
    }

    // 가입 신청 거절
    @PostMapping("/{clubId}/applications/{joinRequestId}/reject")
    public String reject(@PathVariable("clubId") Long clubId, @PathVariable("joinRequestId") Long joinRequestId,
            @AuthenticationPrincipal MemberPrincipal principal, RedirectAttributes redirectAttributes) {
        Long userId = principal.getUserId();
        log.info("가입 신청 거절 요청: clubId={}, requestId={}, userId={}", clubId, joinRequestId, userId);
        try {
            clubService.rejectJoin(userId, clubId, joinRequestId);
            redirectAttributes.addFlashAttribute("message", "가입 신청을 거절했습니다.");
        } catch (IllegalArgumentException exception) {
            log.warn("가입 신청 거절 거부: clubId={}, requestId={}, {}", clubId, joinRequestId, exception.getMessage());
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/teams/manage?clubId=" + clubId + "&tab=applications";
    }
}
