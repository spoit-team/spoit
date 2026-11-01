package co.kr.spoit.match.domain;

/**
 * social_match.required_skill에 저장되는 요구 실력 수준.
 *
 * <p>화면 선택지와 DB 값이 같다. 내부 MMR과는 별개인 안내용 조건이다.</p>
 */
public enum SkillLevel {

    BEGINNER("초급"),
    INTERMEDIATE("중급"),
    ADVANCED("고급");

    private final String dbValue;

    SkillLevel(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    /** DB 값과 정확히 일치하는 수준, 없으면 null */
    public static SkillLevel fromDbValue(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.strip();
        for (SkillLevel level : values()) {
            if (level.dbValue.equals(normalized)) {
                return level;
            }
        }
        return null;
    }
}
