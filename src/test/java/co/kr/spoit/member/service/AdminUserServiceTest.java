package co.kr.spoit.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.member.domain.AdminRequestRow;
import co.kr.spoit.member.domain.AdminUserRow;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberRole;
import co.kr.spoit.member.dto.AdminUserActionResult;
import co.kr.spoit.member.dto.AdminUserListItem;
import co.kr.spoit.member.dto.AdminUserPage;
import co.kr.spoit.member.mapper.MemberMapper;
import co.kr.spoit.member.security.MemberSessionRevocationService;
import co.kr.spoit.report.service.PenaltyService;

/**
 * 사용자 관리의 역할 변경·승인·거절·정지·복구 규칙을 검증한다.
 *
 * <p>허용 역할만 받고, 활성 시스템 관리자는 역할 변경·정지·승인 대상에서 제외하며, 대기 중인
 * {@code admin_request}가 없으면 승인·거절이 아무것도 바꾸지 않는다. 역할이 바뀐 경우에만 세션을
 * 만료시키고, 승인 검토 갱신이 0건이면 예외로 롤백한다. 정지는 기간·사유를 검증하고 제재 이력을
 * 남긴다. 내보내기는 페이지 상한까지 모든 일치 계정을 모은다.</p>
 */
class AdminUserServiceTest {

    private static final long ACTOR = 3L;
    private static final long TARGET = 11L;

    private final MemberMapper memberMapper = mock(MemberMapper.class);
    private final MemberAccountLockService accountLockService = mock(MemberAccountLockService.class);
    private final PenaltyService penaltyService = mock(PenaltyService.class);
    private final MemberSessionRevocationService sessionRevocationService =
            mock(MemberSessionRevocationService.class);
    private final AdminUserService service = new AdminUserService(memberMapper, accountLockService,
            penaltyService, sessionRevocationService);

    @BeforeEach
    void stubDefaults() {
        when(memberMapper.findAdminUsers(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(memberMapper.countAdminUsers(any(), any(), any(), any(), any())).thenReturn(0);
        when(memberMapper.countActiveUsers()).thenReturn(0);
        when(memberMapper.countPendingAdminRequests()).thenReturn(0);
        when(memberMapper.countActiveUsersByRole(any(), any())).thenReturn(0);
        when(memberMapper.countSuspendedUsers(any())).thenReturn(0);
        when(memberMapper.updateRole(anyLong(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("일반 회원을 시설 관리자로 바꾼다")
    void promotesMemberToFacilityManager() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));

        AdminUserActionResult result = service.changeRole(ACTOR, TARGET, "facility", "시설 담당 승인");

        assertThat(result.applied()).isTrue();
        verify(memberMapper).updateRole(TARGET, MemberRole.FACILITY_MANAGER.label());
        verify(sessionRevocationService).revokeAfterCommit(TARGET);
    }

    @Test
    @DisplayName("허용되지 않은 역할 값은 거부한다")
    void rejectsUnknownRole() {
        assertThatThrownBy(() -> service.changeRole(ACTOR, TARGET, "superuser", "사유"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(memberMapper);
    }

    @Test
    @DisplayName("변경 사유가 비어 있으면 거부한다")
    void rejectsEmptyReason() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));

        assertThatThrownBy(() -> service.changeRole(ACTOR, TARGET, "member", "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(memberMapper, never()).updateRole(anyLong(), any());
    }

    @Test
    @DisplayName("활성 시스템 관리자 계정은 역할 변경 대상에서 제외한다")
    void rejectsChangingSystemAdminRole() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("시스템 관리자"));

        AdminUserActionResult result = service.changeRole(ACTOR, TARGET, "member", "강등");

        assertThat(result.applied()).isFalse();
        assertThat(result.message()).contains("시스템 관리자");
        verify(memberMapper, never()).updateRole(anyLong(), any());
        verify(sessionRevocationService, never()).revokeAfterCommit(anyLong());
    }

    @Test
    @DisplayName("자기 자신의 관리자 권한은 내릴 수 없다")
    void rejectsDemotingSelf() {
        when(accountLockService.lockById(ACTOR)).thenReturn(member("시스템 관리자"));

        AdminUserActionResult result = service.changeRole(ACTOR, ACTOR, "member", "강등");

        assertThat(result.applied()).isFalse();
        verify(memberMapper, never()).updateRole(anyLong(), any());
    }

    @Test
    @DisplayName("이미 같은 역할로 바꾸는 요청은 아무것도 바꾸지 않고 거절한다")
    void rejectsNoOpSameRoleChange() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("시설 관리자"));

        AdminUserActionResult result = service.changeRole(ACTOR, TARGET, "facility", "재지정");

        assertThat(result.applied()).isFalse();
        assertThat(result.message()).contains("이미 같은 역할");
        verify(memberMapper, never()).updateRole(anyLong(), any());
        verify(sessionRevocationService, never()).revokeAfterCommit(anyLong());
    }

