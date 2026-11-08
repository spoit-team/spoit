package co.kr.spoit.match.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.member.domain.MemberPrincipal;

/**
 * 매치 운영 화면의 참가 신청 승인·반려 API다.
 *
 * <p>운영 화면은 폼 없이 fetch와 CSRF 헤더로 호출하고, 성공하면 페이지를 새로 불러온다.
 * 신청 행 하나(팀 신청이면 팀원 한 명)씩 처리하며, 담당 시설 확인은 서비스가 인증 주체 ID로 한다.</p>
 */
@RestController
@RequestMapping("/admin/operations/applications")
public class MatchApplicationDecisionController {

    private final MatchApplicationService applicationService;

    public MatchApplicationDecisionController(MatchApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/{applicationId}/confirm")
    public ResponseEntity<Map<String, String>> confirm(@PathVariable("applicationId") long applicationId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        if (principal == null) {
            return unauthorized();
        }
        try {
            applicationService.confirm(applicationId, principal.getUserId());
            return ResponseEntity.ok(Map.of("message", "참가 신청을 승인했습니다."));
        } catch (MatchApplyException e) {
            return conflict(e);
        }
    }

    @PostMapping("/{applicationId}/reject")
    public ResponseEntity<Map<String, String>> reject(@PathVariable("applicationId") long applicationId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        if (principal == null) {
            return unauthorized();
        }
        try {
            applicationService.reject(applicationId, principal.getUserId());
            return ResponseEntity.ok(Map.of("message", "참가 신청을 반려했습니다."));
        } catch (MatchApplyException e) {
            return conflict(e);
        }
    }

    private static ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요합니다."));
    }

    private static ResponseEntity<Map<String, String>> conflict(MatchApplyException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }
}
