package co.kr.spoit.payment.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.payment.domain.Payment;
import co.kr.spoit.payment.domain.RefundableApplicationRow;

/**
 * payment와 신청 행의 결제 연결 컬럼.
 *
 * <p>상태 전이는 모두 "현재 상태" 조건을 건 UPDATE로 한다. 영향 행 수가 0이면 다른 요청이 먼저 바꾼 것이다.</p>
 */
@Mapper
public interface PaymentMapper {

    int insert(Payment payment);

    Payment findById(@Param("paymentId") String paymentId);

    /** 결제 행 잠금. 잠금 순서는 매칭방 → 결제다. */
    Payment lockById(@Param("paymentId") String paymentId);

    /** 결제 준비 응답의 카카오페이 결제 고유번호를 저장한다. */
    int saveTid(@Param("paymentId") String paymentId,
                @Param("tid") String tid,
                @Param("now") LocalDateTime now);

    /** READY → PAID. 최종 승인하고 신청 행을 만든 같은 트랜잭션에서 부른다. */
    int markPaid(@Param("paymentId") String paymentId,
                 @Param("paidAt") LocalDateTime paidAt,
                 @Param("now") LocalDateTime now);

    /** READY → FAILED. 승인하지 않고 끝난 주문을 닫는다. */
    int markFailed(@Param("paymentId") String paymentId,
                   @Param("failReason") String failReason,
                   @Param("now") LocalDateTime now);

    /** READY → REFUNDED. 최종 승인했지만 신청을 만들지 못해 바로 전액 취소했다. */
    int markRefundedUnapplied(@Param("paymentId") String paymentId,
                              @Param("failReason") String failReason,
                              @Param("now") LocalDateTime now);

    /** 결제 완료(PAID·PARTIAL_REFUNDED) 건에 환불 금액을 더하고, 다 돌려주면 REFUNDED로 바꾼다. */
    int addRefunded(@Param("paymentId") String paymentId,
                    @Param("amount") BigDecimal amount,
                    @Param("now") LocalDateTime now);

    int linkApplications(@Param("paymentId") String paymentId,
                         @Param("applicationIds") Collection<Long> applicationIds);

    /** 이 결제로 만든 신청의 접수 번호. 팀 신청은 대표 행(가장 먼저 만든 행)의 번호다. */
    String findReceiptNo(@Param("paymentId") String paymentId);

    /** 고른 신청 행 중 결제가 있고 아직 환불하지 않은 행 */
    List<RefundableApplicationRow> findRefundableApplications(@Param("applicationIds") Collection<Long> applicationIds);

    /** 매칭방의 활성 신청 중 결제가 있고 아직 환불하지 않은 행 */
    List<RefundableApplicationRow> findRefundableApplicationsOfMatch(@Param("matchId") long matchId,
                                                                     @Param("activeStatuses") List<String> activeStatuses);

    int markApplicationsRefunded(@Param("applicationIds") Collection<Long> applicationIds,
                                 @Param("now") LocalDateTime now);
}
