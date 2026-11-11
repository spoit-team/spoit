package co.kr.spoit.payment.domain;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 아직 환불하지 않은 결제 신청 행 하나와 그 결제의 1인 참가비 */
@Getter
@Setter
@NoArgsConstructor
public class RefundableApplicationRow {

    private Long applicationId;
    private String paymentId;
    private BigDecimal unitAmount;
}
