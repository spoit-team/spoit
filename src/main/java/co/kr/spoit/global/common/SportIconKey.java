package co.kr.spoit.global.common;

/** 종목 아이콘 파일 이름(images/club/sports/{key}.svg). 매칭방·동호회 종목 탭이 같이 쓴다. */
public final class SportIconKey {

    private SportIconKey() {
    }

    // 모르는 종목과 전체(sportId 없음)는 별(etc)
    public static String of(Long sportId) {
        if (sportId == null) {
            return "etc";
        }
        return switch (sportId.intValue()) {
            case 1 -> "tennis";
            case 2 -> "soccer";
            case 3 -> "jokgu";
            case 4 -> "basketball";
            case 5 -> "badminton";
            default -> "etc";
        };
    }
}
