package co.kr.spoit.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.kr.spoit.match.dto.JoinPage;
import co.kr.spoit.match.dto.JoinPage.JoinMatch;
import co.kr.spoit.match.dto.JoinPage.Person;
import co.kr.spoit.match.dto.JoinPage.Selection;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MatchApplicationService.PaidApply;
import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.payment.config.KakaoPayProperties;
import co.kr.spoit.payment.domain.Payment;
import co.kr.spoit.payment.dto.PaymentDtos.CompletionResult;
import co.kr.spoit.payment.dto.PaymentDtos.PrepareCommand;
import co.kr.spoit.payment.kakao.KakaoPayClient;
import co.kr.spoit.payment.kakao.KakaoPayClient.ReadyResult;
import co.kr.spoit.payment.kakao.KakaoPayException;
import co.kr.spoit.payment.mapper.PaymentMapper;
import co.kr.spoit.payment.service.PaymentLedgerService.Unapplied;

/** 카카오페이 결제 준비·승인: 금액은 서버가 정하고, 정원 확인 뒤 승인하며, 신청을 못 만들면 승인 결제를 취소한다. */
class PaymentServiceTest {

    private static final long MATCH_ID = 3L;
    private static final long USER_ID = 7L;
    private static final String PAYMENT_ID = "spoit-test";
    private static final String BASE = "http://localhost:8080";

