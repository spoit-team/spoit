package co.kr.spoit.club.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ClubJoinRequest {

    private Long joinRequestId;
    private Long userId;
    private String nickname;
    private String message;
    private LocalDateTime requestedAt;
}
