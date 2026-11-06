package co.kr.spoit.match.dto;

import co.kr.spoit.global.common.SportIconKey;
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

    /** 종목 아이콘 파일 이름(images/club/sports/{key}.svg). 전체 탭은 별(etc) */
    public String getSportKey() {
        return SportIconKey.of(sportId);
    }

    public static SportTab all(long count) {
        return new SportTab(null, "전체", count);
    }
}
