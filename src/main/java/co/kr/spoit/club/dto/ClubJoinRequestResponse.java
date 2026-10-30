package co.kr.spoit.club.dto;

import java.time.format.DateTimeFormatter;

import co.kr.spoit.club.domain.ClubJoinRequest;
import lombok.Builder;
import lombok.Getter;

@Getter 
@Builder 
public class ClubJoinRequestResponse {

    private final Long joinRequestId;
    private final String nickname;
    private final String date;
    private final String message;

    // 내동호회 관리 화면 가입 신청 탭
    public static ClubJoinRequestResponse from(ClubJoinRequest request){
        return ClubJoinRequestResponse.builder()
        .joinRequestId(request.getRequestId())
        .nickname(request.getNickname())
        .date(request.getRequestedAt().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))
        .message(request.getMessage())
        .build();
    }
}
