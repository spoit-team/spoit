package co.kr.spoit.mat.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 지금 적용 중인 제재의 종류와 종료 시각. endAt이 null이면 기한 없음이다. */
@Getter
@Setter
@NoArgsConstructor
public class ActivePenalty {

    private String penaltyType;
    private LocalDateTime endAt;
}
