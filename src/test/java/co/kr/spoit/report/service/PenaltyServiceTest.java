package co.kr.spoit.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.service.MemberPenaltyTimeService;
import co.kr.spoit.report.domain.Penalty;
import co.kr.spoit.report.domain.PenaltyRow;
import co.kr.spoit.report.dto.PenaltyPage;
import co.kr.spoit.report.mapper.PenaltyMapper;

/**
 * 제재 부여·조회·해제의 검증 규칙과 회원 컬럼 동기화 시점을 검증한다.
 *
 * <p>이용 제한만 {@code app_user.penalty_time}을 연장하고 경고는 이력만 남긴다. 조기 해제는 남은
 * 적용 중 이용 제한의 최대 종료 시각으로 회원 컬럼을 재계산하며, 남은 제재가 없으면 {@code null}로
 * 푼다. 감사 로그는 이 경로에서 쓰지 않는다.</p>
 */
class PenaltyServiceTest {

    private static final long ADMIN_ID = 3L;
    private static final long TARGET_ID = 11L;
    private static final long REPORT_ID = 15L;
    private static final long PENALTY_ID = 42L;

    private final PenaltyMapper mapper = mock(PenaltyMapper.class);
    private final MemberPenaltyTimeService memberPenaltyTimeService = mock(MemberPenaltyTimeService.class);
    private final PenaltyService service = new PenaltyService(mapper, memberPenaltyTimeService);