    @Test
    @DisplayName("대기 중인 관리자 요청이 없으면 승인·거절은 아무것도 바꾸지 않는다")
    void approveWithoutRequestIsSafe() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));
        when(memberMapper.findPendingAdminRequestForUpdate(TARGET)).thenReturn(null);

        AdminUserActionResult approve = service.approve(ACTOR, TARGET);
        AdminUserActionResult reject = service.reject(ACTOR, TARGET);

        assertThat(approve.applied()).isFalse();
        assertThat(reject.applied()).isFalse();
        verify(memberMapper, never()).updateRole(anyLong(), any());
        verify(memberMapper, never()).reviewAdminRequest(anyLong(), any(), anyLong());
        verify(sessionRevocationService, never()).revokeAfterCommit(anyLong());
    }

    @Test
    @DisplayName("대기 요청을 승인하면 요청 역할을 반영하고 검토자를 남긴다")
    void approvesPendingRequest() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));
        AdminRequestRow request = new AdminRequestRow();
        request.setRequestId(9L);
        request.setUserId(TARGET);
        request.setRequestedRole("FACILITY_MANAGER");
        when(memberMapper.findPendingAdminRequestForUpdate(TARGET)).thenReturn(request);
        when(memberMapper.reviewAdminRequest(9L, "승인", ACTOR)).thenReturn(1);

        AdminUserActionResult result = service.approve(ACTOR, TARGET);

        assertThat(result.applied()).isTrue();
        verify(memberMapper).updateRole(TARGET, MemberRole.FACILITY_MANAGER.label());
        verify(memberMapper).reviewAdminRequest(9L, "승인", ACTOR);
        verify(sessionRevocationService).revokeAfterCommit(TARGET);
    }

    @Test
    @DisplayName("이미 시스템 관리자인 계정은 승인으로 강등할 수 없다")
    void approveRejectsExistingAdmin() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("시스템 관리자"));
        AdminRequestRow request = new AdminRequestRow();
        request.setRequestId(9L);
        request.setUserId(TARGET);
        request.setRequestedRole("FACILITY_MANAGER");
        when(memberMapper.findPendingAdminRequestForUpdate(TARGET)).thenReturn(request);

        AdminUserActionResult result = service.approve(ACTOR, TARGET);

        assertThat(result.applied()).isFalse();
        assertThat(result.message()).contains("시스템 관리자");
        verify(memberMapper, never()).updateRole(anyLong(), any());
        verify(memberMapper, never()).reviewAdminRequest(anyLong(), any(), anyLong());
        verify(sessionRevocationService, never()).revokeAfterCommit(anyLong());
    }

    @Test
    @DisplayName("요청 역할이 일반 회원이면 승인하지 않는다")
    void approveRejectsUnusableRequestedRole() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));
        AdminRequestRow request = new AdminRequestRow();
        request.setRequestId(9L);
        request.setUserId(TARGET);
        request.setRequestedRole("MEMBER");
        when(memberMapper.findPendingAdminRequestForUpdate(TARGET)).thenReturn(request);

        AdminUserActionResult result = service.approve(ACTOR, TARGET);

        assertThat(result.applied()).isFalse();
        assertThat(result.message()).contains("요청된 역할");
        verify(memberMapper, never()).updateRole(anyLong(), any());
        verify(memberMapper, never()).reviewAdminRequest(anyLong(), any(), anyLong());
        verify(sessionRevocationService, never()).revokeAfterCommit(anyLong());
    }

    @Test
    @DisplayName("거절 검토 갱신이 0건이면 적용되지 않은 결과로 돌려준다")
    void rejectReportsLostUpdateAsNotApplied() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));
        AdminRequestRow request = new AdminRequestRow();
        request.setRequestId(9L);
        request.setUserId(TARGET);
        request.setRequestedRole("FACILITY_MANAGER");
        when(memberMapper.findPendingAdminRequestForUpdate(TARGET)).thenReturn(request);
        when(memberMapper.reviewAdminRequest(9L, "거절", ACTOR)).thenReturn(0);

        AdminUserActionResult result = service.reject(ACTOR, TARGET);

        assertThat(result.applied()).isFalse();
        assertThat(result.message()).contains("다른 관리자가 먼저 처리");
    }

    @Test
    @DisplayName("승인 검토 갱신이 0건이면 역할 변경을 롤백하도록 예외를 던진다")
    void approveRollsBackWhenReviewLost() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));
        AdminRequestRow request = new AdminRequestRow();
        request.setRequestId(9L);
        request.setUserId(TARGET);
        request.setRequestedRole("FACILITY_MANAGER");
        when(memberMapper.findPendingAdminRequestForUpdate(TARGET)).thenReturn(request);
        when(memberMapper.reviewAdminRequest(9L, "승인", ACTOR)).thenReturn(0);

        assertThatThrownBy(() -> service.approve(ACTOR, TARGET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("다른 관리자가 먼저 처리한 요청");

        verify(sessionRevocationService, never()).revokeAfterCommit(anyLong());
    }

    @Test
    @DisplayName("역할 저장이 반영되지 않으면 성공으로 보고하지 않는다")
    void changeRoleFailsWhenUpdateMisses() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));
        when(memberMapper.updateRole(TARGET, MemberRole.FACILITY_MANAGER.label())).thenReturn(0);

        assertThatThrownBy(() -> service.changeRole(ACTOR, TARGET, "facility", "시설 담당 승인"))
                .isInstanceOf(IllegalStateException.class);

        verify(sessionRevocationService, never()).revokeAfterCommit(anyLong());
    }

    @Test
    @DisplayName("회원 정지는 기간·사유를 검증하고 제재 이력을 남긴다")
    void suspendsWithDurationAndReason() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));

        AdminUserActionResult result = service.suspend(ACTOR, TARGET, 7, "운영 정책 위반");

        assertThat(result.applied()).isTrue();
        verify(penaltyService).issueForAccount(TARGET, ACTOR, 7, "운영 정책 위반");
        verify(memberMapper, never()).updatePenaltyTime(anyLong(), any());
    }

    @Test
    @DisplayName("정지 기간이 허용 목록 밖이면 영구 정지까지 열거해 안내하고 제재를 만들지 않는다")
    void rejectsUnsupportedSuspendDuration() {
        // 계정 정지 화면에는 영구 정지 선택지도 있으므로 오류 안내가 두 선택지를 모두 열거한다.
        assertThatThrownBy(() -> service.suspend(ACTOR, TARGET, 5, "사유"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1·3·7·14·30")
                .hasMessageContaining("영구 정지");
        // 영구 정지 특수 값(-1) 외의 음수·0은 계속 거부한다.
        assertThatThrownBy(() -> service.suspend(ACTOR, TARGET, 0, "사유"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1·3·7·14·30")
                .hasMessageContaining("영구 정지");
        assertThatThrownBy(() -> service.suspend(ACTOR, TARGET, -2, "사유"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("영구 정지");
        assertThatThrownBy(() -> service.suspend(ACTOR, TARGET, null, "사유"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("영구 정지");

        verify(penaltyService, never()).issueForAccount(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("시스템 관리자 계정과 자기 자신은 정지할 수 없다")
    void rejectsSuspendingAdminOrSelf() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("시스템 관리자"));

        assertThat(service.suspend(ACTOR, TARGET, 3, "사유").applied()).isFalse();
        assertThat(service.suspend(ACTOR, ACTOR, 3, "사유").applied()).isFalse();

        verify(penaltyService, never()).issueForAccount(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("아직 이용정지가 남아 있으면 다시 정지하지 않는다")
    void rejectsSecondSuspendWhilePenaltyIsFuture() {
        Member suspended = member("일반 회원");
        suspended.setPenaltyTime(LocalDateTime.now().plusDays(3));
        when(accountLockService.lockById(TARGET)).thenReturn(suspended);

        AdminUserActionResult result = service.suspend(ACTOR, TARGET, 7, "중복 정지");

        assertThat(result.applied()).isFalse();
        assertThat(result.message()).contains("이미 이용이 제한");
        verify(penaltyService, never()).issueForAccount(anyLong(), anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("계정 영구 정지는 영구 정지 특수 값을 그대로 제재 부여에 넘긴다")
    void suspendsAccountPermanently() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));

        AdminUserActionResult result = service.suspend(ACTOR, TARGET,
                PenaltyService.PERMANENT_DURATION_DAYS, "운영 정책 위반");

        assertThat(result.applied()).isTrue();
        assertThat(result.message()).contains("영구 정지");
        verify(penaltyService).issueForAccount(TARGET, ACTOR, PenaltyService.PERMANENT_DURATION_DAYS,
                "운영 정책 위반");
        verify(memberMapper, never()).updatePenaltyTime(anyLong(), any());
    }

    @Test
    @DisplayName("영구 정지된 계정의 상태·제재 문구는 9999-12-31 대신 영구 정지로 보여 준다")
    void permanentSuspensionShowsReadableLabels() {
        AdminUserRow row = row(1L);
        row.setPenaltyTime(MemberPenaltyTimeService.PERMANENT_END_AT);
        when(memberMapper.findAdminUsers(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));

        AdminUserPage page = service.loadPage(null, null, "suspended", 0);

        AdminUserListItem user = page.getUsers().get(0);
        assertThat(user.getStatusLabel()).isEqualTo("영구 정지");
        assertThat(user.getSanction()).contains("영구 정지");
        assertThat(user.getSanction()).doesNotContain("9999");
        assertThat(user.isSuspended()).isTrue();
        assertThat(user.getStatusTone()).isEqualTo("bad");
    }

    @Test
    @DisplayName("기간 정지된 계정의 상태·제재 문구는 기존 표시를 유지한다")
    void temporarySuspensionKeepsExistingLabels() {
        LocalDateTime endAt = LocalDateTime.now().plusDays(3).withNano(0);
        AdminUserRow row = row(1L);
        row.setPenaltyTime(endAt);
        when(memberMapper.findAdminUsers(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));

        AdminUserPage page = service.loadPage(null, null, "suspended", 0);

        AdminUserListItem user = page.getUsers().get(0);
        assertThat(user.getStatusLabel()).isEqualTo("정지");
        assertThat(user.getSanction()).isEqualTo("이용 제한 ~ "
                + endAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        assertThat(user.getSanction()).doesNotContain("영구");
    }

    @Test
    @DisplayName("정지 상태가 아닌 계정에는 제재 문구를 표시하지 않는다")
    void expiredPenaltyHasNoSanctionText() {
        AdminUserRow row = row(1L);
        row.setPenaltyTime(LocalDateTime.now().minusDays(1));
        when(memberMapper.findAdminUsers(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));

        AdminUserListItem user = service.loadPage(null, null, "all", 0).getUsers().get(0);

        assertThat(user.getStatusLabel()).isEqualTo("정상");
        assertThat(user.getSanction()).isNull();
    }

    @Test
    @DisplayName("복구는 남은 이용 제한을 모두 해제한다")
    void restoresSuspendedAccount() {
        Member suspended = member("일반 회원");
        suspended.setPenaltyTime(LocalDateTime.now().plusDays(3));
        when(accountLockService.lockById(TARGET)).thenReturn(suspended);

        AdminUserActionResult result = service.restore(ACTOR, TARGET);

        assertThat(result.applied()).isTrue();
        verify(penaltyService).releaseActiveRestrictions(TARGET, ACTOR);
    }

    @Test
    @DisplayName("영구 정지된 계정도 복구로 해제할 수 있다")
    void restoresPermanentlySuspendedAccount() {
        Member suspended = member("일반 회원");
        suspended.setPenaltyTime(MemberPenaltyTimeService.PERMANENT_END_AT);
        when(accountLockService.lockById(TARGET)).thenReturn(suspended);

        AdminUserActionResult result = service.restore(ACTOR, TARGET);

        assertThat(result.applied()).isTrue();
        verify(penaltyService).releaseActiveRestrictions(TARGET, ACTOR);
    }

    @Test
    @DisplayName("정지 상태가 아닌 계정 복구는 거절한다")
    void restoreRejectsActiveAccount() {
        when(accountLockService.lockById(TARGET)).thenReturn(member("일반 회원"));

        AdminUserActionResult result = service.restore(ACTOR, TARGET);

        assertThat(result.applied()).isFalse();
        verifyNoInteractions(penaltyService);
    }

    @Test
    @DisplayName("목록은 pageSize+1 조회로 페이지를 만들고 필터를 서비스에 넘긴다")
    void loadsPageWithPagination() {
        AdminUserRow row = new AdminUserRow();
        row.setUserId(1L);
        row.setNickname("회원");
        row.setEmail("a@b.test");
        row.setRole("ADMIN");
        row.setCreatedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        when(memberMapper.findAdminUsers(isNull(), eq("ADMIN"), eq("시스템 관리자"), isNull(), any(),
                anyInt(), anyInt())).thenReturn(List.of(row));
        when(memberMapper.countAdminUsers(isNull(), eq("ADMIN"), eq("시스템 관리자"), isNull(), any()))
                .thenReturn(45);
        when(memberMapper.countActiveUsersByRole("ADMIN", "시스템 관리자")).thenReturn(2);
        when(memberMapper.countActiveUsersByRole("FACILITY_MANAGER", "시설 관리자")).thenReturn(3);

        AdminUserPage page = service.loadPage(null, "system", "all", 1);

        assertThat(page.getUsers()).singleElement()
                .satisfies(user -> assertThat(user.getRoleValue()).isEqualTo("system"));
        assertThat(page.getPagination().getPage()).isEqualTo(1);
        assertThat(page.getPagination().getTotalPages()).isEqualTo(3);
        assertThat(page.getPagination().getPrevUrl()).contains("view=users").contains("page=0");
        assertThat(page.getPagination().getNextUrl()).contains("page=2");
        assertThat(page.getMetrics().getAdminCount()).isEqualTo(5);
        assertThat(page.getMetrics().getSystemAdminCount()).isEqualTo(2);
        assertThat(page.getMetrics().getFacilityAdminCount()).isEqualTo(3);
        verify(memberMapper).countActiveUsersByRole("ADMIN", "시스템 관리자");
        verify(memberMapper).countActiveUsersByRole("FACILITY_MANAGER", "시설 관리자");
    }

    @Test
    @DisplayName("알 수 없는 필터·음수 페이지는 전체·0으로 정규화한다")
    void normalizesFilters() {
        service.loadPage("  ", "bogus", "bogus", -3);

        verify(memberMapper).findAdminUsers(isNull(), isNull(), isNull(), isNull(), any(),
                eq(AdminUserService.PAGE_SIZE + 1), eq(0));
    }

    @Test
    @DisplayName("내보내기는 배치를 넘는 일치 계정을 순서대로 모두 모은다")
    void findAllForExportCollectsBeyondFirstPage() {
        List<AdminUserRow> firstPage = new ArrayList<>();
        for (long id = 1; id <= AdminUserService.EXPORT_BATCH_SIZE + 1; id++) {
            firstPage.add(row(id));
        }
        List<AdminUserRow> secondPage = List.of(row(501L), row(502L), row(503L));
        when(memberMapper.findAdminUsers(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(firstPage, secondPage);

        List<AdminUserListItem> users = service.findAllForExport(null, null, null);

        assertThat(users).hasSize(AdminUserService.EXPORT_BATCH_SIZE + 3);
        verify(memberMapper).findAdminUsers(isNull(), isNull(), isNull(), isNull(), any(),
                eq(AdminUserService.EXPORT_BATCH_SIZE + 1), eq(0));
        verify(memberMapper).findAdminUsers(isNull(), isNull(), isNull(), isNull(), any(),
                eq(AdminUserService.EXPORT_BATCH_SIZE + 1), eq(AdminUserService.EXPORT_BATCH_SIZE));
    }

    @Test
    @DisplayName("내보내기는 화면과 같은 검색어·역할·상태 필터를 그대로 넘긴다")
    void findAllForExportPassesFilters() {
        when(memberMapper.findAdminUsers(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row(1L)));

        service.findAllForExport("kim", "facility", "suspended");

        verify(memberMapper).findAdminUsers(eq("kim"), eq("FACILITY_MANAGER"), eq("시설 관리자"),
                eq("suspended"), any(), eq(AdminUserService.EXPORT_BATCH_SIZE + 1), eq(0));
    }

    @Test
    @DisplayName("내보내기 배치 경계와 기존 페이지 상한을 유지한다")
    void findAllForExportPreservesBatchBoundariesAndCap() {
        int[] sizes = {0, 20, 21, 500, 501, 20020, 20021};
        int[] expectedCalls = {1, 1, 1, 1, 2, 41, 41};
        int[] currentSize = {0};
        when(memberMapper.findAdminUsers(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> {
                    int limit = invocation.getArgument(5);
                    int offset = invocation.getArgument(6);
                    List<AdminUserRow> rows = new ArrayList<>();
                    for (int i = offset; i < Math.min(currentSize[0], offset + limit); i++) {
                        rows.add(row(i + 1L));
                    }
                    return rows;
                });
        for (int i = 0; i < sizes.length; i++) {
            currentSize[0] = sizes[i];
            org.mockito.Mockito.clearInvocations(memberMapper);
            List<AdminUserListItem> users = service.findAllForExport(null, null, null);
            int expectedSize = Math.min(sizes[i], AdminUserService.MAX_EXPORT_ROWS);
            assertThat(users).hasSize(expectedSize);
            for (int index = 0; index < expectedSize; index++) {
                assertThat(users.get(index).getUserId()).isEqualTo(index + 1L);
            }
            verify(memberMapper, times(expectedCalls[i])).findAdminUsers(isNull(), isNull(), isNull(), isNull(),
                    any(), anyInt(), anyInt());
        }
    }

    private static AdminUserRow row(long userId) {
        AdminUserRow row = new AdminUserRow();
        row.setUserId(userId);
        row.setNickname("회원" + userId);
        row.setEmail("user" + userId + "@example.invalid");
        row.setRole("MEMBER");
        row.setCreatedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        return row;
    }

    private static Member member(String role) {
        Member member = new Member();
        member.setUserId(TARGET);
        member.setNickname("대상");
        member.setEmail("target@example.invalid");
        member.setRole(role);
        return member;
    }
}
