package co.kr.spoit.member.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import co.kr.spoit.member.domain.AdminRequestRow;
import co.kr.spoit.member.domain.AdminUserRow;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberRole;
import co.kr.spoit.member.dto.AdminUserActionResult;
import co.kr.spoit.member.dto.AdminUserFilters;
import co.kr.spoit.member.dto.AdminUserListItem;
import co.kr.spoit.member.dto.AdminUserMetrics;
import co.kr.spoit.member.dto.AdminUserPage;
import co.kr.spoit.member.dto.AdminUserPagination;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.security.MemberSessionRevocationService;
import co.kr.spoit.report.service.PenaltyService;
import lombok.RequiredArgsConstructor;

/**
 * 시스템 관리자 사용자 관리 화면의 조회·상태 변경을 담당한다.
 *
 * <p>계정 목록·필터·페이지네이션은 {@link MemberMapper}의 {@code app_user} 쿼리로 수행한다.
 * 컨트롤러는 매퍼를 직접 호출하지 않는다. 역할 변경은 {@code MEMBER}·{@code FACILITY_MANAGER}·
 * {@code ADMIN}만 허용하고, 로그인 관리자 번호는 요청 본문이 아니라 인증 주체에서 받는다.</p>
 *
 * <p>안전 규칙: 활성 시스템 관리자 계정은 역할 변경·정지·승인 대상에서 제외하고, 자기 자신의
 * 관리자 권한도 내릴 수 없다. 승인·거절은 대기 중인 {@code admin_request}가 있을 때만 적용하며,
 * 없으면 아무것도 바꾸지 않고 거절 결과를 돌려준다. 역할이 실제로 바뀐 뒤에는
 * {@link MemberSessionRevocationService}로 기존 세션을 만료시켜 낡은 권한이 남지 않게 한다.</p>
 *
 * <p>회원 정지는 기간(1·3·7·14·30일) 또는 영구 정지 선택 값({@code -1})과 사유를 검증하고, {@code penalty} 이력을
 * {@link PenaltyService}로 남긴 뒤 같은 트랜잭션에서 {@code app_user.penalty_time}을 동기화한다.
 * 영구 정지는 종료 시각 센티널({@link MemberPenaltyTimeService#PERMANENT_END_AT})을 두 컬럼에 함께 저장하고,
 * 복구({@link #restore})에서만 해제된다. 회원 행은 {@link MemberAccountLockService}로 잠가 동시 변경과
 * 직렬화한다.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    static final int PAGE_SIZE = 20;
    static final int MAX_PAGE = 1000;
    static final int EXPORT_BATCH_SIZE = 500;
    static final int MAX_EXPORT_ROWS = PAGE_SIZE * (MAX_PAGE + 1);

    private static final int REASON_MIN = 2;
    private static final int REASON_MAX = 200;

    private static final String PENDING = "대기";
    private static final String APPROVED = "승인";
    private static final String REJECTED = "거절";

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final MemberMapper memberMapper;
    private final MemberAccountLockService accountLockService;
    private final PenaltyService penaltyService;
    private final MemberSessionRevocationService memberSessionRevocationService;

    /** 사용자 관리 화면 모델을 만든다. */
    @Transactional(readOnly = true)
    public AdminUserPage loadPage(String q, String role, String status, int page) {
        String keyword = normalizeKeyword(q);
        MemberRole roleFilter = roleFilter(role);
        String statusFilter = statusFilter(status);
        int safePage = normalizePage(page);
        LocalDateTime now = LocalDateTime.now(ZONE);
        String roleCode = roleFilter == null ? null : codeOf(roleFilter);
        String roleLabel = roleFilter == null ? null : roleFilter.label();

        List<AdminUserRow> rows = memberMapper.findAdminUsers(keyword, roleCode, roleLabel, statusFilter,
                now, PAGE_SIZE + 1, offset(safePage));
        List<AdminUserListItem> users = new ArrayList<>(Math.min(rows == null ? 0 : rows.size(), PAGE_SIZE));
        if (rows != null) {
            for (AdminUserRow row : rows) {
                if (users.size() >= PAGE_SIZE) {
                    break;
                }
                if (row != null) {
                    users.add(AdminUserListItem.from(row, now));
                }
            }
        }
        int total = memberMapper.countAdminUsers(keyword, roleCode, roleLabel, statusFilter, now);
        int facilityManagerCount = memberMapper.countActiveUsersByRole(codeOf(MemberRole.FACILITY_MANAGER),
                MemberRole.FACILITY_MANAGER.label());
        int adminCount = memberMapper.countActiveUsersByRole(codeOf(MemberRole.ADMIN), MemberRole.ADMIN.label());
        AdminUserMetrics metrics = new AdminUserMetrics(
                memberMapper.countActiveUsers(),
                memberMapper.countPendingAdminRequests(),
                facilityManagerCount + adminCount,
                adminCount,
                facilityManagerCount,
                memberMapper.countSuspendedUsers(now));
        return new AdminUserPage(metrics,
                new AdminUserFilters(keyword, roleValue(roleFilter), statusValue(statusFilter), safePage),
                users, pagination(safePage, total, keyword, roleFilter, statusFilter));
    }

    /**
     * 내보내기용으로 화면과 같은 필터에 맞는 모든 계정을 모은다.
     *
     * <p>목록 화면과 같은 필터·정렬로 조회하되 내보내기는 한 번에 최대 {@code EXPORT_BATCH_SIZE}건을
     * 읽는다. 기존 페이지 상한에 해당하는 {@code MAX_EXPORT_ROWS}건까지만 내보낸다.</p>
     */
    @Transactional(readOnly = true)
    public List<AdminUserListItem> findAllForExport(String q, String role, String status) {
        String keyword = normalizeKeyword(q);
        MemberRole roleFilter = roleFilter(role);
        String statusFilter = statusFilter(status);
        String roleCode = roleFilter == null ? null : codeOf(roleFilter);
        String roleLabel = roleFilter == null ? null : roleFilter.label();
        LocalDateTime now = LocalDateTime.now(ZONE);

        List<AdminUserListItem> users = new ArrayList<>();
        for (int offset = 0; offset < MAX_EXPORT_ROWS; offset += EXPORT_BATCH_SIZE) {
            int batchSize = Math.min(EXPORT_BATCH_SIZE, MAX_EXPORT_ROWS - offset);
            List<AdminUserRow> rows = memberMapper.findAdminUsers(keyword, roleCode, roleLabel, statusFilter,
                    now, batchSize + 1, offset);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            int added = 0;
            for (AdminUserRow row : rows) {
                if (added >= batchSize) {
                    break;
                }
                if (row != null) {
                    users.add(AdminUserListItem.from(row, now));
                    added++;
                }
            }
            // 추가로 조회한 한 행이 없으면 다음 배치가 없다.
            if (rows.size() <= batchSize || added == 0) {
                break;
            }
        }
        return users;
    }

    /**
     * 역할을 바꾼다. 허용 역할만 받고, 활성 시스템 관리자는 대상에서 제외한다.
     *
     * @param actorUserId 인증 주체의 관리자 번호
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminUserActionResult changeRole(Long actorUserId, Long userId, String roleValue, String reason) {
        requireActor(actorUserId);
        MemberRole targetRole = requireRole(roleValue);
        String trimmedReason = normalizeReason(reason);
        Member target = lockTarget(userId);
        MemberRole currentRole = MemberRole.from(target.getRole());

        if (currentRole == MemberRole.ADMIN) {
            if (userId.equals(actorUserId)) {
                return AdminUserActionResult.rejected("자기 자신의 시스템 관리자 권한은 변경할 수 없습니다.");
            }
            return AdminUserActionResult.rejected("활성 시스템 관리자 계정의 역할은 변경할 수 없습니다.");
        }
        if (targetRole == currentRole) {
            return AdminUserActionResult.rejected("이미 같은 역할입니다.");
        }
        if (memberMapper.updateRole(userId, targetRole.label()) != 1) {
            throw new IllegalStateException("대상 계정의 역할을 변경하지 못했습니다.");
        }
        memberSessionRevocationService.revokeAfterCommit(userId);
        return AdminUserActionResult.applied(
                target.getNickname() + " 계정의 역할을 " + targetRole.label() + "(으)로 변경했습니다.");
    }

    /** 대기 중인 관리자 권한 요청을 승인한다. 요청이 없으면 아무것도 바꾸지 않는다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminUserActionResult approve(Long actorUserId, Long userId) {
        requireActor(actorUserId);
        Member target = lockTarget(userId);
        AdminRequestRow request = memberMapper.findPendingAdminRequestForUpdate(userId);
        if (request == null) {
            return AdminUserActionResult.rejected("대기 중인 관리자 권한 요청이 없습니다.");
        }
        MemberRole requestedRole = MemberRole.from(request.getRequestedRole());
        if (requestedRole == MemberRole.MEMBER) {
            return AdminUserActionResult.rejected("요청된 역할을 확인할 수 없어 승인하지 않았습니다.");
        }
        if (MemberRole.from(target.getRole()) == MemberRole.ADMIN) {
            return AdminUserActionResult.rejected("활성 시스템 관리자 계정의 역할은 변경할 수 없습니다.");
        }
        if (memberMapper.updateRole(userId, requestedRole.label()) != 1) {
            throw new IllegalStateException("대상 계정의 역할을 변경하지 못했습니다.");
        }
        // 잠금 뒤 검토 갱신이 0건이면 다른 관리자가 먼저 처리한 요청이므로 역할 변경도 함께 롤백한다.
        if (memberMapper.reviewAdminRequest(request.getRequestId(), APPROVED, actorUserId) == 0) {
            throw new IllegalStateException("다른 관리자가 먼저 처리한 요청입니다.");
        }
        memberSessionRevocationService.revokeAfterCommit(userId);
        return AdminUserActionResult.applied(
                target.getNickname() + " 계정의 " + requestedRole.label() + " 권한을 승인했습니다.");
    }

    /** 대기 중인 관리자 권한 요청을 거절한다. 요청이 없으면 아무것도 바꾸지 않는다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminUserActionResult reject(Long actorUserId, Long userId) {
        requireActor(actorUserId);
        Member target = lockTarget(userId);
        AdminRequestRow request = memberMapper.findPendingAdminRequestForUpdate(userId);
        if (request == null) {
            return AdminUserActionResult.rejected("대기 중인 관리자 권한 요청이 없습니다.");
        }
        if (memberMapper.reviewAdminRequest(request.getRequestId(), REJECTED, actorUserId) == 0) {
            return AdminUserActionResult.rejected("다른 관리자가 먼저 처리한 요청입니다.");
        }
        return AdminUserActionResult.applied(target.getNickname() + " 계정의 권한 요청을 거절했습니다.");
    }

    /**
     * 회원 계정을 정지한다. 기간(또는 영구 정지)과 사유를 검증하고 제재 이력을 함께 남긴다.
     *
     * @param durationDays 1·3·7·14·30일 중 하나, 또는 영구 정지를 뜻하는
     *                     {@link PenaltyService#PERMANENT_DURATION_DAYS}
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminUserActionResult suspend(Long actorUserId, Long userId, Integer durationDays, String reason) {
        requireActor(actorUserId);
        if (userId != null && userId.equals(actorUserId)) {
            return AdminUserActionResult.rejected("자기 자신의 계정은 정지할 수 없습니다.");
        }
        int days = resolveSuspendDuration(durationDays);
        String trimmedReason = PenaltyService.normalizeReason(reason);
        Member target = lockTarget(userId);
        if (MemberRole.from(target.getRole()) == MemberRole.ADMIN) {
            return AdminUserActionResult.rejected("활성 시스템 관리자 계정은 정지할 수 없습니다.");
        }
        if (target.getPenaltyTime() != null && target.getPenaltyTime().isAfter(LocalDateTime.now(ZONE))) {
            return AdminUserActionResult.rejected("이미 이용이 제한된 계정입니다.");
        }
        penaltyService.issueForAccount(userId, actorUserId, days, trimmedReason);
        String periodLabel = days == PenaltyService.PERMANENT_DURATION_DAYS
                ? "영구 정지"
                : days + "일 정지";
        return AdminUserActionResult.applied(target.getNickname() + " 계정을 " + periodLabel + "했습니다.");
    }

    /** 정지를 해제하고 남은 제재도 함께 정리한다. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminUserActionResult restore(Long actorUserId, Long userId) {
        requireActor(actorUserId);
        Member target = lockTarget(userId);
        LocalDateTime now = LocalDateTime.now(ZONE);
        boolean suspended = target.getPenaltyTime() != null && target.getPenaltyTime().isAfter(now);
        if (!suspended) {
            return AdminUserActionResult.rejected("현재 정지 상태인 계정이 아닙니다.");
        }
        penaltyService.releaseActiveRestrictions(userId, actorUserId);
        return AdminUserActionResult.applied(target.getNickname() + " 계정의 정지를 해제했습니다.");
    }

    /**
     * 정지 기간을 검증한다. 영구 정지 선택 값을 허용하고, 나머지는
     * {@link PenaltyService#requireDuration}의 허용 목록(1·3·7·14·30일)을 따른다.
     *
     * <p>계정 정지 화면에는 영구 정지 선택지도 있으므로, 잘못된 값에는 두 선택지를 모두
     * 열거해 안내한다. 신고 기반 제재가 함께 쓰는 {@link PenaltyService#requireDuration}의
     * 메시지는 바꾸지 않는다.</p>
     */
    private static int resolveSuspendDuration(Integer durationDays) {
        if (durationDays != null && durationDays == PenaltyService.PERMANENT_DURATION_DAYS) {
            return PenaltyService.PERMANENT_DURATION_DAYS;
        }
        if (durationDays == null || !PenaltyService.ALLOWED_DURATIONS.contains(durationDays)) {
            throw new IllegalArgumentException("정지 기간은 1·3·7·14·30일 또는 영구 정지 중에서 선택해 주세요.");
        }
        return PenaltyService.requireDuration(durationDays);
    }

    private void requireActor(Long actorUserId) {
        if (actorUserId == null || actorUserId <= 0) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
    }

    private Member lockTarget(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("대상 계정을 확인해 주세요.");
        }
        Member member = accountLockService.lockById(userId);
        if (member == null || member.getDeletedAt() != null) {
            throw new IllegalArgumentException("대상 계정을 찾을 수 없습니다.");
        }
        return member;
    }

    private static MemberRole requireRole(String roleValue) {
        if (roleValue == null || roleValue.isBlank()) {
            throw new IllegalArgumentException("변경할 역할을 선택해 주세요.");
        }
        String value = roleValue.strip().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "member" -> MemberRole.MEMBER;
            case "facility" -> MemberRole.FACILITY_MANAGER;
            case "system" -> MemberRole.ADMIN;
            default -> throw new IllegalArgumentException("회원·시설 관리자·시스템 관리자 역할만 지정할 수 있습니다.");
        };
    }

    private static MemberRole roleFilter(String role) {
        if (role == null || role.isBlank() || "all".equalsIgnoreCase(role.strip())) {
            return null;
        }
        String value = role.strip().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "member" -> MemberRole.MEMBER;
            case "facility" -> MemberRole.FACILITY_MANAGER;
            case "system" -> MemberRole.ADMIN;
            default -> null;
        };
    }

    private static String statusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.strip().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "pending", "active", "suspended" -> value;
            default -> null;
        };
    }

    static String codeOf(MemberRole role) {
        return switch (role) {
            case MEMBER -> "MEMBER";
            case FACILITY_MANAGER -> "FACILITY_MANAGER";
            case ADMIN -> "ADMIN";
        };
    }

    static String roleValue(MemberRole role) {
        return role == null ? null : AdminUserListItem.value(role);
    }

    static String statusValue(String statusFilter) {
        return statusFilter == null ? null : statusFilter;
    }

    private static String normalizeKeyword(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String value = q.strip();
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("변경 사유를 입력해 주세요.");
        }
        String value = reason.strip();
        if (value.length() < REASON_MIN || value.length() > REASON_MAX) {
            throw new IllegalArgumentException("사유는 2~200자로 입력해 주세요.");
        }
        return value;
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

    private static AdminUserPagination pagination(int page, int total, String keyword, MemberRole role,
            String status) {
        int totalPages = total <= 0 ? 1 : (total + PAGE_SIZE - 1) / PAGE_SIZE;
        boolean lastPage = total > 0 && (page + 1) * PAGE_SIZE >= total;
        String prev = page > 0 ? url(page - 1, keyword, role, status) : null;
        String next = lastPage ? null : url(page + 1, keyword, role, status);
        return new AdminUserPagination(page, totalPages, prev, next);
    }

    private static String url(int page, String keyword, MemberRole role, String status) {
        StringBuilder builder = new StringBuilder("/admin/system?view=users");
        if (keyword != null) {
            builder.append("&q=").append(encode(keyword));
        }
        if (role != null) {
            builder.append("&role=").append(AdminUserListItem.value(role));
        }
        if (status != null) {
            builder.append("&status=").append(status);
        }
        builder.append("&page=").append(page);
        return builder.toString();
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