    @BeforeEach
    void stubDefaults() {
        when(mapper.findPenalties(any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countActiveRestrictedUsers(any())).thenReturn(0);
        when(mapper.countWarnings()).thenReturn(0);
        when(mapper.countIssuedSince(any())).thenReturn(0);
        when(memberPenaltyTimeService.lock(anyLong())).thenReturn(member("일반 회원"));
    }

    @Test
    @DisplayName("이용 제한은 기간을 더해 저장하고 회원 이용정지 종료 시각을 연장한다")
    void issuesRestrictionAndSyncsPenaltyTime() {
        service.issueForReport(REPORT_ID, TARGET_ID, ADMIN_ID, "이용 제한", 14, "반복 신고 확인");

        verify(memberPenaltyTimeService).lock(TARGET_ID);
        ArgumentCaptor<Penalty> captor = ArgumentCaptor.forClass(Penalty.class);
        verify(mapper).insertPenalty(captor.capture());
        Penalty saved = captor.getValue();
        assertThat(saved.getPenaltyType()).isEqualTo("이용 제한");
        assertThat(saved.getReportId()).isEqualTo(REPORT_ID);
        assertThat(saved.getIssuedBy()).isEqualTo(ADMIN_ID);
        assertThat(saved.getStatus()).isEqualTo("적용 중");
        assertThat(saved.getEndAt()).isEqualTo(saved.getStartAt().plusDays(14));
        verify(memberPenaltyTimeService).extendUntil(eq(TARGET_ID), eq(saved.getEndAt()));
    }

    @Test
    @DisplayName("경고는 기간 없이 이력만 남기고 회원 이용정지를 건드리지 않는다")
    void warningDoesNotTouchPenaltyTime() {
        service.issueForReport(REPORT_ID, TARGET_ID, ADMIN_ID, "경고", 7, "경고 1회");

        ArgumentCaptor<Penalty> captor = ArgumentCaptor.forClass(Penalty.class);
        verify(mapper).insertPenalty(captor.capture());
        assertThat(captor.getValue().getEndAt()).isNull();
        verify(memberPenaltyTimeService, never()).extendUntil(anyLong(), any());
    }

    @Test
    @DisplayName("이용 제한 기간은 1·3·7·14·30일만 허용한다")
    void rejectsUnsupportedDuration() {
        assertThatThrownBy(() -> service.issueForReport(REPORT_ID, TARGET_ID, ADMIN_ID, "이용 제한", 2, "사유"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1·3·7·14·30");

        verify(mapper, never()).insertPenalty(any());
        verify(memberPenaltyTimeService, never()).extendUntil(anyLong(), any());
    }

    @Test
    @DisplayName("영구 정지·알 수 없는 종류와 빈 사유는 거부한다")
    void rejectsInvalidTypeAndReason() {
        assertThatThrownBy(() -> service.issueForReport(REPORT_ID, TARGET_ID, ADMIN_ID, "영구 정지", null, "사유"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issueForReport(REPORT_ID, TARGET_ID, ADMIN_ID, "이용 제한", 7, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issueForReport(REPORT_ID, null, ADMIN_ID, "경고", null, "사유"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mapper, never()).insertPenalty(any());
    }

    @Test
    @DisplayName("조기 해제는 남은 이용 제한 종료 시각으로 회원 컬럼을 재계산한다")
    void releaseRecomputesRemainingRestriction() {
        LocalDateTime remaining = LocalDateTime.of(2026, 12, 1, 10, 0);
        when(mapper.findPenalty(PENALTY_ID)).thenReturn(penalty("적용 중"));
        when(mapper.findPenaltyForUpdate(PENALTY_ID)).thenReturn(penalty("적용 중"));
        when(mapper.releasePenalty(eq(PENALTY_ID), eq(ADMIN_ID), any())).thenReturn(1);
        when(mapper.findLatestActiveRestrictionEndAtForUpdate(eq(TARGET_ID), any())).thenReturn(remaining);

        LocalDateTime result = service.release(PENALTY_ID, ADMIN_ID);

        assertThat(result).isEqualTo(remaining);
        verify(memberPenaltyTimeService).lock(TARGET_ID);
        verify(memberPenaltyTimeService).overwrite(TARGET_ID, remaining);
    }

    @Test
    @DisplayName("조기 해제는 남은 이용 제한을 잠금 읽기(현재 읽기)로 다시 조회한다")
    void releaseReadsRemainingRestrictionsWithCurrentRead() {
        when(mapper.findPenalty(PENALTY_ID)).thenReturn(penalty("적용 중"));
        when(mapper.findPenaltyForUpdate(PENALTY_ID)).thenReturn(penalty("적용 중"));
        when(mapper.releasePenalty(eq(PENALTY_ID), eq(ADMIN_ID), any())).thenReturn(1);
        when(mapper.findLatestActiveRestrictionEndAtForUpdate(eq(TARGET_ID), any())).thenReturn(null);

        service.release(PENALTY_ID, ADMIN_ID);

        // REPEATABLE READ 스냅샷이 아니라 잠금 읽기로 조회해야 방금 해제한 행이 반영된다.
        verify(mapper).findLatestActiveRestrictionEndAtForUpdate(eq(TARGET_ID), any());
    }

    @Test
    @DisplayName("적용 중 이용 제한을 해제하면 회원 이용정지를 null로 푼다")
    void releaseLastRestrictionClearsPenaltyTime() {
        when(mapper.findPenalty(PENALTY_ID)).thenReturn(penalty("적용 중"));
        when(mapper.findPenaltyForUpdate(PENALTY_ID)).thenReturn(penalty("적용 중"));
        when(mapper.releasePenalty(eq(PENALTY_ID), eq(ADMIN_ID), any())).thenReturn(1);
        when(mapper.findLatestActiveRestrictionEndAtForUpdate(eq(TARGET_ID), any())).thenReturn(null);

        service.release(PENALTY_ID, ADMIN_ID);

        verify(memberPenaltyTimeService).overwrite(TARGET_ID, null);
    }

    @Test
    @DisplayName("경고 해제는 회원 이용정지 컬럼을 건드리지 않는다")
    void releaseWarningLeavesPenaltyTime() {
        Penalty warning = penalty("적용 중");
        warning.setPenaltyType("경고");
        when(mapper.findPenalty(PENALTY_ID)).thenReturn(warning);
        when(mapper.findPenaltyForUpdate(PENALTY_ID)).thenReturn(warning);
        when(mapper.releasePenalty(eq(PENALTY_ID), eq(ADMIN_ID), any())).thenReturn(1);

        service.release(PENALTY_ID, ADMIN_ID);

        verify(memberPenaltyTimeService, never()).overwrite(anyLong(), any());
    }

    @Test
    @DisplayName("이미 해제된 제재는 다시 해제하지 않고 경합으로 안내한다")
    void rejectsReleasingInactivePenalty() {
        when(mapper.findPenalty(PENALTY_ID)).thenReturn(penalty("해제"));
        when(mapper.findPenaltyForUpdate(PENALTY_ID)).thenReturn(penalty("해제"));

        assertThatThrownBy(() -> service.release(PENALTY_ID, ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class);

        verify(mapper, never()).releasePenalty(anyLong(), anyLong(), any());
        verify(memberPenaltyTimeService, never()).overwrite(anyLong(), any());
    }

    @Test
    @DisplayName("조건부 해제가 0행이면 경합으로 보고 회원 컬럼을 건드리지 않는다")
    void zeroRowReleaseIsConflict() {
        when(mapper.findPenalty(PENALTY_ID)).thenReturn(penalty("적용 중"));
        when(mapper.findPenaltyForUpdate(PENALTY_ID)).thenReturn(penalty("적용 중"));
        when(mapper.releasePenalty(eq(PENALTY_ID), eq(ADMIN_ID), any())).thenReturn(0);

        assertThatThrownBy(() -> service.release(PENALTY_ID, ADMIN_ID))
                .isInstanceOf(ReportStateConflictException.class);

        verify(memberPenaltyTimeService, never()).overwrite(anyLong(), any());
    }

    @Test
    @DisplayName("없는 제재 해제는 404 예외다")
    void releaseMissingPenaltyIsNotFound() {
        when(mapper.findPenalty(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.release(999L, ADMIN_ID))
                .isInstanceOf(ReportNotFoundException.class);

        verify(memberPenaltyTimeService, never()).lock(anyLong());
    }

    @Test
    @DisplayName("시스템 관리자 계정에는 경고·이용 제한을 부여하지 않는다")
    void rejectsIssuingPenaltyToAdminAccount() {
        when(memberPenaltyTimeService.lock(TARGET_ID)).thenReturn(member("시스템 관리자"));

        assertThatThrownBy(() -> service.issueForReport(REPORT_ID, TARGET_ID, ADMIN_ID, "경고", null, "사유"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시스템 관리자");
        assertThatThrownBy(() -> service.issueForAccount(TARGET_ID, ADMIN_ID, 7, "사유"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("시스템 관리자");

        verify(mapper, never()).insertPenalty(any());
        verify(memberPenaltyTimeService, never()).extendUntil(anyLong(), any());
    }

    @Test
    @DisplayName("목록은 pageSize+1건 조회로 페이지를 만들고 지표를 함께 계산한다")
    void loadsPageWithMetrics() {
        PenaltyRow row = new PenaltyRow();
        row.setPenaltyId(1L);
        row.setTargetUserId(TARGET_ID);
        row.setAccount("대상");
        row.setPenaltyType("경고");
        row.setStatus("적용 중");
        row.setActive(true);
        when(mapper.findPenalties(isNull(), eq("active"), any(), eq(PenaltyService.PAGE_SIZE + 1), eq(0)))
                .thenReturn(List.of(row));
        when(mapper.countActiveRestrictedUsers(any())).thenReturn(2);
        when(mapper.countWarnings()).thenReturn(5);
        when(mapper.countIssuedSince(any())).thenReturn(3);

        PenaltyPage page = service.loadPage(null, "active", 0);

        assertThat(page.getPenalties()).hasSize(1);
        assertThat(page.getPenalties().get(0).getStatusLabel()).isEqualTo("적용 중");
        assertThat(page.getPenalties().get(0).isActive()).isTrue();
        assertThat(page.getMetrics().getActiveCount()).isEqualTo(2);
        assertThat(page.getMetrics().getWarningCount()).isEqualTo(5);
        assertThat(page.getMetrics().getMonthlyCount()).isEqualTo(3);
        assertThat(page.getFilters().getStatus()).isEqualTo("active");
        assertThat(page.isHasNextPenalties()).isFalse();
    }

    @Test
    @DisplayName("pageSize+1건이면 다음 페이지가 있다고 표시하고 pageSize만 노출한다")
    void exposesHasNextPenaltyPage() {
        List<PenaltyRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i <= PenaltyService.PAGE_SIZE; i++) {
            PenaltyRow row = new PenaltyRow();
            row.setPenaltyId((long) (i + 1));
            row.setTargetUserId(TARGET_ID);
            row.setPenaltyType("경고");
            row.setStatus("적용 중");
            row.setActive(true);
            rows.add(row);
        }
        when(mapper.findPenalties(isNull(), eq("all"), any(), eq(PenaltyService.PAGE_SIZE + 1),
                eq(PenaltyService.PAGE_SIZE))).thenReturn(rows);

        PenaltyPage page = service.loadPage(null, null, 1);

        assertThat(page.getPenalties()).hasSize(PenaltyService.PAGE_SIZE);
        assertThat(page.getFilters().getPage()).isEqualTo(1);
        assertThat(page.isHasNextPenalties()).isTrue();
    }

    @Test
    @DisplayName("알 수 없는 상태 필터는 전체로, 음수 페이지는 0으로 정규화한다")
    void normalizesFilters() {
        PenaltyPage page = service.loadPage("  ", "bogus", -5);

        assertThat(page.getFilters().getStatus()).isEqualTo("all");
        assertThat(page.getFilters().getQ()).isNull();
        assertThat(page.getFilters().getPage()).isZero();
        verify(mapper).findPenalties(isNull(), eq("all"), any(), eq(PenaltyService.PAGE_SIZE + 1), eq(0));
    }

    private static Member member(String role) {
        Member member = new Member();
        member.setUserId(TARGET_ID);
        member.setRole(role);
        return member;
    }

    private static Penalty penalty(String status) {
        Penalty penalty = new Penalty();
        penalty.setPenaltyId(PENALTY_ID);
        penalty.setTargetUserId(TARGET_ID);
        penalty.setPenaltyType("이용 제한");
        penalty.setStatus(status);
        penalty.setStartAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        penalty.setEndAt(LocalDateTime.of(2026, 11, 15, 10, 0));
        return penalty;
    }
}
