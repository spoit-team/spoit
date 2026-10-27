package co.kr.spoit.match.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** match_application 한 건 */
@Getter
@Setter
@NoArgsConstructor
public class MatchApplication {

    private Long applicationId;
    private Long matchId;
    private Long applicantUserId;
    /** 개인 신청이면 null */
    private Long clubId;
    private String applicationType;
    private String status;
    private String receiptNo;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;

    public static MatchApplication individual(long matchId, long userId, String receiptNo, LocalDateTime now) {
        MatchApplication a = new MatchApplication();
        a.matchId = matchId;
        a.applicantUserId = userId;
        a.applicationType = ApplicationType.INDIVIDUAL.dbValue();
        a.status = ApplicationStatus.WAITING.dbValue();
        a.receiptNo = receiptNo;
        a.createdAt = now;
        return a;
    }
}
