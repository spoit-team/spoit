package co.kr.spoit.match.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 현재 검색 조건 칩. url은 이 조건만 해제한 검색 주소 */
@Getter
@AllArgsConstructor
public class ActiveFilter {

    private final String label;
    private final String url;
}
