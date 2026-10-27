package co.kr.spoit.club.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.dto.ClubCreateForm;
import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.member.domain.MemberPrincipal;

@Controller
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @GetMapping("/teams")
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

    @GetMapping("/teams/{clubId}")
    public String detail(@PathVariable("clubId") Long clubId, Model model) {
        model.addAttribute("club", clubService.findClubDetail(clubId));
        return "teams/detail";
    }

    @GetMapping("/teams/manage")
    public String teamManage(Model model) {
        model.addAttribute("tab", "team");
        model.addAttribute("managedClubs", List.of());
        model.addAttribute("managedClub", null);
        model.addAttribute("sports", clubService.findSportOptions());
        model.addAttribute("districts", clubService.findRegionOptions());
        return "teams/manage";
    }

    @PostMapping("/teams")
    public String create(ClubCreateForm form, BindingResult bindingResult,
            @AuthenticationPrincipal MemberPrincipal principal, RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", "입력 정보를 확인해 주세요.");
            return "redirect:/teams/manage";
        }

        Long clubId;
        try {
            clubId = clubService.createClub(principal.getUserId(), form);
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/teams/manage";
        }

        redirectAttributes.addFlashAttribute("message", "팀을 만들었습니다.");
        return "redirect:/teams/" + clubId;
    }

}
