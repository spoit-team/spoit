package co.kr.spoit.match.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 지역 선택지. regionId가 null이면 "전체 지역" */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DistrictOption {

    private Long regionId;
    private String name;
}
