package co.kr.spoit.report.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberRole;
import co.kr.spoit.member.service.MemberPenaltyTimeService;
import co.kr.spoit.report.domain.Penalty;
import co.kr.spoit.report.domain.PenaltyRow;
import co.kr.spoit.report.dto.PenaltyFilters;
import co.kr.spoit.report.dto.PenaltyListItem;
import co.kr.spoit.report.dto.PenaltyMetrics;
import co.kr.spoit.report.dto.PenaltyPage;
import co.kr.spoit.report.mapper.PenaltyMapper;

/**
 * 제재(경고·이용 제한) 부여·조회·조기 해제를 담당한다.
 *
 * <p>{@code penalty} 행은 이 도메인이 소유하고, 실제 로그인 차단에 쓰이는
 * {@code app_user.penalty_time}은 회원 도메인의 {@link MemberPenaltyTimeService}를 통해서만
 * 바꾼다. 이용 제한일 때만 회원 컬럼을 동기화하며, 경고는 이력만 남긴다.</p>
 *
 * <p>조기 해제는 회원 행을 먼저 잠그고 제재 행을 잠근 뒤 상태를 판정하며, 해제 뒤 남은 적용 중 이용
 * 제한은 같은 잠금 경계 안에서 <strong>현재 읽기(잠금 읽기)</strong>로 다시 조회해 {@code penalty_time}을
 * 재계산한다. REPEATABLE READ 스냅샷을 쓰면 방금 해제한 행이 여전히 '적용 중'으로 보여 정지가 풀리지
 * 않는다. 남은 제재가 없으면 {@code null}로 풀어 인증이 다시 통과하게 한다.</p>
 *
 * <p>영구 정지는 지원하지 않는다. 이용 제한은 {@link #ALLOWED_DURATIONS}일 중 하나만 허용하고,
 * 경고는 기간 없이 기록한다. 이 클래스는 감사 로그를 쓰지 않는다(감사 화면 제거 요청).</p>
 */
@Service
public class PenaltyService {

    /** 이용 제한으로 허용하는 기간(일)이다. */
    public static final Set<Integer> ALLOWED_DURATIONS = Set.of(1, 3, 7, 14, 30);

    public static final String TYPE_WARNING = "경고";
    public static final String TYPE_RESTRICTION = "이용 제한";

    public static final String STATUS_ACTIVE = "적용 중";
    public static final String STATUS_RELEASED = "해제";

    /** 처리 사유 최소·최대 길이. */
    private static final int REASON_MIN = 2;
    private static final int REASON_MAX = 500;

    static final int PAGE_SIZE = 20;
    static final int MAX_PAGE = 1000;

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final PenaltyMapper penaltyMapper;
    private final MemberPenaltyTimeService memberPenaltyTimeService;

    public PenaltyService(PenaltyMapper penaltyMapper, MemberPenaltyTimeService memberPenaltyTimeService) {
        this.penaltyMapper = penaltyMapper;
        this.memberPenaltyTimeService = memberPenaltyTimeService;
    }

    /** 페널티 관리 화면 모델을 만든다. */
    @Transactional(readOnly = true)
    public PenaltyPage loadPage(String q, String status, int page) {
        String normalizedStatus = normalizeStatus(status);
        String keyword = normalizeKeyword(q);
        int safePage = normalizePage(page);
        LocalDateTime now = LocalDateTime.now(ZONE);

        List<PenaltyRow> rows = penaltyMapper.findPenalties(keyword, normalizedStatus, now,
                PAGE_SIZE + 1, offset(safePage));
        List<PenaltyListItem> items = new ArrayList<>(Math.min(rows == null ? 0 : rows.size(), PAGE_SIZE));
        if (rows != null) {
            for (PenaltyRow row : rows) {
                if (items.size() >= PAGE_SIZE) {
                    break;
                }
                if (row != null) {
                    items.add(toListItem(row, now));
                }
            }
        }
        // pageSize+1건을 요청해 초과분이 있으면 다음 페이지가 있다고 표시하고, 화면에는 pageSize건만 노출한다.
        boolean hasNextPenalties = rows != null && rows.size() > PAGE_SIZE;
        PenaltyMetrics metrics = new PenaltyMetrics(
                penaltyMapper.countActiveRestrictedUsers(now),
                penaltyMapper.countWarnings(),
                penaltyMapper.countIssuedSince(now.withDayOfMonth(1).toLocalDate().atStartOfDay()));
        return new PenaltyPage(metrics, new PenaltyFilters(keyword, normalizedStatus, safePage), items,
                hasNextPenalties);
    }

