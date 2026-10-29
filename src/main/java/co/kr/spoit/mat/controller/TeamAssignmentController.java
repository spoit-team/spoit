package co.kr.spoit.mat.controller;

import co.kr.spoit.mat.dto.AssignmentResult;
import co.kr.spoit.mat.dto.ManualAssignmentRequest;
import co.kr.spoit.mat.dto.TeamAssignmentView;
import co.kr.spoit.mat.service.TeamAssignmentService;
import co.kr.spoit.member.domain.MemberPrincipal;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/operations/matches")
public class TeamAssignmentController {

    private final TeamAssignmentService teamAssignmentService;

    public TeamAssignmentController(TeamAssignmentService teamAssignmentService) {
        this.teamAssignmentService = teamAssignmentService;
    }

    @GetMapping("/{matchId}/assignment")
    public ResponseEntity<?> view(@PathVariable long matchId,
                                  @AuthenticationPrincipal MemberPrincipal principal) {
        Optional<TeamAssignmentView> view = teamAssignmentService.getView(matchId, principal.getUserId());
        if (view.isEmpty()) {
            return toResponse(AssignmentResult.notFound());
        }
        return ResponseEntity.ok(view.get());
    }

    @PostMapping("/{matchId}/auto-assignment")
    public ResponseEntity<AssignmentResult> confirmAuto(@PathVariable long matchId,
                                                        @AuthenticationPrincipal MemberPrincipal principal) {
        return toResponse(teamAssignmentService.confirmAuto(matchId, principal.getUserId()));
    }

    @PostMapping("/{matchId}/assignment")
    public ResponseEntity<AssignmentResult> confirmManual(@PathVariable long matchId,
                                                          @RequestBody ManualAssignmentRequest request,
                                                          @AuthenticationPrincipal MemberPrincipal principal) {
        return toResponse(teamAssignmentService.confirmManual(matchId, principal.getUserId(),
                request.teamA(), request.teamB()));
    }

    @DeleteMapping("/{matchId}/assignment")
    public ResponseEntity<AssignmentResult> reset(@PathVariable long matchId,
                                                  @AuthenticationPrincipal MemberPrincipal principal) {
        return toResponse(teamAssignmentService.reset(matchId, principal.getUserId()));
    }

    private ResponseEntity<AssignmentResult> toResponse(AssignmentResult result) {
        HttpStatus status = switch (result.outcome()) {
            case ASSIGNED, RESET -> HttpStatus.OK;
            case INVALID_TEAMS -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status).body(result);
    }
}
