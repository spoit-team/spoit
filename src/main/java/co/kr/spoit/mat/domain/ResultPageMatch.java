package co.kr.spoit.mat.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 경기 결과 제출 화면에 보여 줄 경기 정보. */
@Getter
@Setter
@NoArgsConstructor
public class ResultPageMatch {

    private Long matchId;
    private String title;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private String facilityName;
    private String sportName;
    private Long captainAParticipantId;
    private Long captainBParticipantId;
}
