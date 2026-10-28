package co.kr.spoit.mat.controller;

import co.kr.spoit.mat.dto.AutoAssignmentResult;
import co.kr.spoit.mat.service.AutoAssignmentService;
import co.kr.spoit.member.domain.MemberPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시설 관리자 자동 배정 API. 역할 확인은 SecurityConfig의 /admin/operations/** 규칙이 맡고,
 * 담당 시설 여부는 서비스에서 확인한다.
 */
@RestController
@RequestMapping("/admin/operations/matches")
public class AutoAssignmentController {

    private final AutoAssignmentService autoAssignmentService;

    public AutoAssignmentController(AutoAssignmentService autoAssignmentService) {
        this.autoAssignmentService = autoAssignmentService;
    }

    /** 저장하지 않는 미리보기라 GET으로 둔다. 저장 API가 생기면 같은 경로의 POST로 붙인다. */
    @GetMapping("/{matchId}/auto-assignment")
    public ResponseEntity<AutoAssignmentResult> preview(@PathVariable long matchId,
                                                        @AuthenticationPrincipal MemberPrincipal principal) {
        AutoAssignmentResult result = autoAssignmentService.preview(matchId, principal.getUserId());
        HttpStatus status = switch (result.outcome()) {
            case READY -> HttpStatus.OK;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(result);
    }
}
