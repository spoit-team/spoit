package co.kr.spoit.mat.controller;

import co.kr.spoit.mat.dto.MatchResultResponse;
import co.kr.spoit.mat.service.MatchResultService;
import co.kr.spoit.member.domain.MemberPrincipal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 노쇼 경기 레이팅 제외 API. 역할 확인은 SecurityConfig의 /admin/operations/** 규칙이 맡고,
 * 담당 시설 여부는 서비스에서 확인한다.
 */
@RestController
@RequestMapping("/admin/operations/matches")
public class RatingExclusionController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MatchResultService matchResultService;

    public RatingExclusionController(MatchResultService matchResultService) {
        this.matchResultService = matchResultService;
    }

    @PostMapping("/{matchId}/rating-exclusion")
    public ResponseEntity<MatchResultResponse> exclude(@PathVariable("matchId") long matchId,
                                                       @AuthenticationPrincipal MemberPrincipal principal) {
        MatchResultResponse response = matchResultService.excludeRating(matchId, principal.getUserId(),
                LocalDateTime.now(ZONE));
        HttpStatus status = switch (response.outcome()) {
            case EXCLUDED -> HttpStatus.OK;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(response);
    }
}
