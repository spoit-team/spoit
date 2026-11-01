package co.kr.spoit.match.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 담당 시설 매칭방에 실제로 존재하는 종목 선택지 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomManagementSport {

    private Long sportId;
    private String name;
}
