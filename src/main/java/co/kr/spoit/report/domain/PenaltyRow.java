package co.kr.spoit.report.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 제재 이력 목록 조회 SQL의 결과 행이다. 대상 회원의 표시 이름을 조인해 담는다.
 *
 * <p>{@code active}는 DB 현재 시각 기준으로 적용 중인지 계산한 값이다. 기간이 지난
 * {@code 적용 중} 행은 화면에서 만료로 표시하기 위해 이 값을 쓴다.</p>
 */
@Getter
@Setter
public class PenaltyRow {

    private Long penaltyId;
    private Long targetUserId;
    private String account;
    private String email;
    private Long reportId;
    private String penaltyType;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String status;
    private String reason;
    private Boolean active;
}
