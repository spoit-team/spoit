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
    /**
     * RES-04 팀 신청 묶음: 대표 행의 application_id (대표 행은 자기 자신).
     * 개인 신청과 이전 방식의 동호회 신청(신청 1건 : 참가자 N명)은 null.
     */
    private Long groupApplicationId;

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

    /**
     * RES-04 팀 신청의 한 사람 몫. 팀원마다 한 행씩 만들고 groupApplicationId로 묶는다.
     *
     * @param groupApplicationId 대표 행이면 null (INSERT 뒤 자기 id로 채운다)
     */
    public static MatchApplication teamMember(long matchId, long userId, long clubId, Long groupApplicationId,
                                              String receiptNo, LocalDateTime now) {
        MatchApplication a = new MatchApplication();
        a.matchId = matchId;
        a.applicantUserId = userId;
        a.clubId = clubId;
        a.applicationType = ApplicationType.CLUB.dbValue();
        a.status = ApplicationStatus.WAITING.dbValue();
        a.receiptNo = receiptNo;
        a.createdAt = now;
        a.groupApplicationId = groupApplicationId;
        return a;
    }

    public boolean isTeamGroup() {
        return groupApplicationId != null;
    }
}
