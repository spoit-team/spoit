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
import co.kr.spoit.report.domain.ReportMatchRow;
import co.kr.spoit.report.domain.ReportParticipantRow;
import co.kr.spoit.report.domain.ReportRow;
import co.kr.spoit.report.domain.ReportStatus;
import co.kr.spoit.report.dto.FacilityReportPage;
import co.kr.spoit.report.dto.ReportDetail;
import co.kr.spoit.report.dto.ReportListItem;
import co.kr.spoit.report.dto.ReportMatchOption;
import co.kr.spoit.report.dto.ReportParticipantOption;
import co.kr.spoit.report.mapper.ReportMapper;

/**
 * 시설 관리자의 신고 접수·조회를 담당한다.
 *
 * <p>신고 대상은 본인이 담당하는 <strong>활성 시설</strong>의 <strong>이용 완료({@code 종료})</strong>
 * 경기 중 <strong>확정 참가자</strong>뿐이다. 예정 시각이나 별도 상태값을 게이트로 쓰지 않고, 경기
 * 상태·참가 상태만 확인한다. 관리자 식별자는 항상 인증 주체에서 받는다.</p>
 *
 * <p>접수는 한 트랜잭션에서 경기 행을 먼저 잠근 뒤 소유권·경기 상태·확정 참가 여부를 다시 검사하고,
 * 같은 신고자·경기·대상·유형·원문의 아주 최근(10초 이내) 동일 요청은 후보 행만 PK로 잠가 확인해
 * 기존 결과를 돌려준다. 다른 내용의 사건은 이 조건에 걸리지 않아 정상 접수된다. 새 신고가 저장된
 * 경우에만 접수 알림을 같은 트랜잭션에서 예약한다.</p>
 */
@Service
public class FacilityReportService {

    /** 한 페이지에 보여 주는 경기·신고 건수. 다음 페이지 판정을 위해 DB에는 +1로 요청한다. */
    static final int PAGE_SIZE = 20;

    /** 안전한 페이지 상한. 비정상적으로 큰 page 값이 거대한 offset으로 이어지지 않게 한다. */
    static final int MAX_PAGE = 1000;

    /** offset 상한. PAGE_SIZE × MAX_PAGE를 넘지 않게 해 int overflow와 과도한 스캔을 막는다. */
    private static final int MAX_OFFSET = PAGE_SIZE * MAX_PAGE;

    /** 중복 클릭 방어 창(초). 이 안의 완전히 같은 요청만 기존 결과로 처리한다. */
    static final int DUPLICATE_WINDOW_SECONDS = 10;

    /** 신고 내용 최소·최대 길이. 화면 입력 상한과 같다. */
    private static final int SUMMARY_MIN = 1;

    /**
     * 신고 내용 최대 길이(문자). 컨트롤러가 실패 시 작성 내용을 flash로 보존할 때도 같은 상한을 쓴다.
     * 저장 시 검증과 보존 상한이 어긋나지 않도록 한 곳에서 관리한다.
     */
    public static final int SUMMARY_MAX_LENGTH = 1000;

    private final ReportMapper reportMapper;
    private final ReportNotificationService reportNotificationService;

    public FacilityReportService(ReportMapper reportMapper,
            ReportNotificationService reportNotificationService) {
        this.reportMapper = reportMapper;
        this.reportNotificationService = reportNotificationService;
    }