    /**
     * 신고 처리 결과로 제재를 부여한다.
     *
     * @param reportId    근거 신고 번호
     * @param targetUserId 서버가 신고 상세에서 확인한 대상 회원 번호(클라이언트 값 아님)
     * @param issuedBy    부여한 시스템 관리자 번호
     * @param penaltyType {@code 경고} 또는 {@code 이용 제한}
     * @param durationDays 이용 제한 기간(1/3/7/14/30). 경고에서는 무시한다
     * @param reason      처리 사유
     */
    @Transactional
    public Long issueForReport(Long reportId, Long targetUserId, Long issuedBy, String penaltyType,
            Integer durationDays, String reason) {
        if (reportId == null || reportId <= 0) {
            throw new IllegalArgumentException("신고 번호를 확인해 주세요.");
        }
        return issue(reportId, targetUserId, issuedBy, penaltyType, durationDays, reason);
    }

    /** 신고와 무관한 계정 정지(사용자 관리 화면)로 제재 이력을 남긴다. */
    @Transactional
    public Long issueForAccount(Long targetUserId, Long issuedBy, int durationDays, String reason) {
        return issue(null, targetUserId, issuedBy, TYPE_RESTRICTION, durationDays, reason);
    }

    /**
     * 제재를 조기 해제하고 남은 이용 제한에 맞춰 {@code penalty_time}을 재계산한다.
     *
     * @return 해제 뒤 적용될 이용정지 종료 시각(없으면 {@code null})
     */
    @Transactional
    public LocalDateTime release(Long penaltyId, Long releasedBy) {
        if (penaltyId == null || penaltyId <= 0) {
            throw new IllegalArgumentException("해제할 제재를 확인해 주세요.");
        }
        if (releasedBy == null || releasedBy <= 0) {
            throw new ReportAccessDeniedException("로그인이 필요합니다.");
        }
        // 잠금 순서를 회원 → 제재로 통일한다. 제재만 먼저 잠그면 같은 회원을 다루는 다른 경로와
        // 교착이 생길 수 있어, 대상 회원을 먼저 확인해 잠근 뒤 제재 행을 잠근다.
        Penalty preview = penaltyMapper.findPenalty(penaltyId);
        if (preview == null) {
            throw new ReportNotFoundException("제재 기록을 찾을 수 없습니다.");
        }
        memberPenaltyTimeService.lock(preview.getTargetUserId());
        Penalty penalty = penaltyMapper.findPenaltyForUpdate(penaltyId);
        if (penalty == null) {
            throw new ReportNotFoundException("제재 기록을 찾을 수 없습니다.");
        }
        if (!STATUS_ACTIVE.equals(penalty.getStatus())) {
            throw new ReportStateConflictException("이미 해제되었거나 만료된 제재입니다.");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        int changed = penaltyMapper.releasePenalty(penaltyId, releasedBy, now);
        if (changed == 0) {
            throw new ReportStateConflictException(
                    "다른 관리자가 먼저 처리해 제재 상태가 바뀌었습니다. 화면을 새로 고친 뒤 다시 시도해 주세요.");
        }
        LocalDateTime remaining = penaltyMapper.findLatestActiveRestrictionEndAtForUpdate(
                penalty.getTargetUserId(), now);
        // 경고에는 회원 이용정지 컬럼이 연결되지 않으므로 건드리지 않는다.
        if (TYPE_RESTRICTION.equals(penalty.getPenaltyType())) {
            memberPenaltyTimeService.overwrite(penalty.getTargetUserId(), remaining);
        }
        return remaining;
    }

    /**
     * 회원의 적용 중인 이용 제한을 모두 해제하고 {@code penalty_time}을 {@code null}로 푼다.
     *
     * <p>사용자 관리 화면의 정지 해제에서 호출한다. 남은 제재가 없으므로 인증이 다시 통과한다.</p>
     *
     * @return 해제한 제재 수
     */
    @Transactional
    public int releaseActiveRestrictions(Long targetUserId, Long releasedBy) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new IllegalArgumentException("대상 회원을 확인해 주세요.");
        }
        if (releasedBy == null || releasedBy <= 0) {
            throw new ReportAccessDeniedException("로그인이 필요합니다.");
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        // 회원 행을 먼저 잠가 제재 해제와 회원 컬럼 반영을 같은 잠금 경계에서 직렬화한다.
        memberPenaltyTimeService.lock(targetUserId);
        int released = penaltyMapper.releaseActiveRestrictions(targetUserId, releasedBy, now);
        memberPenaltyTimeService.overwrite(targetUserId, null);
        return released;
    }

