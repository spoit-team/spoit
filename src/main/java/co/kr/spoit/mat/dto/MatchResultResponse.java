package co.kr.spoit.mat.dto;

/** 경기 결과 제출과 레이팅 제외의 처리 결과. */
public record MatchResultResponse(Outcome outcome, String message) {

    public enum Outcome {
        SUBMITTED,
        CONFIRMED,
        CONFLICT,
        EXCLUDED,
        NOT_FOUND,
        INVALID_RESULT,
        INVALID_STATUS,
        OUT_OF_PERIOD,
        RATING_CLOSED,
        ALREADY_SUBMITTED,
        NOT_STARTED,
        ALREADY_APPLIED,
        ALREADY_EXCLUDED
    }

    public static MatchResultResponse notFound() {
        return of(Outcome.NOT_FOUND, "경기 정보를 찾을 수 없습니다.");
    }

    public static MatchResultResponse of(Outcome outcome, String message) {
        return new MatchResultResponse(outcome, message);
    }
}
