package co.kr.spoit.match.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 종목 탭. sportId가 null이면 "전체" */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SportTab {

    private Long sportId;
    private String name;
    private long count;

    public static SportTab all(long count) {
        return new SportTab(null, "전체", count);
    }
}