    private Long issue(Long reportId, Long targetUserId, Long issuedBy, String penaltyType,
            Integer durationDays, String reason) {
        if (targetUserId == null || targetUserId <= 0) {
            throw new IllegalArgumentException("제재 대상 회원을 확인할 수 없습니다.");
        }
        if (issuedBy == null || issuedBy <= 0) {
            throw new ReportAccessDeniedException("로그인이 필요합니다.");
        }
        String type = normalizeType(penaltyType);
        String trimmedReason = normalizeReason(reason);
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDateTime endAt = TYPE_RESTRICTION.equals(type)
                ? now.plusDays(requireDuration(durationDays))
                : null;

        // 잠금 순서를 회원 → 제재로 통일한다. 경고도 이력이 남으므로 회원 행을 먼저 잠가 역할을 확인하고,
        // 시스템 관리자 계정에는 어떤 제재도 부여하지 않는다.
        Member target = memberPenaltyTimeService.lock(targetUserId);
        if (MemberRole.from(target.getRole()) == MemberRole.ADMIN) {
            throw new IllegalArgumentException("시스템 관리자 계정에는 제재를 부여할 수 없습니다.");
        }

        Penalty penalty = new Penalty();
        penalty.setTargetUserId(targetUserId);
        penalty.setReportId(reportId);
        penalty.setIssuedBy(issuedBy);
        penalty.setPenaltyType(type);
        penalty.setStartAt(now);
        penalty.setEndAt(endAt);
        penalty.setStatus(STATUS_ACTIVE);
        penalty.setReason(trimmedReason);

        penaltyMapper.insertPenalty(penalty);

        // 이용 제한만 실제 로그인 차단 컬럼과 동기화한다. 경고는 이력만 남긴다.
        if (endAt != null) {
            memberPenaltyTimeService.extendUntil(targetUserId, endAt);
        }
        return penalty.getPenaltyId();
    }

    static String normalizeType(String penaltyType) {
        if (penaltyType == null || penaltyType.isBlank()) {
            throw new IllegalArgumentException("제재 종류를 선택해 주세요.");
        }
        String value = penaltyType.strip();
        if (TYPE_WARNING.equals(value) || TYPE_RESTRICTION.equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("제재 종류는 경고 또는 이용 제한만 선택할 수 있습니다.");
    }

    public static int requireDuration(Integer durationDays) {
        if (durationDays == null || !ALLOWED_DURATIONS.contains(durationDays)) {
            throw new IllegalArgumentException("이용 제한 기간은 1·3·7·14·30일 중에서 선택해 주세요.");
        }
        return durationDays;
    }

    public static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("제재 사유를 입력해 주세요.");
        }
        String value = reason.strip();
        if (value.length() < REASON_MIN || value.length() > REASON_MAX) {
            throw new IllegalArgumentException("제재 사유는 2~500자로 입력해 주세요.");
        }
        return value;
    }

    static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "all";
        }
        String value = status.strip().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "active", "done", "released" -> value;
            default -> "all";
        };
    }

    static int normalizePage(int page) {
        if (page < 0) {
            return 0;
        }
        return Math.min(page, MAX_PAGE);
    }

    static int offset(int normalizedPage) {
        long value = (long) normalizedPage * PAGE_SIZE;
        long max = (long) PAGE_SIZE * MAX_PAGE;
        return value > max ? (int) max : (int) value;
    }

    private static String normalizeKeyword(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String value = q.strip();
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private static PenaltyListItem toListItem(PenaltyRow row, LocalDateTime now) {
        boolean active = Boolean.TRUE.equals(row.getActive());
        boolean warning = TYPE_WARNING.equals(row.getPenaltyType());
        String statusLabel;
        String statusTone;
        if (STATUS_RELEASED.equals(row.getStatus())) {
            statusLabel = "해제";
            statusTone = "neutral";
        } else if (active) {
            statusLabel = "적용 중";
            statusTone = warning ? "warn" : "bad";
        } else {
            statusLabel = "만료";
            statusTone = "neutral";
        }
        return new PenaltyListItem(
                row.getPenaltyId(),
                row.getTargetUserId(),
                row.getAccount(),
                row.getEmail(),
                warning ? "경고" : "이용 제한",
                PenaltyListItem.format(row.getStartAt()),
                PenaltyListItem.format(row.getEndAt()),
                row.getReportId(),
                row.getReason(),
                statusLabel,
                statusTone,
                active);
    }
}
