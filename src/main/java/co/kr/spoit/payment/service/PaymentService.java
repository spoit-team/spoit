package co.kr.spoit.payment.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import co.kr.spoit.match.domain.ApplicationType;
import co.kr.spoit.match.dto.JoinPage;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MatchApplicationService.PaidApply;
import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.payment.config.KakaoPayProperties;
import co.kr.spoit.payment.domain.Payment;
import co.kr.spoit.payment.domain.PaymentStatus;
import co.kr.spoit.payment.dto.PaymentDtos.CompletionResult;
import co.kr.spoit.payment.dto.PaymentDtos.PrepareCommand;
import co.kr.spoit.payment.kakao.KakaoPayClient;
import co.kr.spoit.payment.kakao.KakaoPayClient.ReadyResult;
import co.kr.spoit.payment.kakao.KakaoPayException;
import co.kr.spoit.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 참가비 결제 흐름 (카카오페이 단건 결제).
 *
 * <ol>
 *   <li>준비: 신청 내용 화면과 같은 검증 → 서버가 금액 계산 → 카카오페이 결제 준비 → 결제 페이지 주소</li>
 *   <li>사용자가 카카오페이에서 결제하면 카카오페이가 승인 주소로 pg_token을 붙여 돌려보낸다.</li>
 *   <li>승인: 신청 접수 트랜잭션에서 매칭방을 잠그고 정원·중복을 다시 확인한 뒤 최종 승인 → 신청 행 생성</li>
 * </ol>
 *
 * <p>최종 승인 전에는 돈이 빠져나가지 않으므로 자리가 없으면 승인하지 않고 끝낸다. 승인했는데 신청을 만들지 못한
 * 드문 경우만 바로 전액 취소한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final String TEAM = "team";
    private static final int ITEM_NAME_TITLE_MAX = 40;

    private static final String NOT_FOUND = "결제 정보를 찾을 수 없습니다.";

    private final KakaoPayProperties properties;
    private final KakaoPayClient kakaoPayClient;
    private final PaymentMapper paymentMapper;
    private final PaymentLedgerService ledger;
    private final MatchApplicationService applicationService;

    /** 카카오페이 비밀 키가 설정되어 결제를 열 수 있는지 */
    public boolean isAvailable() {
        return properties.isConfigured();
    }

    public boolean isTestMode() {
        return properties.isTestMode();
    }

    /**
     * 결제 준비: 정원·중복·시간 겹침을 확인하고 서버가 금액을 계산해 카카오페이 결제를 준비한다.
     *
     * @param baseUrl 이 서버 주소 (카카오페이가 돌려보낼 승인·취소·실패 주소를 만든다)
     * @param mobile  모바일 브라우저면 모바일 결제 페이지 주소를 돌려준다
     * @return 사용자를 보낼 카카오페이 결제 페이지 주소
     */
    public String prepare(long matchId, long userId, PrepareCommand command, String baseUrl, boolean mobile) {
        if (!properties.isConfigured()) {
            throw new PaymentException("결제 설정이 아직 준비되지 않았습니다. 시설에 문의해 주세요.");
        }
        if (!command.agreeRules()) {
            throw new PaymentException("매치 운영 규칙과 취소·노쇼 안내에 동의해 주세요.");
        }
        if (!command.agreePayment()) {
            throw new PaymentException("결제 및 환불 안내에 동의해 주세요.");
        }

        boolean team = TEAM.equals(command.applicationType());
        Optional<JoinPage> prepared;
        if (team) {
            prepared = command.clubId() == null ? Optional.empty()
                    : applicationService.prepareTeam(matchId, userId, command.clubId(),
                            command.clubMemberIds() == null ? List.of() : command.clubMemberIds());
        } else {
            prepared = applicationService.prepareIndividual(matchId, userId);
        }
        JoinPage page = prepared.orElseThrow(() ->
                new PaymentException("신청 내용을 확인할 수 없습니다. 매치 상세에서 다시 선택해 주세요."));
        if (!page.isCanReserve()) {
            throw new PaymentException(page.getCapacityNotice());
        }
        if (!page.isPaid()) {
            throw new PaymentException("참가비가 없는 매치는 결제 없이 신청해 주세요.");
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        int seats = page.getSelection().getSeats();
        Payment payment = new Payment();
        payment.setPaymentId("spoit-" + UUID.randomUUID());
        payment.setMatchId(matchId);
        payment.setPayerUserId(userId);
        payment.setClubId(team ? page.getSelection().getClubId() : null);
        payment.setApplicationType(team ? ApplicationType.CLUB.dbValue() : ApplicationType.INDIVIDUAL.dbValue());
        payment.setClubMemberIds(team ? Payment.joinIds(page.getSelection().getClubMemberIds()) : null);
        payment.setUnitAmount(BigDecimal.valueOf(page.getUnitAmount()));
        payment.setSeatCount(seats);
        payment.setTotalAmount(BigDecimal.valueOf(page.getUnitAmount() * seats));
        payment.setCreatedAt(now);
        paymentMapper.insert(payment);

        String callback = baseUrl + "/matches/" + matchId + "/apply/payment/";
        String query = "?paymentId=" + payment.getPaymentId();
        ReadyResult ready;
        try {
            ready = kakaoPayClient.ready(payment.getPaymentId(), String.valueOf(userId),
                    itemName(page.getMatch().getTitle(), seats), seats, payment.getTotalAmount().longValue(),
                    callback + "approve" + query, callback + "cancel" + query, callback + "fail" + query);
        } catch (KakaoPayException e) {
            log.warn("카카오페이 결제 준비 실패: paymentId={}, code={}, message={}", payment.getPaymentId(),
                    e.getCode(), e.getMessage());
            paymentMapper.markFailed(payment.getPaymentId(), "결제 준비 실패", now);
            throw new PaymentException("카카오페이 결제를 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        paymentMapper.saveTid(payment.getPaymentId(), ready.tid(), now);
        String url = mobile && ready.mobileRedirectUrl() != null ? ready.mobileRedirectUrl() : ready.pcRedirectUrl();
        if (url == null) {
            throw new PaymentException("카카오페이 결제를 시작하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return url;
    }

    /** 본인 주문이면 결제 행. 카카오페이에서 돌아왔을 때 어느 신청 화면으로 보낼지 정하는 데 쓴다. */
    public Optional<Payment> findOwned(String paymentId, long userId) {
        Payment payment = paymentId == null ? null : paymentMapper.findById(paymentId);
        if (payment == null || payment.getPayerUserId() == null || payment.getPayerUserId() != userId) {
            return Optional.empty();
        }
        return Optional.of(payment);
    }

    /**
     * 결제 승인 (카카오페이 승인 주소). 신청 접수 트랜잭션 안에서 정원·중복을 확인한 뒤 최종 승인하고 신청을 만든다.
     */
    public CompletionResult approve(String paymentId, long userId, String pgToken) {
        Payment payment = findOwned(paymentId, userId).orElse(null);
        if (payment == null) {
            return CompletionResult.fail(null, NOT_FOUND);
        }
        Long matchId = payment.getMatchId();
        CompletionResult settled = settled(payment);
        if (settled != null) {
            return settled;
        }
        if (pgToken == null || pgToken.isBlank()) {
            return CompletionResult.fail(matchId, "결제가 완료되지 않았습니다.");
        }

        PaidApply paid = new PaidApply(paymentId, payment.getUnitAmount(), payment.getSeatCount(), pgToken);
        try {
            String receiptNo = payment.isTeam()
                    ? applicationService.applyTeamPaid(matchId, userId, payment.getClubId(), payment.memberIds(), paid)
                    : applicationService.applyIndividualPaid(matchId, userId, paid);
            return CompletionResult.ok(matchId, receiptNo);
        } catch (RuntimeException e) {
            // 새로고침으로 승인 주소가 두 번 열렸으면 먼저 끝난 요청의 결과를 돌려준다(취소하지 않는다).
            CompletionResult other = settled(paymentMapper.findById(paymentId));
            if (other != null && other.success()) {
                return other;
            }
            String reason;
            if (e instanceof MatchApplyException) {
                reason = e.getMessage();
            } else {
                log.error("결제 승인 중 신청 처리 실패: paymentId={}", paymentId, e);
                reason = "신청을 처리하지 못했습니다.";
            }
            return switch (ledger.compensateUnapplied(paymentId, reason, LocalDateTime.now(ZONE))) {
                case NOT_PAID -> CompletionResult.fail(matchId, reason + " 결제는 진행되지 않았습니다.");
                case REFUNDED -> CompletionResult.fail(matchId, reason + " 결제한 금액은 전액 취소되었습니다.");
                case UNKNOWN -> CompletionResult.fail(matchId,
                        reason + " 결제 상태를 확인하지 못했습니다. 잠시 후 마이페이지 신청 내역을 확인해 주세요.");
            };
        }
    }

    /** 카카오페이 결제 화면에서 취소했거나 결제에 실패했다. 승인 전이라 돈은 빠져나가지 않았다. */
    public void abandon(String paymentId, long userId, String reason) {
        findOwned(paymentId, userId).ifPresent(payment ->
                paymentMapper.markFailed(paymentId, reason, LocalDateTime.now(ZONE)));
    }

    /** 이미 결론이 난 주문이면 그 결과, 승인 전(READY)이면 null */
    private CompletionResult settled(Payment payment) {
        if (payment == null) {
            return CompletionResult.fail(null, NOT_FOUND);
        }
        if (payment.hasStatus(PaymentStatus.READY)) {
            return null;
        }
        String receiptNo = paymentMapper.findReceiptNo(payment.getPaymentId());
        if (receiptNo != null) {
            return CompletionResult.ok(payment.getMatchId(), receiptNo);
        }
        String reason = payment.getFailReason() == null || payment.getFailReason().isBlank()
                ? "결제가 완료되지 않았습니다." : payment.getFailReason();
        return CompletionResult.fail(payment.getMatchId(), payment.hasStatus(PaymentStatus.REFUNDED)
                ? reason + " 결제한 금액은 전액 취소되었습니다." : reason);
    }

    private static String itemName(String title, int seats) {
        String base = title == null ? "매치" : title.strip();
        if (base.length() > ITEM_NAME_TITLE_MAX) {
            base = base.substring(0, ITEM_NAME_TITLE_MAX);
        }
        return base + " 참가비" + (seats > 1 ? " (" + seats + "명)" : "");
    }
}
