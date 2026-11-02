package co.kr.spoit.mat.domain;

/**
 * match_result_submission.submission_status 값. REPLACED는 불일치 뒤 다시 제출해 무효가 된 행이다.
 *
 * <p>ResultStatus와 같은 이유로 DB 값은 name()과 비교한다.</p>
 */
public enum SubmissionStatus {
    SUBMITTED,
    CONFLICT,
    CONFIRMED,
    REPLACED
}
