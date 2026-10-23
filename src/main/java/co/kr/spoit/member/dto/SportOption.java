package co.kr.spoit.member.dto;

import lombok.Getter;
import lombok.Setter;

/** 종목 선택지·저장된 대표 종목 조회용 행. */
@Getter
@Setter
public class SportOption {
    private Long sportId;
    private String name;
}
