package co.kr.spoit.report.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.match.domain.MatchStatus;
import co.kr.spoit.match.domain.ParticipationStatus;
import co.kr.spoit.notification.service.ReportNotificationService;
import co.kr.spoit.report.domain.MatchSnapshot;
import co.kr.spoit.report.domain.Report;
import co.kr.spoit.report.domain.ReportCategory;
import co.kr.spoit.report.domain.ReportParticipantRow;
import co.kr.spoit.report.domain.ReportStatus;
import co.kr.spoit.report.dto.ReportSubmitRequest;
import co.kr.spoit.report.dto.ReportTargetResponse;
import co.kr.spoit.report.mapper.ReportMapper;

/**
 * 경기 결과 화면에서 회원이 다른 확정 참가자를 신고하는 흐름을 담당한다.
 *
 * <p>신고자는 항상 인증 주체에서만 받는다. 신고 대상은 <strong>이용 완료({@code 종료})</strong> 경기의
 * <strong>확정 참가자</strong> 중 신고자 본인을 제외한 회원뿐이다. 대상 선택지 조회도 같은 조건을
 * 먼저 검사한 뒤에만 참가자 목록을 읽으므로, 권한 없는 요청에 다른 경기의 참가자 정보가 새지 않는다.</p>
 *
 * <p>접수는 시설 관리자 신고와 같은 트랜잭션 규칙을 따른다. 경기 행을 먼저 잠근 뒤 상태·신고자·대상의
 * 확정 참가 여부를 다시 검사하고, 같은 신고자·경기·대상·유형·원문의 아주 최근(10초 이내) 동일 요청은
 * 새 행 없이 기존 신고 번호를 돌려준다. 새 신고가 저장된 경우에만 접수 알림을 같은 트랜잭션에서
 * 예약한다.</p>
 */
@Service
public class MemberReportService {

    /** 중복 클릭 방어 창(초). 시설 관리자 신고와 같은 창을 쓴다. */
    static final int DUPLICATE_WINDOW_SECONDS = FacilityReportService.DUPLICATE_WINDOW_SECONDS;

    private final ReportMapper reportMapper;
    private final ReportNotificationService reportNotificationService;

    public MemberReportService(ReportMapper reportMapper,
            ReportNotificationService reportNotificationService) {
        this.reportMapper = reportMapper;
        this.reportNotificationService = reportNotificationService;
    }

