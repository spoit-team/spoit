package co.kr.spoit.club.dto;

import java.time.format.DateTimeFormatter;

import co.kr.spoit.club.domain.ClubJoinRequest;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MyJoinRequestResponse {

    private final Long joinRequestId;
    private final Long clubId;
    private final String clubName;
    private final String date;

    // 관리 화면 맨 위 내 가입 신청 박스
    public static MyJoinRequestResponse from(ClubJoinRequest request) {
        return MyJoinRequestResponse.builder()
                .joinRequestId(request.getJoinRequestId())
                .clubId(request.getClubId())
                .clubName(request.getClubName())
                .date(request.getRequestedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
                .build();
    }
}
