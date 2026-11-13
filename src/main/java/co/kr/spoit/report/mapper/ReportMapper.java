package co.kr.spoit.report.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import co.kr.spoit.report.domain.MatchSnapshot;
import co.kr.spoit.report.domain.Report;
import co.kr.spoit.report.domain.ReportMatchRow;
import co.kr.spoit.report.domain.ReportParticipantRow;
import co.kr.spoit.report.domain.ReportRow;

/**
 * 신고 접수·조회·처리 SQL. SQL은 {@code resources/mapper/report/ReportMapper.xml}에 있다.
 *
 * <p>모든 문장은 단일 읽기·쓰기만 수행하고, 소유권·상태 판정은 서비스가 맡는다. 목록 조회는
 * {@code summary}를 선택하지 않아 신고 원문이 목록에 실리지 않는다.</p>
 */
@Mapper
public interface ReportMapper {

    /** 담당 활성 시설의 지정 상태(이용 완료) 경기 선택지를 최신 시작 순으로 한 페이지 조회한다. */
    List<ReportMatchRow> findReportableMatches(@Param("managerUserId") Long managerUserId,
            @Param("matchStatus") String matchStatus, @Param("limit") int limit,
            @Param("offset") int offset);

    /**
     * 담당 활성 시설의 지정 상태 경기 한 건을 선택용으로 조회한다. 페이지 밖 경기를 운영 화면 링크로
     * 골랐을 때만 쓴다. 담당 범위 밖이거나 상태가 다르면 {@code null}이라 다른 경기 정보가 새지 않는다.
     */
    ReportMatchRow findReportableMatch(@Param("managerUserId") Long managerUserId,
            @Param("matchId") Long matchId, @Param("matchStatus") String matchStatus);

    /** 해당 시설의 담당자인지 확인한다. */
    boolean existsFacilityManager(@Param("facilityId") Long facilityId, @Param("userId") Long userId);

    /** 경기 상태 스냅샷을 잠그고 읽는다. 접수 트랜잭션에서 상태를 다시 검사할 때 쓴다. */
    MatchSnapshot findMatchSnapshotForUpdate(@Param("matchId") Long matchId);

    /** 경기 상태 스냅샷을 잠그지 않고 읽는다. 회원 신고 대상 조회에서 상태·참가 여부를 검사할 때 쓴다. */
    MatchSnapshot findMatchSnapshot(@Param("matchId") Long matchId);

    /** 경기의 지정 상태 참가자를 대상 선택지로 조회한다. 한 경기 참가자는 팀 정원으로 제한된다. */
    List<ReportParticipantRow> findParticipants(@Param("matchId") Long matchId,
            @Param("participationStatus") String participationStatus);

    /** 해당 회원이 경기의 지정 상태 참가자인지 확인한다. */
    boolean existsParticipant(@Param("matchId") Long matchId, @Param("userId") Long userId,
            @Param("participationStatus") String participationStatus);

    /**
     * 같은 신고자·경기·대상·유형·원문으로 <strong>DB 현재 시각 기준</strong> 아주 최근
     * {@code windowSeconds}초 안에 만들어진 신고 번호를 찾는다. 없으면 {@code null}이다.
     *
     * <p>{@code report.created_at}은 DB {@code CURRENT_TIMESTAMP}로 저장되므로, 비교도 같은 DB 시계로
     * 해야 세션 타임존 설정과 무관하게 동작한다(애플리케이션 시각을 넘기지 않는다). 시간 조건은 중복
     * 클릭 방어일 뿐 신고 가능 여부를 가르는 게이트가 아니다.</p>
     *
     * <p>{@code report}에는 이 조합의 보조 인덱스가 없으므로 범위 잠금을 걸지 않는다. 범위 잠금은
     * 스캔한 모든 행을 잠가 다른 경기의 접수까지 직렬화한다. 대신 찾은 행만 PK로 다시 잠근다.</p>
     */
    Long findRecentDuplicate(@Param("reporterUserId") Long reporterUserId,
            @Param("matchId") Long matchId, @Param("targetUserId") Long targetUserId,
            @Param("category") String category, @Param("summary") String summary,
            @Param("windowSeconds") int windowSeconds);

    /** 중복 후보 행만 PK로 잠가 확인한다. 없으면 {@code null}이다. */
    Long lockReportById(@Param("reportId") Long reportId);

    /** 신고를 접수 상태로 저장하고 생성된 ID를 {@code report.reportId}에 채운다. */
    int insertReport(Report report);

    /** 신고자가 접수했고 현재 이 관리자의 담당 시설인 신고 목록 한 페이지. 원문은 담지 않는다. */
    List<ReportRow> findReportsForFacilityManager(@Param("managerUserId") Long managerUserId,
            @Param("reporterUserId") Long reporterUserId, @Param("limit") int limit,
            @Param("offset") int offset);

    /** 신고자가 접수했고 현재 이 관리자의 담당 시설인 신고 한 건(원문·처리 사유 포함). */
    ReportRow findFacilityManagerReport(@Param("managerUserId") Long managerUserId,
            @Param("reporterUserId") Long reporterUserId, @Param("reportId") Long reportId);

    /** 시스템 관리자 목록 한 페이지. 지정 상태가 있으면 그 상태만 조회하며 원문은 담지 않는다. */
    List<ReportRow> findSystemReports(@Param("status") String status, @Param("limit") int limit,
            @Param("offset") int offset);

    /** 시스템 관리자 상세 한 건(원문·처리 사유 포함). */
    ReportRow findSystemReport(@Param("reportId") Long reportId);

    /** 처리 대상 신고를 잠그고 읽는다. 없으면 {@code null}이다. */
    Report findReportForUpdate(@Param("reportId") Long reportId);

    /**
     * 기대 상태가 그대로일 때만 검토 중으로 옮긴다. 검토 완료 시각과 사유는 아직 쓰지 않는다.
     * 변경 행 수를 돌려준다.
     */
    int markReviewing(@Param("reportId") Long reportId, @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus, @Param("reviewedBy") Long reviewedBy);

    /**
     * 기대 상태가 그대로일 때만 처리 완료·기각으로 마무리하고 사유·검토자·검토 완료 시각을 남긴다.
     * 변경 행 수를 돌려준다.
     */
    int applyFinalDecision(@Param("reportId") Long reportId, @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus, @Param("resolution") String resolution,
            @Param("reviewedBy") Long reviewedBy);
}
