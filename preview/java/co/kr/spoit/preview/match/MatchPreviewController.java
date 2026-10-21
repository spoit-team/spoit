package co.kr.spoit.preview.match;

import co.kr.spoit.preview.match.MatchDetailModels.MatchDetailPage;
import co.kr.spoit.preview.match.MatchJoinModels.MatchJoinPage;
import co.kr.spoit.preview.match.MatchListModels.MatchListPage;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Preview-only match screens. Every handler returns fixture data and no POST
 * endpoint is exposed, so no reservation state can be changed from these pages.
 */
@Controller
public class MatchPreviewController {

    @GetMapping("/matches")
    public String list(Model model) {
        applyListModel(model, MatchPreviewFixtures.listPage());
        return "matches/list";
    }

    @GetMapping("/matches/cards")
    public String cards(Model model) {
        applyListModel(model, MatchPreviewFixtures.listPage());
        return "matches/cards";
    }

    @GetMapping("/matches/{matchId}")
    public String detail(@PathVariable long matchId, Model model) {
        MatchDetailPage page = MatchPreviewFixtures.detailPage(matchId);
        model.addAttribute("unavailableMessage", page.getUnavailableMessage());
        model.addAttribute("match", page.getMatch());
        return "matches/detail";
    }

    @GetMapping("/matches/{matchId}/apply")
    public String apply(@PathVariable long matchId, Model model) {
        MatchJoinPage page = MatchPreviewFixtures.joinPage(matchId);
        model.addAttribute("matchId", page.getMatchId());
        model.addAttribute("completed", page.isCompleted());
        model.addAttribute("unavailableMessage", page.getUnavailableMessage());
        model.addAttribute("match", page.getMatch());
        model.addAttribute("selection", page.getSelection());
        model.addAttribute("canReserve", page.isCanReserve());
        model.addAttribute("capacityNotice", page.getCapacityNotice());
        model.addAttribute("error", page.getError());
        model.addAttribute("feeTotalText", page.getFeeTotalText());
        model.addAttribute("feeSummaryNote", page.getFeeSummaryNote());
        model.addAttribute("completion", page.getCompletion());
        return "matches/join";
    }

    private void applyListModel(Model model, MatchListPage page) {
        model.addAttribute("search", page.getSearch());
        model.addAttribute("sports", page.getSports());
        model.addAttribute("districts", page.getDistricts());
        model.addAttribute("dates", page.getDates());
        model.addAttribute("hourOptions", page.getHourOptions());
        model.addAttribute("selectedHours", page.getSelectedHours());
        model.addAttribute("selectedHoursLabel", page.getSelectedHoursLabel());
        model.addAttribute("selectedSportName", page.getSelectedSportName());
        model.addAttribute("sportImageUrl", page.getSportImageUrl());
        model.addAttribute("totalRoomCount", page.getTotalRoomCount());
        model.addAttribute("roomCount", page.getRoomCount());
        model.addAttribute("recruitingCount", page.getRecruitingCount());
        model.addAttribute("matchedCount", page.getMatchedCount());
        model.addAttribute("activeFilters", page.getActiveFilters());
        model.addAttribute("matches", page.getMatches());
    }
}