    /**
     * 시설 관리자 신고 화면 모델을 구성한다.
     *
     * <p>모든 조회는 담당 활성 시설 범위로 제한한다. 경기·신고 목록은 0부터 시작하는 offset 페이지이며,
     * 잘못된 페이지 값은 0으로 정규화한다. 운영 화면 링크로 페이지 밖 종료 경기를 골라도, 단건
     * 소유권·종료 확인을 통과한 경기만 선택지에 덧붙여 대상 선택이 가능하다. {@code selectedReportId}는
     * 본인이 접수했고 현재 담당 시설에 속한 신고만 열린다. 그 밖의 값은
     * {@link ReportNotFoundException}이며, 컨트롤러가 404로 응답한다.</p>
     */
    @Transactional(readOnly = true)
    public FacilityReportPage loadPage(Long managerUserId, Long selectedMatchId, Long selectedTargetUserId,
            Long selectedReportId, int matchPage, int reportPage) {
        if (managerUserId == null || managerUserId <= 0) {
            return FacilityReportPage.empty();
        }
        int safeMatchPage = normalizePage(matchPage);
        int safeReportPage = normalizePage(reportPage);

        List<ReportMatchRow> matchRows = reportMapper.findReportableMatches(managerUserId,
                MatchStatus.CLOSED.dbValue(), PAGE_SIZE + 1, offset(safeMatchPage));
        boolean hasNextMatches = matchRows.size() > PAGE_SIZE;
        List<ReportMatchOption> matches = toMatchOptions(matchRows);
        // 페이지 밖 경기를 골랐으면 담당·종료를 단건으로 다시 확인해 선택지에만 덧붙인다.
        // 다른 경기 정보는 이 단건 쿼리 결과 한 건 외에는 내려가지 않는다.
        if (selectedMatchId != null && selectedMatchId > 0 && !containsMatch(matches, selectedMatchId)) {
            ReportMatchOption linked = linkedReportableMatch(managerUserId, selectedMatchId);
            if (linked != null) {
                List<ReportMatchOption> merged = new ArrayList<>(matches.size() + 1);
                merged.add(linked);
                merged.addAll(matches);
                matches = List.copyOf(merged);
            }
        }
        Long effectiveMatchId = selectableMatchId(matches, selectedMatchId);

        List<ReportParticipantOption> participants = effectiveMatchId == null
                ? List.of()
                : confirmedParticipants(effectiveMatchId);
        Long effectiveTargetUserId = selectableTargetUserId(participants, selectedTargetUserId);

        List<ReportRow> reportRows = reportMapper.findReportsForFacilityManager(
                managerUserId, managerUserId, PAGE_SIZE + 1, offset(safeReportPage));
        boolean hasNextReports = reportRows.size() > PAGE_SIZE;
        List<ReportListItem> reports = reportRows.stream()
                .limit(PAGE_SIZE)
                .map(row -> ReportViewAssembler.toListItem(row, false))
                .toList();

        ReportDetail selectedReport = selectedReportId == null || selectedReportId <= 0
                ? null
                : detail(reportMapper.findFacilityManagerReport(managerUserId, managerUserId, selectedReportId));

        return new FacilityReportPage(matches, participants, reports, effectiveMatchId,
                effectiveTargetUserId, selectedReport, safeMatchPage, hasNextMatches, safeReportPage,
                hasNextReports);
    }

