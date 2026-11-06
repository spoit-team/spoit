package co.kr.spoit.club.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.kr.spoit.club.dto.ClubMemberHistoryView;
import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.mat.dto.ParticipantHistoryView;
import co.kr.spoit.member.domain.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/teams")
public class ClubHistoryController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final ClubService clubService;

    // 가입 신청자 개인이력(JSON): 동호회장 아님, 403, 신청 없음 404
    @GetMapping("/{clubId}/applications/{joinRequestId}/history")
    public ResponseEntity<ParticipantHistoryView> applicantHistory(@PathVariable("clubId") Long clubId,
            @PathVariable("joinRequestId") Long joinRequestId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        log.info("가입 신청자 이력 조회: clubId={}, joinRequestId={}, userId={}", clubId, joinRequestId, principal.getUserId());
        return clubService.findApplicantHistory(clubId, joinRequestId, principal.getUserId(), LocalDateTime.now(ZONE))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // 동호회원 동호회 이력(JSON): 이 동호회 활성 회원 아님 404
    @GetMapping("/{clubId}/members/{clubMemberId}/history")
    public ResponseEntity<ClubMemberHistoryView> memberHistory(@PathVariable("clubId") Long clubId,
            @PathVariable("clubMemberId") Long clubMemberId,
            @AuthenticationPrincipal MemberPrincipal principal) {
        log.info("동호회원 이력 조회: clubId={}, clubMemberId={}, userId={}", clubId, clubMemberId, principal.getUserId());
        return clubService.findMemberHistory(clubId, clubMemberId, LocalDateTime.now(ZONE))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}
