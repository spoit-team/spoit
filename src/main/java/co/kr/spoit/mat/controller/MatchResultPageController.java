package co.kr.spoit.mat.controller;

import co.kr.spoit.mat.service.MatchResultService;
import co.kr.spoit.member.domain.MemberPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * 확정 참가자의 경기 결과 화면. 화면 자체는 결과 요약 API를 읽어 그리고, 진입 시점의 권한 검증은
 * 같은 요약 조회 서비스를 재사용해 JSON API와 판정이 어긋나지 않게 한다.
 *
 * <p>팀장 전용 결과 조회·제출 JSON API({@code /result})와 제출 권한은 이 컨트롤러가 건드리지 않는다.
 * 경기가 없으면 404, 확정 참가자가 아니면 서비스의 {@code AccessDeniedException}이 403으로 변환된다.</p>
 */
@Controller
@RequestMapping("/mypage/matches")
public class MatchResultPageController {

    private final MatchResultService matchResultService;

    public MatchResultPageController(MatchResultService matchResultService) {
        this.matchResultService = matchResultService;
    }

    @GetMapping("/{matchId}/result-form")
    public String resultForm(@PathVariable("matchId") long matchId,
                             @AuthenticationPrincipal MemberPrincipal principal,
                             Model model) {
        matchResultService.getSummary(matchId, principal.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "경기 정보를 찾을 수 없습니다."));
        model.addAttribute("matchId", matchId);
        return "mypage/match-result";
    }
}