    private KakaoPayProperties properties;
    private KakaoPayClient kakaoPayClient;
    private PaymentMapper paymentMapper;
    private PaymentLedgerService ledger;
    private MatchApplicationService applicationService;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        properties = new KakaoPayProperties();
        properties.setSecretKey("DEV-secret");
        kakaoPayClient = mock(KakaoPayClient.class);
        paymentMapper = mock(PaymentMapper.class);
        ledger = mock(PaymentLedgerService.class);
        applicationService = mock(MatchApplicationService.class);
        service = new PaymentService(properties, kakaoPayClient, paymentMapper, ledger, applicationService);
        given(kakaoPayClient.ready(anyString(), anyString(), anyString(), anyInt(), anyLong(), anyString(),
                anyString(), anyString())).willReturn(new ReadyResult("T123", "https://pc", "https://mobile"));
    }

    /* ---------------- 준비 ---------------- */

    @Test
    @DisplayName("팀 결제 준비는 1인 참가비 × 인원으로 금액을 계산하고 카카오페이 결제 페이지 주소를 돌려준다")
    void prepareTeamComputesAmountOnServer() {
        given(applicationService.prepareTeam(MATCH_ID, USER_ID, 5L, List.of(11L, 12L, 13L, 14L)))
                .willReturn(Optional.of(page(true, true, 9000, teamSelection(5, List.of(11L, 12L, 13L, 14L)))));

        String url = service.prepare(MATCH_ID, USER_ID,
                new PrepareCommand("team", 5L, List.of(11L, 12L, 13L, 14L), true, true), BASE, false);

        assertThat(url).isEqualTo("https://pc");
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insert(saved.capture());
        Payment payment = saved.getValue();
        assertThat(payment.getTotalAmount()).isEqualByComparingTo("45000");
        assertThat(payment.getUnitAmount()).isEqualByComparingTo("9000");
        assertThat(payment.getSeatCount()).isEqualTo(5);
        assertThat(payment.getClubId()).isEqualTo(5L);
        assertThat(payment.getClubMemberIds()).isEqualTo("11,12,13,14");
        assertThat(payment.getApplicationType()).isEqualTo("동호회");
        String id = payment.getPaymentId();
        assertThat(id).startsWith("spoit-");
        verify(kakaoPayClient).ready(eq(id), eq("7"), eq("토요일 풋살 참가비 (5명)"), eq(5), eq(45000L),
                eq(BASE + "/matches/3/apply/payment/approve?paymentId=" + id),
                eq(BASE + "/matches/3/apply/payment/cancel?paymentId=" + id),
                eq(BASE + "/matches/3/apply/payment/fail?paymentId=" + id));
        verify(paymentMapper).saveTid(eq(id), eq("T123"), any());
    }

    @Test
    @DisplayName("모바일이면 모바일 결제 페이지 주소를 돌려준다")
    void prepareReturnsMobileUrl() {
        given(applicationService.prepareIndividual(MATCH_ID, USER_ID))
                .willReturn(Optional.of(page(true, true, 9000, individualSelection())));

        assertThat(service.prepare(MATCH_ID, USER_ID, individual(), BASE, true)).isEqualTo("https://mobile");
    }

    @Test
    @DisplayName("비밀 키가 없으면 결제를 시작하지 않는다")
    void prepareRequiresConfiguration() {
        properties.setSecretKey("");

        assertThatThrownBy(() -> service.prepare(MATCH_ID, USER_ID, individual(), BASE, false))
                .isInstanceOf(PaymentException.class);
        verify(paymentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("결제·환불 안내 동의가 없으면 주문을 만들지 않는다")
    void prepareRequiresPaymentAgreement() {
        assertThatThrownBy(() -> service.prepare(MATCH_ID, USER_ID,
                new PrepareCommand("individual", null, List.of(), true, false), BASE, false))
                .isInstanceOf(PaymentException.class)
                .hasMessage("결제 및 환불 안내에 동의해 주세요.");
        verify(paymentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("신청할 수 없는 상태면 신청 화면의 사유를 그대로 돌려준다")
    void prepareRejectsBlockedSelection() {
        given(applicationService.prepareIndividual(MATCH_ID, USER_ID))
                .willReturn(Optional.of(page(false, true, 9000, individualSelection())));

        assertThatThrownBy(() -> service.prepare(MATCH_ID, USER_ID, individual(), BASE, false))
                .isInstanceOf(PaymentException.class)
                .hasMessage("정원이 모두 찼습니다.");
        verify(paymentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("무료 매치는 결제 주문을 만들지 않는다")
    void prepareRejectsFreeMatch() {
        given(applicationService.prepareIndividual(MATCH_ID, USER_ID))
                .willReturn(Optional.of(page(true, false, 0, individualSelection())));

        assertThatThrownBy(() -> service.prepare(MATCH_ID, USER_ID, individual(), BASE, false))
                .isInstanceOf(PaymentException.class);
        verify(paymentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("카카오페이 결제 준비가 실패하면 주문을 실패로 닫고 안내한다")
    void prepareFailsWhenKakaoPayFails() {
        given(applicationService.prepareIndividual(MATCH_ID, USER_ID))
                .willReturn(Optional.of(page(true, true, 9000, individualSelection())));
        given(kakaoPayClient.ready(anyString(), anyString(), anyString(), anyInt(), anyLong(), anyString(),
                anyString(), anyString())).willThrow(new KakaoPayException("-780", "approval failure"));

        assertThatThrownBy(() -> service.prepare(MATCH_ID, USER_ID, individual(), BASE, false))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("카카오페이 결제를 시작하지 못했습니다");
        verify(paymentMapper).markFailed(anyString(), anyString(), any());
        verify(paymentMapper, never()).saveTid(anyString(), anyString(), any());
    }

    /* ---------------- 승인 ---------------- */

    @Test
    @DisplayName("승인 주소로 돌아오면 pg_token을 넘겨 신청을 접수한다")
    void approveApplies() {
        given(paymentMapper.findById(PAYMENT_ID)).willReturn(payment("READY", null, 9000, 1));
        given(applicationService.applyIndividualPaid(eq(MATCH_ID), eq(USER_ID), any())).willReturn("RCPT-1");

        CompletionResult result = service.approve(PAYMENT_ID, USER_ID, "pg-token");

        assertThat(result.success()).isTrue();
        assertThat(result.receiptNo()).isEqualTo("RCPT-1");
        ArgumentCaptor<PaidApply> paid = ArgumentCaptor.forClass(PaidApply.class);
        verify(applicationService).applyIndividualPaid(eq(MATCH_ID), eq(USER_ID), paid.capture());
        assertThat(paid.getValue()).isEqualTo(new PaidApply(PAYMENT_ID, new BigDecimal("9000"), 1, "pg-token"));
        verify(ledger, never()).compensateUnapplied(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("팀 결제는 결제할 때 고른 동호회·팀원으로 팀 신청을 접수한다")
    void approveAppliesTeamFromSnapshot() {
        Payment payment = payment("READY", null, 9000, 3);
        payment.setClubId(5L);
        payment.setClubMemberIds("11,12");
        given(paymentMapper.findById(PAYMENT_ID)).willReturn(payment);
        given(applicationService.applyTeamPaid(eq(MATCH_ID), eq(USER_ID), eq(5L), eq(List.of(11L, 12L)), any()))
                .willReturn("RCPT-T");

        assertThat(service.approve(PAYMENT_ID, USER_ID, "pg").receiptNo()).isEqualTo("RCPT-T");
    }

    @Test
    @DisplayName("다른 회원의 주문은 승인하지 않는다")
    void approveRejectsOtherUsersPayment() {
        given(paymentMapper.findById(PAYMENT_ID)).willReturn(payment("READY", null, 9000, 1));

        assertThat(service.approve(PAYMENT_ID, 99L, "pg").success()).isFalse();
        verify(applicationService, never()).applyIndividualPaid(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("승인 전에 자리가 없으면 결제는 진행되지 않았다고 안내한다")
    void approveReportsNotPaidWhenApplyFails() {
        given(paymentMapper.findById(PAYMENT_ID)).willReturn(payment("READY", null, 9000, 1));
        given(applicationService.applyIndividualPaid(eq(MATCH_ID), eq(USER_ID), any()))
                .willThrow(new MatchApplyException("정원이 모두 찼습니다."));
        given(ledger.compensateUnapplied(eq(PAYMENT_ID), eq("정원이 모두 찼습니다."), any())).willReturn(Unapplied.NOT_PAID);

        CompletionResult result = service.approve(PAYMENT_ID, USER_ID, "pg");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("정원이 모두 찼습니다. 결제는 진행되지 않았습니다.");
    }

    @Test
    @DisplayName("승인 뒤 신청을 만들지 못했으면 전액 취소했다고 안내한다")
    void approveReportsRefundWhenApprovedButFailed() {
        given(paymentMapper.findById(PAYMENT_ID)).willReturn(payment("READY", null, 9000, 1));
        given(applicationService.applyIndividualPaid(eq(MATCH_ID), eq(USER_ID), any()))
                .willThrow(new IllegalStateException("DB 오류"));
        given(ledger.compensateUnapplied(eq(PAYMENT_ID), anyString(), any())).willReturn(Unapplied.REFUNDED);

        CompletionResult result = service.approve(PAYMENT_ID, USER_ID, "pg");

        assertThat(result.message()).isEqualTo("신청을 처리하지 못했습니다. 결제한 금액은 전액 취소되었습니다.");
    }

    @Test
    @DisplayName("승인 주소가 두 번 열려 먼저 끝난 요청이 있으면 취소하지 않고 그 접수 번호를 돌려준다")
    void approveReturnsEarlierResult() {
        given(paymentMapper.findById(PAYMENT_ID)).willReturn(payment("READY", null, 9000, 1),
                payment("PAID", null, 9000, 1));
        given(applicationService.applyIndividualPaid(eq(MATCH_ID), eq(USER_ID), any()))
                .willThrow(new MatchApplyException("이미 참가 신청한 매칭방입니다."));
        given(paymentMapper.findReceiptNo(PAYMENT_ID)).willReturn("RCPT-1");

        CompletionResult result = service.approve(PAYMENT_ID, USER_ID, "pg");

        assertThat(result.success()).isTrue();
        assertThat(result.receiptNo()).isEqualTo("RCPT-1");
        verify(ledger, never()).compensateUnapplied(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("이미 처리된 결제는 다시 승인하지 않고 결과를 돌려준다")
    void approveIsIdempotent() {
        given(paymentMapper.findById(PAYMENT_ID)).willReturn(payment("PAID", null, 9000, 1));
        given(paymentMapper.findReceiptNo(PAYMENT_ID)).willReturn("RCPT-1");

        assertThat(service.approve(PAYMENT_ID, USER_ID, "pg").receiptNo()).isEqualTo("RCPT-1");

        given(paymentMapper.findById(PAYMENT_ID)).willReturn(payment("FAILED", "결제를 취소했습니다.", 9000, 1));
        given(paymentMapper.findReceiptNo(PAYMENT_ID)).willReturn(null);
        assertThat(service.approve(PAYMENT_ID, USER_ID, "pg").message()).isEqualTo("결제를 취소했습니다.");
        verify(applicationService, never()).applyIndividualPaid(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("카카오페이에서 취소하고 돌아오면 승인 전 주문을 실패로 닫는다")
    void abandonClosesOwnOrder() {
        given(paymentMapper.findById(PAYMENT_ID)).willReturn(payment("READY", null, 9000, 1));

        service.abandon(PAYMENT_ID, USER_ID, "결제를 취소했습니다.");
        service.abandon(PAYMENT_ID, 99L, "다른 회원");

        verify(paymentMapper).markFailed(eq(PAYMENT_ID), eq("결제를 취소했습니다."), any());
        verify(paymentMapper, never()).markFailed(eq(PAYMENT_ID), eq("다른 회원"), any());
    }

    /* ---------------- 픽스처 ---------------- */

    private static PrepareCommand individual() {
        return new PrepareCommand("individual", null, List.of(), true, true);
    }

    private static Payment payment(String status, String failReason, long unit, int seats) {
        Payment p = new Payment();
        p.setPaymentId(PAYMENT_ID);
        p.setMatchId(MATCH_ID);
        p.setPayerUserId(USER_ID);
        p.setApplicationType("개인");
        p.setUnitAmount(BigDecimal.valueOf(unit));
        p.setSeatCount(seats);
        p.setTotalAmount(BigDecimal.valueOf(unit * seats));
        p.setRefundedAmount(BigDecimal.ZERO);
        p.setStatus(status);
        p.setTid("T123");
        p.setFailReason(failReason);
        return p;
    }

    private static Selection individualSelection() {
        return new Selection("개인 신청", "회원", "010-0000-0000", "individual", 1,
                List.of(new Person("회원", "신청자")), null, List.of());
    }

    private static Selection teamSelection(int seats, List<Long> memberIds) {
        return new Selection("팀 신청", "대표", "010-0000-0000", "team", seats,
                List.of(new Person("대표", "신청 대표")), 5L, memberIds);
    }

    private static JoinPage page(boolean canReserve, boolean paid, long unit, Selection selection) {
        return JoinPage.builder()
                .match(JoinMatch.builder().matchId(MATCH_ID).title("토요일 풋살").build())
                .selection(selection)
                .canReserve(canReserve)
                .capacityNotice(canReserve ? "" : "정원이 모두 찼습니다.")
                .paid(paid)
                .unitAmount(unit)
                .totalAmount(unit * selection.getSeats())
                .build();
    }
}