    /**
     * 신고 대상 선택지를 조회한다. 인증 주체가 그 경기의 확정 참가자이고 경기가 종료된 경우에만
     * 본인을 제외한 확정 참가자를 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<ReportTargetResponse> loadTargets(Long reporterUserId, Long matchId) {
        requireReporter(reporterUserId);
        requireMatchId(matchId);

        MatchSnapshot match = reportMapper.findMatchSnapshot(matchId);
        if (match == null) {
            throw new ReportNotFoundException("경기 정보를 찾을 수 없습니다.");
        }
        requireClosedMatch(match);
        requireReporterParticipant(matchId, reporterUserId);

        List<ReportParticipantRow> rows = reportMapper.findParticipants(matchId,
                ParticipationStatus.CONFIRMED.dbValue());
        List<ReportTargetResponse> targets = new ArrayList<>(rows.size());
        for (ReportParticipantRow row : rows) {
            if (row == null || row.getUserId() == null) {
                continue;
            }
            // 본인은 신고 대상이 아니다. 목록에서 빼 다른 참가자 정보만 노출한다.
            if (row.getUserId().equals(reporterUserId)) {
                continue;
            }
            targets.add(new ReportTargetResponse(row.getUserId(), row.getNickname()));
        }
        return List.copyOf(targets);
    }

    /**
     * 신고를 접수한다. 같은 요청이 짧은 창 안에 다시 오면 새 행을 만들지 않고 기존 신고 번호를 돌려준다.
     *
     * @return 저장되었거나 중복으로 판정된 신고 번호
     */
    @Transactional
    public long submit(Long reporterUserId, Long matchId, ReportSubmitRequest request) {
        requireReporter(reporterUserId);
        requireMatchId(matchId);
        if (request == null || request.targetUserId() == null || request.targetUserId() <= 0) {
            throw new IllegalArgumentException("신고할 대상을 선택해 주세요.");
        }
        ReportCategory reportCategory = ReportCategory.from(request.category())
                .orElseThrow(() -> new IllegalArgumentException("신고 유형을 선택해 주세요."));
        String content = FacilityReportService.normalizeSummary(request.summary());

        Long targetUserId = request.targetUserId();
        if (targetUserId.equals(reporterUserId)) {
            throw new ReportAccessDeniedException("본인은 신고할 수 없습니다.");
        }

        // 잠금 순서는 시설 관리자 신고와 같다. 경기 행을 먼저 잠가 같은 경기의 동시 접수를 직렬화한다.
        MatchSnapshot match = reportMapper.findMatchSnapshotForUpdate(matchId);
        if (match == null) {
            throw new ReportAccessDeniedException("신고할 수 있는 경기가 아닙니다.");
        }
        requireClosedMatch(match);
        requireReporterParticipant(matchId, reporterUserId);
        requireTargetParticipant(matchId, targetUserId);

        Long duplicateId = reportMapper.findRecentDuplicate(reporterUserId, matchId, targetUserId,
                reportCategory.dbValue(), content, DUPLICATE_WINDOW_SECONDS);
        if (duplicateId != null) {
            // 후보 행만 PK로 잠가 확인한다. 같은 요청이 동시에 들어와도 한쪽만 새로 저장한다.
            if (reportMapper.lockReportById(duplicateId) != null) {
                return duplicateId;
            }
        }

        Report report = new Report();
        report.setReporterUserId(reporterUserId);
        report.setTargetUserId(targetUserId);
        report.setMatchId(matchId);
        report.setCategory(reportCategory.dbValue());
        report.setSummary(content);
        report.setStatus(ReportStatus.RECEIVED.dbValue());
        reportMapper.insertReport(report);
        Long reportId = report.getReportId();
        if (reportId == null) {
            throw new IllegalStateException("신고 저장 후 생성 ID를 받지 못했습니다.");
        }

        // 실제 접수에서만 알림을 예약한다. 같은 트랜잭션이라 저장이 롤백되면 알림도 남지 않는다.
        reportNotificationService.notifySubmitted(reportId);
        return reportId;
    }

    private static void requireReporter(Long reporterUserId) {
        if (reporterUserId == null || reporterUserId <= 0) {
            throw new ReportAccessDeniedException("로그인이 필요합니다.");
        }
    }

    private static void requireMatchId(Long matchId) {
        if (matchId == null || matchId <= 0) {
            throw new IllegalArgumentException("신고할 경기를 선택해 주세요.");
        }
    }

    private static void requireClosedMatch(MatchSnapshot match) {
        if (!MatchStatus.CLOSED.dbValue().equals(match.getStatus())) {
            throw new ReportAccessDeniedException("이용이 끝난 경기만 신고할 수 있습니다.");
        }
    }

    private void requireReporterParticipant(Long matchId, Long reporterUserId) {
        if (!reportMapper.existsParticipant(matchId, reporterUserId,
                ParticipationStatus.CONFIRMED.dbValue())) {
            throw new ReportAccessDeniedException("이 경기의 확정 참가자만 신고할 수 있습니다.");
        }
    }

    private void requireTargetParticipant(Long matchId, Long targetUserId) {
        if (!reportMapper.existsParticipant(matchId, targetUserId,
                ParticipationStatus.CONFIRMED.dbValue())) {
            throw new ReportAccessDeniedException("해당 경기의 확정 참가자만 신고 대상이 될 수 있습니다.");
        }
    }
}
