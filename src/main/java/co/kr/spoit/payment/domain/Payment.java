package co.kr.spoit.payment.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** payment 한 건 (카카오페이 단건 결제). 팀 신청은 대표가 인원수만큼 한 번에 결제한다. */
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    private String paymentId;
    private Long matchId;
    private Long payerUserId;
    /** 팀 신청이면 동호회, 개인 신청이면 null */
    private Long clubId;
    /** match_application.application_type 값 (개인 / 동호회) */
    private String applicationType;
    /** 팀 신청 때 고른 동호회원 ID (쉼표 구분, 대표 제외). 개인 신청이면 null */
    private String clubMemberIds;
    private BigDecimal unitAmount;
    private int seatCount;
    private BigDecimal totalAmount;
    private BigDecimal refundedAmount;
    private String status;
    /** 카카오페이 결제 고유번호 (결제 준비 응답) */
    private String tid;
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime updatedAt;

    public boolean isTeam() {
        return clubId != null;
    }

    public boolean hasStatus(PaymentStatus expected) {
        return expected.name().equals(status);
    }

    public List<Long> memberIds() {
        if (clubMemberIds == null || clubMemberIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(clubMemberIds.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }

    public static String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }
}
