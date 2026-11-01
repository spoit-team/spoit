package co.kr.spoit.match.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당 활성 시설이 제공하는 종목 한 건 (생성·수정 모달 선택지) */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomManagementFacilitySport {

    private Long facilityId;
    private Long sportId;
    private String name;
}
