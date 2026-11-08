package co.kr.spoit.mat.controller;

import co.kr.spoit.mat.dto.MatchResultResponse;
import co.kr.spoit.mat.dto.MatchResultSummary;
import co.kr.spoit.mat.dto.MatchResultView;
import co.kr.spoit.mat.dto.ResultSubmitRequest;
import co.kr.spoit.mat.service.MatchResultService;
import co.kr.spoit.member.domain.MemberPrincipal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀장의 경기 결과 조회·제출 API. 로그인 확인은 SecurityConfig의 /mypage/** 규칙이 맡고,
 * 팀장 여부는 서비스에서 확인한다.
 */
@RestController
@RequestMapping("/mypage/matches")
public class MatchResultController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MatchResultService matchResultService;

    public MatchResultController(MatchResultService matchResultService) {
        this.matchResultService = matchResultService;
    }

    @GetMapping("/{matchId}/result")
    public ResponseEntity<?> view(@PathVariable("matchId") long matchId,
                                  @AuthenticationPrincipal MemberPrincipal principal) {
        Optional<MatchResultView> view = matchResultService.getView(matchId, principal.getUserId(),
                LocalDateTime.now(ZONE));
        if (view.isEmpty()) {
            return toResponse(MatchResultResponse.notFound());
        }
        return ResponseEntity.ok(view.get());
    }

    /** 확정 참가자 전용 결과 요약. 팀장 전용 {@code GET /result}와 달리 상대 제출 결과를 담지 않는다. */
    @GetMapping("/{matchId}/result/summary")
    public ResponseEntity<?> summary(@PathVariable long matchId,
                                     @AuthenticationPrincipal MemberPrincipal principal) {
        Optional<MatchResultSummary> summary = matchResultService.getSummary(matchId, principal.getUserId());
        if (summary.isEmpty()) {
            return toResponse(MatchResultResponse.notFound());
        }
        return ResponseEntity.ok(summary.get());
    }

    @PostMapping("/{matchId}/result")
    public ResponseEntity<MatchResultResponse> submit(@PathVariable("matchId") long matchId,
                                                      @RequestBody ResultSubmitRequest request,
                                                      @AuthenticationPrincipal MemberPrincipal principal) {
        return toResponse(matchResultService.submit(matchId, principal.getUserId(), request.result(),
                LocalDateTime.now(ZONE)));
    }

    private ResponseEntity<MatchResultResponse> toResponse(MatchResultResponse response) {
        HttpStatus status = switch (response.outcome()) {
            case SUBMITTED, CONFIRMED, CONFLICT -> HttpStatus.OK;
            case INVALID_RESULT -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(response);
    }
}
