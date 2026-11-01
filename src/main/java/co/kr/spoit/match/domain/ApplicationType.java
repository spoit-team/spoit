package co.kr.spoit.match.domain;

/** match_application.application_type 값 */
public enum ApplicationType {

    INDIVIDUAL("개인"),
    CLUB("동호회");

    private final String dbValue;

    ApplicationType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }
}
