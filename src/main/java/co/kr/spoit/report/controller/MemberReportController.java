package co.kr.spoit.report.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.report.dto.ReportSubmitRequest;
import co.kr.spoit.report.dto.ReportSubmitResponse;
import co.kr.spoit.report.dto.ReportTargetResponse;
import co.kr.spoit.report.service.MemberReportService;
import co.kr.spoit.report.service.ReportAccessDeniedException;
import co.kr.spoit.report.service.ReportNotFoundException;

/**
 * 경기 결과 화면의 회원 신고 API다.
 *
 * <p>{@code GET /mypage/matches/{matchId}/report-targets}는 인증 주체가 확정 참가자인 종료 경기의
 * 신고 대상(본인 제외 확정 참가자)을 {@code [{userId, nickname}]}로 돌려준다.
 * {@code POST /mypage/matches/{matchId}/reports}는 {@code {targetUserId, category, summary}}를 받아
 * 접수 결과 신고 번호를 돌려준다.</p>
 *
 * <p>로그인 확인은 {@code SecurityConfig}의 {@code /mypage/**} 규칙이 맡고, 확정 참가 여부·경기 상태는
 * 서비스가 검사한다. 신고자 번호는 요청 본문이 아니라 인증 주체에서만 얻는다. 권한·상태 위반은 403,
 * 존재하지 않는 경기는 404, 잘못된 입력은 400이며, 어느 응답에도 다른 경기의 참가자 정보를 싣지 않는다.</p>
 */
@RestController
@RequestMapping("/mypage/matches")
public class MemberReportController {

    private final MemberReportService memberReportService;

    public MemberReportController(MemberReportService memberReportService) {
        this.memberReportService = memberReportService;
    }

    @GetMapping("/{matchId}/report-targets")
    public List<ReportTargetResponse> reportTargets(@PathVariable("matchId") long matchId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        return memberReportService.loadTargets(principal == null ? null : principal.getUserId(), matchId);
    }

    @PostMapping("/{matchId}/reports")
    public ResponseEntity<ReportSubmitResponse> submit(@PathVariable("matchId") long matchId,
            @RequestBody(required = false) ReportSubmitRequest request,
            @AuthenticationPrincipal MemberPrincipal principal) {
        long reportId = memberReportService.submit(principal == null ? null : principal.getUserId(),
                matchId, request);
        return ResponseEntity.ok(new ReportSubmitResponse(reportId, "신고가 접수되었습니다."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ReportSubmitResponse> handleInvalidInput(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ReportSubmitResponse(null, exception.getMessage()));
    }

    @ExceptionHandler(ReportAccessDeniedException.class)
    public ResponseEntity<ReportSubmitResponse> handleAccessDenied(ReportAccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ReportSubmitResponse(null, exception.getMessage()));
    }

    @ExceptionHandler(ReportNotFoundException.class)
    public ResponseEntity<ReportSubmitResponse> handleNotFound(ReportNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ReportSubmitResponse(null, exception.getMessage()));
    }
}
