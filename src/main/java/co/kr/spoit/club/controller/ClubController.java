package co.kr.spoit.club.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import co.kr.spoit.club.dto.ClubCardResponse;
import co.kr.spoit.club.service.ClubService;

@Controller 
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    @GetMapping ("/teams")
    public String list (Model model) {
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
}