    /**
     * 신고를 접수한다. 중복 클릭으로 같은 요청이 다시 오면 새 행을 만들지 않고 기존 신고 번호를
     * 돌려준다.
     *
     * @return 저장되었거나 중복으로 판정된 신고 번호
     */
    @Transactional
    public long submit(Long managerUserId, Long matchId, Long targetUserId, String category, String summary) {
        if (managerUserId == null || managerUserId <= 0) {
            throw new ReportAccessDeniedException("로그인이 필요합니다.");
        }
        if (matchId == null || matchId <= 0 || targetUserId == null || targetUserId <= 0) {
            throw new IllegalArgumentException("신고할 경기와 대상을 선택해 주세요.");
        }
        ReportCategory reportCategory = ReportCategory.from(category)
                .orElseThrow(() -> new IllegalArgumentException("신고 유형을 선택해 주세요."));
        String content = normalizeSummary(summary);

        // 잠금 순서: social_match 행을 먼저 잠근다. 이 잠금이 같은 경기의 동시 접수를 직렬화하므로,
        // 이후의 읽기(소유권·참가자·중복)는 모두 선행 트랜잭션 커밋 이후 시점의 스냅샷을 본다.
        MatchSnapshot match = reportMapper.findMatchSnapshotForUpdate(matchId);
        if (match == null) {
            throw new ReportAccessDeniedException("신고할 수 있는 경기가 아닙니다.");
        }
        requireManagedActiveFacility(managerUserId, match.getFacilityId());
        requireClosedMatch(match);
        requireConfirmedParticipant(matchId, targetUserId);

        Long duplicateId = reportMapper.findRecentDuplicate(managerUserId, matchId, targetUserId,
                reportCategory.dbValue(), content, DUPLICATE_WINDOW_SECONDS);
        if (duplicateId != null) {
            // 후보 행을 PK로 잠가 확인한다. 같은 요청이 동시에 들어와도 한쪽만 새로 저장한다.
            if (reportMapper.lockReportById(duplicateId) != null) {
                return duplicateId;
            }
        }

        Report report = new Report();
        report.setReporterUserId(managerUserId);
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

    private static List<ReportMatchOption> toMatchOptions(List<ReportMatchRow> rows) {
        List<ReportMatchOption> options = new ArrayList<>(Math.min(rows.size(), PAGE_SIZE));
        for (ReportMatchRow row : rows) {
            if (row != null && options.size() < PAGE_SIZE) {
                options.add(new ReportMatchOption(row.getMatchId(), row.getFacilityName(), row.getTitle(),
                        row.getStartAt()));
            }
        }
        return List.copyOf(options);
    }

    /**
     * 페이지 밖 경기를 링크로 골랐을 때 그 한 건만 담당·종료 조건으로 다시 확인한다.
     * 조건을 통과하지 못하면 {@code null}이며, 선택지에 아무 것도 추가하지 않는다.
     */
    private ReportMatchOption linkedReportableMatch(Long managerUserId, Long matchId) {
        ReportMatchRow row = reportMapper.findReportableMatch(managerUserId, matchId,
                MatchStatus.CLOSED.dbValue());
        if (row == null) {
            return null;
        }
        return new ReportMatchOption(row.getMatchId(), row.getFacilityName(), row.getTitle(), row.getStartAt());
    }

    private static boolean containsMatch(List<ReportMatchOption> matches, Long matchId) {
        for (ReportMatchOption match : matches) {
            if (matchId.equals(match.getMatchId())) {
                return true;
            }
        }
        return false;
    }

    private List<ReportParticipantOption> confirmedParticipants(Long matchId) {
        // 경기당 확정 참가자는 팀 정원으로 제한되고, 이 조회는 담당·종료가 검증된 경기에만 호출한다.
        // 임의 건수 절단을 두면 뒤쪽 참가자 신고가 불가능해지므로 전체를 읽는다.
        List<ReportParticipantRow> rows = reportMapper.findParticipants(matchId,
                ParticipationStatus.CONFIRMED.dbValue());
        List<ReportParticipantOption> options = new ArrayList<>(rows.size());
        for (ReportParticipantRow row : rows) {
            if (row != null && row.getUserId() != null) {
                options.add(new ReportParticipantOption(row.getUserId(), row.getNickname()));
            }
        }
        return List.copyOf(options);
    }

    /** 0부터 시작하는 페이지를 {@code [0, MAX_PAGE]}로 정규화한다. 음수·과대 값은 안전 범위로 자른다. */
    static int normalizePage(int page) {
        if (page < 0) {
            return 0;
        }
        return Math.min(page, MAX_PAGE);
    }

    /** 정규화된 페이지의 offset. 상한을 넘지 않아 곱셈 overflow가 없다. */
    static int offset(int normalizedPage) {
        long offset = (long) normalizedPage * PAGE_SIZE;
        return offset > MAX_OFFSET ? MAX_OFFSET : (int) offset;
    }

    private static Long selectableMatchId(List<ReportMatchOption> matches, Long requested) {
        if (requested == null || requested <= 0) {
            return null;
        }
        for (ReportMatchOption match : matches) {
            if (requested.equals(match.getMatchId())) {
                return requested;
            }
        }
        return null;
    }

    private static Long selectableTargetUserId(List<ReportParticipantOption> participants, Long requested) {
        if (requested == null || requested <= 0) {
            return null;
        }
        for (ReportParticipantOption participant : participants) {
            if (requested.equals(participant.getUserId())) {
                return requested;
            }
        }
        return null;
    }

    private void requireManagedActiveFacility(Long managerUserId, Long facilityId) {
        if (facilityId == null || !reportMapper.existsFacilityManager(facilityId, managerUserId)) {
            throw new ReportAccessDeniedException("담당 시설의 경기만 신고할 수 있습니다.");
        }
    }

    private static void requireClosedMatch(MatchSnapshot match) {
        if (!MatchStatus.CLOSED.dbValue().equals(match.getStatus())) {
            throw new ReportAccessDeniedException("이용이 끝난 경기만 신고할 수 있습니다.");
        }
    }

    private void requireConfirmedParticipant(Long matchId, Long targetUserId) {
        if (!reportMapper.existsParticipant(matchId, targetUserId,
                ParticipationStatus.CONFIRMED.dbValue())) {
            throw new ReportAccessDeniedException("해당 경기의 확정 참가자만 신고할 수 있습니다.");
        }
    }

    private static String normalizeSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("신고 내용을 입력해 주세요.");
        }
        String value = summary.strip();
        if (value.length() < SUMMARY_MIN || value.length() > SUMMARY_MAX_LENGTH) {
            throw new IllegalArgumentException("신고 내용은 1~1000자로 입력해 주세요.");
        }
        return value;
    }

    private static ReportDetail detail(ReportRow row) {
        if (row == null) {
            throw new ReportNotFoundException("신고를 찾을 수 없습니다.");
        }
        return ReportViewAssembler.toDetail(row);
    }
}
