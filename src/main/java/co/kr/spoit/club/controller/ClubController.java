package co.kr.spoit.club.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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
}
