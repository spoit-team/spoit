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

    // 동호회 상세 화면(없는 동호회면 NULL)
    @GetMapping("/{clubId}")
    public String detail(@PathVariable("clubId") Long clubId, Model model) {
        log.info("동호회 상세 조회: clubId={}", clubId);
        model.addAttribute("club", clubService.findClubDetail(clubId));
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

        // 선택 버튼 강조 동호회
        Long activeClubId = managedClub == null ? null : managedClub.getClubId();

        model.addAttribute("managedClubs", clubService.findMyClubs(userId, activeClubId));
        model.addAttribute("managedClub", managedClub);
        // 내 동호회, 동호회원만 허용(가입 신청 미구현)
        model.addAttribute("tab", "members".equals(tab) ? "members" : "team");
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

}
