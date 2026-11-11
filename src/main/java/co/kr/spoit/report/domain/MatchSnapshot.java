package co.kr.spoit.report.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 신고 접수 시 잠금 읽기로 확인하는 경기 상태 스냅샷이다.
 *
 * <p>잠금 뒤 이 값으로 시설 소유·경기 종료를 다시 검사해, 요청 사이에 상태가 바뀐 경우를
 * 막는다.</p>
 */
@Getter
@Setter
public class MatchSnapshot {

    private Long matchId;
    private Long facilityId;
    private String status;
}
