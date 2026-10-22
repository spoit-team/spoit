package co.kr.spoit.match.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import co.kr.spoit.match.dto.MatchListView;
import co.kr.spoit.match.dto.MatchSearchCondition;
import co.kr.spoit.match.service.MatchService;
import lombok.RequiredArgsConstructor;

/**
 * 매칭(=시설 예약) 화면 컨트롤러.
 *
 * <p>CorePageController에서 분리했다. RES-01 목록/카드는 실제 조회,
 * 상세·신청은 RES-02/03 구현 전까지 기존 빈 모델을 유지한다.</p>
 */
@Controller
@RequestMapping("/matches")
@RequiredArgsConstructor
public class MatchController {

    private static final String LIST_PATH = "/matches";
    private static final String CARDS_PATH = "/matches/cards";

    private final MatchService matchService;

    /** RES-01 목록형 */
    @GetMapping
    public String list(@ModelAttribute("search") MatchSearchCondition search,
                       BindingResult bindingResult,
                       Model model) {
        addListModel(search, LIST_PATH, model);
        return "matches/list";
    }

    /** RES-01 카드형 (목록과 같은 모델) */
    @GetMapping("/cards")
    public String cards(@ModelAttribute("search") MatchSearchCondition search,
                        BindingResult bindingResult,
                        Model model) {
        addListModel(search, CARDS_PATH, model);
        return "matches/cards";
    }

    /** RES-02에서 구현 예정 */
    @GetMapping("/{matchId}")
    public String detail(@PathVariable long matchId, Model model) {
        model.addAttribute("unavailableMessage", "매칭방 정보를 찾을 수 없습니다.");
        model.addAttribute("match", null);
        return "matches/detail";
    }

    /** RES-03/04에서 구현 예정 */
    @GetMapping("/{matchId}/apply")
    public String apply(@PathVariable long matchId, Model model) {
        model.addAttribute("matchId", matchId);
        model.addAttribute("completed", false);
        model.addAttribute("unavailableMessage", "신청 내용을 확인할 수 없습니다.");
        model.addAttribute("match", null);
        model.addAttribute("selection", null);
        model.addAttribute("canReserve", false);
        model.addAttribute("capacityNotice", "");
        model.addAttribute("error", null);
        model.addAttribute("feeTotalText", "");
        model.addAttribute("feeSummaryNote", "");
        model.addAttribute("completion", null);
        return "matches/join";
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