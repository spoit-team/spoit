package co.kr.spoit.mat.controller;

import co.kr.spoit.mat.dto.ParticipantHistoryView;
import co.kr.spoit.mat.service.ParticipantHistoryService;
import co.kr.spoit.member.domain.MemberPrincipal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시설 관리자의 참가자 이력 조회 API. 시설 관리자 역할은 SecurityConfig의 /admin/operations/** 규칙이,
 * 담당 시설 여부는 서비스가 확인한다. 경기가 없거나 그 경기와 관계없는 회원이면 본문 없이 404로 답한다.
 */
@RestController
@RequestMapping("/admin/operations/matches")
public class ParticipantHistoryController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final ParticipantHistoryService historyService;

    public ParticipantHistoryController(ParticipantHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/{matchId}/participants/{userId}/history")
    public ResponseEntity<ParticipantHistoryView> history(@PathVariable("matchId") long matchId,
                                                          @PathVariable("userId") long userId,
                                                          @AuthenticationPrincipal MemberPrincipal principal) {
        return historyService.getHistory(matchId, userId, principal.getUserId(), LocalDateTime.now(ZONE))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
