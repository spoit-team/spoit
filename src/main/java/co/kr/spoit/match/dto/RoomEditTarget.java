package co.kr.spoit.match.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 수정·취소 대상 매칭방의 현재 값. 담당 시설 범위 안에서만 조회된다. */
@Getter
@Setter
@NoArgsConstructor
public class RoomEditTarget {

    private Long matchId;
    private Long facilityId;
    private Long sportId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    /** social_match.status */
    private String status;
    /** 현재 시설 사진 URL. 교체·삭제 후 이전 파일을 지울 때 쓴다. */
    private String photoUrl;
}
