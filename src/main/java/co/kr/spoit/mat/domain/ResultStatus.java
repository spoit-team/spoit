package co.kr.spoit.mat.domain;

/**
 * social_match.result_status 값. 미제출이면 NULL이고, DB에는 상수 이름 그대로 저장한다.
 *
 * <p>시드에는 '확정' 같은 이전 값이 남아 있어 DB 값을 valueOf로 읽지 않고 name()과 비교한다.</p>
 */
public enum ResultStatus {
    SUBMITTED,
    CONFLICT,
    CONFIRMED
}
