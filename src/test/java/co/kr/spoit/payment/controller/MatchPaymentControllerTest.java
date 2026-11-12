package co.kr.spoit.payment.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import co.kr.spoit.payment.domain.Payment;
import co.kr.spoit.payment.dto.PaymentDtos.CompletionResult;
import co.kr.spoit.payment.service.PaymentException;
import co.kr.spoit.payment.service.PaymentService;

/** 신청 화면 카카오페이 결제: 준비는 로그인·CSRF가 필요한 JSON, 승인·취소·실패는 카카오페이가 돌려보내는 GET이다. */
@WebMvcTest(controllers = MatchPaymentController.class)
@Import(SecurityConfig.class)
class MatchPaymentControllerTest {

    private static final String PREPARE_BODY = """
            {"applicationType":"individual","clubId":null,"clubMemberIds":[],"agreeRules":true,"agreePayment":true}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("결제 준비는 카카오페이 결제 페이지 주소를 돌려주고, 승인·취소 주소를 만들 서버 주소를 넘긴다")
    void prepareReturnsRedirectUrl() throws Exception {
        given(paymentService.prepare(eq(1L), eq(7L), any(), eq("http://localhost"), eq(false)))
                .willReturn("https://online-pay.kakao.com/mockup/v1/abc/info");

        mockMvc.perform(post("/matches/{matchId}/apply/payment/prepare", 1L)
                        .with(user(member())).with(csrf())
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0)")
                        .contentType(MediaType.APPLICATION_JSON).content(PREPARE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.redirectUrl").value("https://online-pay.kakao.com/mockup/v1/abc/info"));
    }

    @Test
    @DisplayName("모바일 브라우저면 모바일 결제 페이지를 요청한다")
    void prepareDetectsMobile() throws Exception {
        given(paymentService.prepare(anyLong(), anyLong(), any(), anyString(), eq(true))).willReturn("https://mobile");

        mockMvc.perform(post("/matches/{matchId}/apply/payment/prepare", 1L)
                        .with(user(member())).with(csrf())
                        .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Mobile/15E148")
                        .contentType(MediaType.APPLICATION_JSON).content(PREPARE_BODY))
                .andExpect(jsonPath("$.redirectUrl").value("https://mobile"));
    }

    @Test
    @DisplayName("결제를 시작할 수 없으면 사유를 400으로 돌려준다")
    void prepareReturnsReason() throws Exception {
        given(paymentService.prepare(anyLong(), anyLong(), any(), anyString(), anyBoolean()))
                .willThrow(new PaymentException("정원이 모두 찼습니다."));

        mockMvc.perform(post("/matches/{matchId}/apply/payment/prepare", 1L)
                        .with(user(member())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(PREPARE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.message").value("정원이 모두 찼습니다."));
    }

    @Test
    @DisplayName("결제 준비는 CSRF 토큰과 로그인이 필요하다")
    void prepareRequiresCsrfAndLogin() throws Exception {
        mockMvc.perform(post("/matches/{matchId}/apply/payment/prepare", 1L)
                        .with(user(member()))
                        .contentType(MediaType.APPLICATION_JSON).content(PREPARE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/matches/{matchId}/apply/payment/prepare", 1L).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(PREPARE_BODY))
                .andExpect(status().is3xxRedirection());
        verify(paymentService, never()).prepare(anyLong(), anyLong(), any(), anyString(), anyBoolean());
    }

    @Test
    @DisplayName("카카오페이 승인 주소로 돌아오면 승인하고 신청 완료 화면으로 보낸다")
    void approveRedirectsToCompletion() throws Exception {
        given(paymentService.findOwned("spoit-1", 7L)).willReturn(Optional.of(individualPayment()));
        given(paymentService.approve("spoit-1", 7L, "pg-1")).willReturn(CompletionResult.ok(1L, "RCPT-1"));

        mockMvc.perform(get("/matches/{matchId}/apply/payment/approve", 1L)
                        .param("paymentId", "spoit-1").param("pg_token", "pg-1")
                        .with(user(member())))
                .andExpect(redirectedUrl("/matches/1/apply/complete?receiptNo=RCPT-1"));
    }

    @Test
    @DisplayName("승인이 실패하면 사유와 함께 팀 신청 화면으로 돌려보낸다")
    void approveFailureGoesBackToTeamApply() throws Exception {
        Payment payment = individualPayment();
        payment.setClubId(5L);
        payment.setClubMemberIds("21,22");
        given(paymentService.findOwned("spoit-1", 7L)).willReturn(Optional.of(payment));
        given(paymentService.approve("spoit-1", 7L, "pg-1"))
                .willReturn(CompletionResult.fail(1L, "정원이 모두 찼습니다. 결제는 진행되지 않았습니다."));

        mockMvc.perform(get("/matches/{matchId}/apply/payment/approve", 1L)
                        .param("paymentId", "spoit-1").param("pg_token", "pg-1")
                        .with(user(member())))
                .andExpect(redirectedUrl("/matches/1/apply/team?clubId=5&clubMemberIds=21%2C22"))
                .andExpect(flash().attribute("error", "정원이 모두 찼습니다. 결제는 진행되지 않았습니다."));
    }

    @Test
    @DisplayName("카카오페이에서 취소하고 돌아오면 주문을 닫고 개인 신청 화면으로 돌려보낸다")
    void cancelGoesBackToApply() throws Exception {
        given(paymentService.findOwned("spoit-1", 7L)).willReturn(Optional.of(individualPayment()));

        mockMvc.perform(get("/matches/{matchId}/apply/payment/cancel", 1L)
                        .param("paymentId", "spoit-1")
                        .with(user(member())))
                .andExpect(redirectedUrl("/matches/1/apply"))
                .andExpect(flash().attribute("paymentNotice", "결제를 취소했습니다."));
        verify(paymentService).abandon("spoit-1", 7L, "결제를 취소했습니다.");
    }

    @Test
    @DisplayName("다른 회원의 주문이면 아무것도 하지 않고 매치 상세로 보낸다")
    void ignoresOtherUsersPayment() throws Exception {
        given(paymentService.findOwned("spoit-1", 7L)).willReturn(Optional.empty());

        mockMvc.perform(get("/matches/{matchId}/apply/payment/approve", 1L)
                        .param("paymentId", "spoit-1").param("pg_token", "pg-1")
                        .with(user(member())))
                .andExpect(redirectedUrl("/matches/1"));
        verify(paymentService, never()).approve(anyString(), anyLong(), anyString());
    }

    private static Payment individualPayment() {
        Payment payment = new Payment();
        payment.setPaymentId("spoit-1");
        payment.setPayerUserId(7L);
        return payment;
    }
}
