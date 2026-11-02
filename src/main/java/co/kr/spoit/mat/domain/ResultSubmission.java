package co.kr.spoit.mat.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResultSubmission {

    private Long submissionId;
    private Long captainParticipantId;
    private String reportedResult;
    private String submissionStatus;
    private LocalDateTime submittedAt;
}
