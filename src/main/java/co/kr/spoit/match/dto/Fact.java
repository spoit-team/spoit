package co.kr.spoit.match.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 상세 모달의 "라벨: 값" 한 줄 */
@Getter
@AllArgsConstructor
public class Fact {

    private final String label;
    private final String value;
}
