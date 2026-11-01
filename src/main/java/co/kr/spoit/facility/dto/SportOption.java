package co.kr.spoit.facility.dto;

import lombok.Getter;

/** 종목 선택 상자에 쓰는 항목이다. */
@Getter
public class SportOption {

    private final Long sportId;
    private final String name;

    public SportOption(Long sportId, String name) {
        this.sportId = sportId;
        this.name = name;
    }
}
