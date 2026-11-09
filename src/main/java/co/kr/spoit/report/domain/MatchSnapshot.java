package co.kr.spoit.report.domain;

/**
 * 신고 접수 시 잠금 읽기로 확인하는 경기 상태 스냅샷이다.
 *
 * <p>잠금 뒤 이 값으로 시설 소유·경기 종료를 다시 검사해, 요청 사이에 상태가 바뀐 경우를
 * 막는다.</p>
 */
public class MatchSnapshot {

    private Long matchId;
    private Long facilityId;
    private String status;

    public Long getMatchId() {
        return matchId;
    }

    public void setMatchId(Long matchId) {
        this.matchId = matchId;
    }

    public Long getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(Long facilityId) {
        this.facilityId = facilityId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
