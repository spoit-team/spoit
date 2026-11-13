package co.kr.spoit.report.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code penalty} 테이블 행이다. 신고 처리·회원 정지로 부여되는 제재 이력을 담는다.
 *
 * <p>{@code endAt}이 {@code null}이면 기간이 없는 제재(경고)이고, 값이 있으면 그 시각까지
 * 이용이 제한된다. 영구 정지는 종료 시각이 없는 대신
 * {@link co.kr.spoit.member.service.MemberPenaltyTimeService#PERMANENT_END_AT} 센티널을
 * {@code end_at}에 저장해 항상 미래로 비교되게 한다(신고 처리 경로에서는 선택할 수 없다).</p>
 */
@Getter
@Setter
public class Penalty {

    private Long penaltyId;
    private Long targetUserId;
    private Long reportId;
    private Long issuedBy;
    private String penaltyType;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private String reason;
    private Long releasedBy;
    private LocalDateTime releasedAt;
}
