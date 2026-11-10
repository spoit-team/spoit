package co.kr.spoit.report.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.report.domain.Penalty;
import co.kr.spoit.report.domain.PenaltyRow;

/**
 * {@code penalty} 테이블 조회·저장 SQL. SQL은 {@code resources/mapper/report/PenaltyMapper.xml}에 있다.
 *
 * <p>각 문장은 단일 읽기·쓰기만 수행한다. 기간·상태 판정 같은 흐름 로직은 서비스가 맡고, 여기서는
 * 조건과 정렬·페이징만 SQL로 표현한다.</p>
 */
@Mapper
public interface PenaltyMapper {

    /** 제재 이력 한 페이지. 검색어와 상태 조건을 SQL에 반영하고, 최신 부여 순으로 자른다. */
    List<PenaltyRow> findPenalties(@Param("keyword") String keyword, @Param("status") String status,
            @Param("now") LocalDateTime now, @Param("limit") int limit, @Param("offset") int offset);

    /** 현재 적용 중인 이용 제한 사용자 수. */
    int countActiveRestrictedUsers(@Param("now") LocalDateTime now);

    /** 누적 경고 기록 수. */
    int countWarnings();

    /** 지정 시각 이후 부여된 제재 수. */
    int countIssuedSince(@Param("since") LocalDateTime since);

    /** 해제 대상 제재를 잠그지 않고 읽는다. 잠금 순서(회원 → 제재)를 맞추기 위한 사전 조회다. */
    Penalty findPenalty(@Param("penaltyId") Long penaltyId);

    /** 해제 대상 제재를 잠그고 읽는다. 없으면 {@code null}이다. */
    Penalty findPenaltyForUpdate(@Param("penaltyId") Long penaltyId);

    /**
     * 해당 회원의 적용 중인 이용 제한 중 가장 늦은 종료 시각을 잠그고 읽는다. 없으면 {@code null}.
     *
     * <p>잠금 읽기(현재 읽기)이므로 REPEATABLE READ 트랜잭션의 스냅샷이 아니라 커밋된 최신 값을 본다.
     * 조기 해제가 방금 바꾼 상태를 반영해야 하기 때문이다.</p>
     */
    LocalDateTime findLatestActiveRestrictionEndAtForUpdate(@Param("targetUserId") Long targetUserId,
            @Param("now") LocalDateTime now);

    /** 제재를 저장하고 생성된 ID를 {@code penalty.penaltyId}에 채운다. */
    int insertPenalty(Penalty penalty);

    /** 조기 해제를 기록한다. 적용 중인 행만 바뀌며, 바뀐 행 수를 돌려준다. */
    int releasePenalty(@Param("penaltyId") Long penaltyId, @Param("releasedBy") Long releasedBy,
            @Param("releasedAt") LocalDateTime releasedAt);

    /** 회원의 적용 중인 이용 제한을 한 번에 모두 해제한다. 바뀐 행 수를 돌려준다. */
    int releaseActiveRestrictions(@Param("targetUserId") Long targetUserId,
            @Param("releasedBy") Long releasedBy, @Param("releasedAt") LocalDateTime releasedAt);
}
