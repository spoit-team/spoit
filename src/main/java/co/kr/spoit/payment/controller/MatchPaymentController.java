package co.kr.spoit.payment.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import co.kr.spoit.global.common.FlashFeedback;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.payment.domain.Payment;
import co.kr.spoit.payment.dto.PaymentDtos.CompletionResult;
import co.kr.spoit.payment.dto.PaymentDtos.PrepareCommand;
import co.kr.spoit.payment.service.PaymentException;
import co.kr.spoit.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;

/**
 * 신청 화면 STEP 2 카카오페이 결제. 경로가 /matches/{id}/apply/** 아래라 로그인이 필요하다.
 *
 * <ul>
 *   <li>POST prepare (fetch + CSRF 헤더): 결제를 준비하고 카카오페이 결제 페이지 주소를 돌려준다.</li>
 *   <li>GET approve: 카카오페이가 pg_token을 붙여 돌려보내는 주소. 최종 승인 후 신청 완료 화면으로 보낸다.</li>
 *   <li>GET cancel / fail: 카카오페이에서 취소·실패했을 때 돌아오는 주소. 신청 화면으로 돌려보낸다.</li>
 * </ul>
 */
@Controller
@RequestMapping("/matches/{matchId}/apply/payment")
@RequiredArgsConstructor
public class MatchPaymentController {

    /** 결제가 실패했을 때 실패 안내 모달의 제목 */
    static final String PAYMENT_ERROR_TITLE = "결제를 완료하지 못했습니다";
    /** 사용자가 카카오페이에서 직접 취소했을 때의 안내. 실패가 아니므로 모달 대신 신청 화면 문구로 보여 준다. */
    static final String PAYMENT_NOTICE = "paymentNotice";

    private final PaymentService paymentService;

    public record PrepareBody(String applicationType, Long clubId, List<Long> clubMemberIds,
                              boolean agreeRules, boolean agreePayment) {
    }

    @PostMapping("/prepare")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> prepare(@PathVariable long matchId,
                                                       @AuthenticationPrincipal MemberPrincipal principal,
                                                       @RequestHeader(value = "User-Agent", required = false) String userAgent,
                                                       @RequestBody PrepareBody body) {
        try {
            String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
            String redirectUrl = paymentService.prepare(matchId, principal.getUserId(),
                    new PrepareCommand(body.applicationType(), body.clubId(), body.clubMemberIds(),
                            body.agreeRules(), body.agreePayment()),
                    baseUrl, isMobile(userAgent));
            return ResponseEntity.ok(Map.of("ok", true, "redirectUrl", redirectUrl));
        } catch (PaymentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/approve")
    public String approve(@PathVariable long matchId,
                          @RequestParam(required = false) String paymentId,
                          @RequestParam(name = "pg_token", required = false) String pgToken,
                          @AuthenticationPrincipal MemberPrincipal principal,
                          RedirectAttributes redirect) {
        Payment payment = paymentService.findOwned(paymentId, principal.getUserId()).orElse(null);
        if (payment == null) {
            return "redirect:/matches/{matchId}";
        }
        CompletionResult result = paymentService.approve(paymentId, principal.getUserId(), pgToken);
        if (result.success()) {
            redirect.addAttribute("receiptNo", result.receiptNo());
            return "redirect:/matches/{matchId}/apply/complete";
        }
        FlashFeedback.failure(redirect, PAYMENT_ERROR_TITLE, result.message());
        return backToApply(payment, redirect);
    }

    @GetMapping("/cancel")
    public String cancel(@PathVariable long matchId,
                         @RequestParam(required = false) String paymentId,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes redirect) {
        return abandon(paymentId, principal, redirect, "결제를 취소했습니다.", false);
    }

    @GetMapping("/fail")
    public String fail(@PathVariable long matchId,
                       @RequestParam(required = false) String paymentId,
                       @AuthenticationPrincipal MemberPrincipal principal,
                       RedirectAttributes redirect) {
        return abandon(paymentId, principal, redirect, "결제에 실패했습니다. 다시 시도해 주세요.", true);
    }

    private String abandon(String paymentId, MemberPrincipal principal, RedirectAttributes redirect, String message,
                           boolean failed) {
        Payment payment = paymentService.findOwned(paymentId, principal.getUserId()).orElse(null);
        if (payment == null) {
            return "redirect:/matches/{matchId}";
        }
        paymentService.abandon(paymentId, principal.getUserId(), message);
        if (failed) {
            FlashFeedback.failure(redirect, PAYMENT_ERROR_TITLE, message);
        } else {
            redirect.addFlashAttribute(PAYMENT_NOTICE, message);
        }
        return backToApply(payment, redirect);
    }

    private static boolean isMobile(String userAgent) {
        return userAgent != null && (userAgent.contains("Mobi") || userAgent.contains("Android"));
    }

    private static String backToApply(Payment payment, RedirectAttributes redirect) {
        if (!payment.isTeam()) {
            return "redirect:/matches/{matchId}/apply";
        }
        redirect.addAttribute("clubId", payment.getClubId());
        List<Long> memberIds = payment.memberIds();
        if (!memberIds.isEmpty()) {
            redirect.addAttribute("clubMemberIds", memberIds);
        }
        return "redirect:/matches/{matchId}/apply/team";
    }
}
