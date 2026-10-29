package co.kr.spoit.match.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * 시설 관리자 매칭방 조회 파라미터.
 *
 * <p>모든 조회는 {@code managerUserId}로 담당 시설 범위를 제한한다. 점유 인원 계산에 쓰는
 * 상태 값도 서비스가 도메인 enum에서 만들어 넘긴다.</p>
 */
@Getter
@Builder
public class RoomManagementQuery {

    private final Long managerUserId;
    /** 상세 조회 대상. 목록 조회에서는 null */
    private final Long matchId;
    /** 검색어(정규화·이스케이프 완료), 없으면 null */
    private final String q;
    private final Long sportId;
    /** 방 상태 DB 값, '전체'면 null */
    private final String statusValue;

    private final String waitingApplicationStatus;
    private final String individualType;
    private final List<String> participationStatuses;
}
