package co.kr.spoit.mat.domain;

/** social_match.rating_status 값. DB에는 상수 이름 그대로 저장한다. DB가 허용하는 READY는 쓰지 않는다. */
public enum RatingStatus {
    WAITING,
    APPLIED,
    EXCLUDED
}
