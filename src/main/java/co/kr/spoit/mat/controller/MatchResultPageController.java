package co.kr.spoit.mat.controller;

import co.kr.spoit.mat.dto.MatchResultPage;
import co.kr.spoit.mat.service.MatchResultPageService;
import co.kr.spoit.member.domain.MemberPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/** 팀장의 경기 결과 제출 화면. 결과 조회·제출은 화면의 스크립트가 MatchResultController API로 한다. */
@Controller
@RequestMapping("/mypage/matches")
public class MatchResultPageController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MatchResultPageService pageService;

    public MatchResultPageController(MatchResultPageService pageService) {
        this.pageService = pageService;
    }

    @GetMapping("/{matchId}/result-form")
    public String resultForm(@PathVariable long matchId,
                             @AuthenticationPrincipal MemberPrincipal principal,
                             Model model,
                             HttpServletResponse response) {
        Optional<MatchResultPage> page = pageService.getPage(matchId, principal.getUserId(),
                LocalDateTime.now(ZONE));
        if (page.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        model.addAttribute("page", page.orElse(null));
        return "mypage/match-result";
    }
}
