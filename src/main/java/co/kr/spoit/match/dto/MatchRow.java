package co.kr.spoit.match.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** findMatches 조회 결과: 매칭방 + 시설·종목·지역 */
@Getter
@Setter
@NoArgsConstructor
public class MatchRow {

    private Long matchId;
    private String title;
    private Long createdBy;
    private Long sportId;
    private String sportName;
    private Long facilityId;
    private String facilityName;
    private String address;
    private String regionName;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private int minParticipants;
    private int maxParticipants;
    private BigDecimal feePerPerson;
    private String requiredSkill;
    private String status;
}
