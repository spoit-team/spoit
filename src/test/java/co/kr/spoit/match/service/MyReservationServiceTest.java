package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.kr.spoit.match.dto.MyApplicationRow;
import co.kr.spoit.match.dto.MyReservations;
import co.kr.spoit.match.dto.MyReservations.Entry;
import co.kr.spoit.match.mapper.MatchApplicationMapper;

/** RES-06 신청 내역을 현재/지난 목록으로 나누는 규칙을 검증한다. */
class MyReservationServiceTest {

    private static final long USER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 12, 0);

    private MatchApplicationMapper mapper;
    private MyReservationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(MatchApplicationMapper.class);
        service = new MyReservationService(mapper);
    }

    @Test
    @DisplayName("경기 전 대기·확정 신청은 현재 신청, 나머지는 지난 내역(최근 경기 먼저)")
    void splitsCurrentAndPast() {
        given(mapper.findMyApplications(eq(USER_ID), anyList())).willReturn(List.of(
                row(1L, "확정", "종료", NOW.minusDays(3), "A"),
                row(2L, "취소", "모집 중", NOW.minusDays(1), null),
                row(3L, "대기", "모집 중", NOW.plusDays(1), null),
                row(4L, "확정", "매칭 완료", NOW.plusDays(2), "B")));

        MyReservations result = service.find(USER_ID, NOW);

        assertThat(result.getCurrent()).extracting(Entry::getApplicationId).containsExactly(3L, 4L);
        assertThat(result.getCurrent()).extracting(Entry::getStateLabel).containsExactly("확정 대기", "참가 확정");
        assertThat(result.getPast()).extracting(Entry::getApplicationId).containsExactly(2L, 1L);
        assertThat(result.getPast()).extracting(Entry::getStateLabel).containsExactly("신청 취소", "이용 완료");
        assertThat(result.getCurrent().get(1).getStatusNote()).isEqualTo("B팀에 배정되었습니다.");
        assertThat(result.getConfirmedCount()).isEqualTo(2);
        assertThat(result.getCompletedCount()).isEqualTo(1);
        assertThat(result.getWaitingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("확정되지 않고 경기가 끝난 신청과 취소된 매칭방 신청은 지난 내역")
    void expiredAndCancelledMatchesArePast() {
        given(mapper.findMyApplications(eq(USER_ID), anyList())).willReturn(List.of(
                row(1L, "대기", "모집 중", NOW.minusDays(1), null),
                row(2L, "확정", "취소", NOW.plusDays(1), null)));

        MyReservations result = service.find(USER_ID, NOW);

        assertThat(result.getCurrent()).isEmpty();
        assertThat(result.getPast()).extracting(Entry::getStateLabel).containsExactly("매치 취소", "미확정 종료");
        assertThat(result.getWaitingCount()).isZero();
        assertThat(result.getConfirmedCount()).isZero();
    }

    @Test
    @DisplayName("동호회 신청은 팀 신청으로 표시하고 확정 참가자 수를 보여 준다")
    void clubApplicationShowsParticipantCount() {
        MyApplicationRow club = row(1L, "확정", "모집 중", NOW.plusDays(1), null);
        club.setApplicationType("동호회");
        club.setParticipantCount(4);
        given(mapper.findMyApplications(eq(USER_ID), anyList())).willReturn(List.of(club));

        Entry entry = service.find(USER_ID, NOW).getCurrent().get(0);

        assertThat(entry.getTypeLabel()).isEqualTo("팀 신청");
        assertThat(entry.getSeatLine()).isEqualTo("참가 4명");
    }

    @Test
    @DisplayName("취소 버튼은 본인 신청 + 모집 중 + 경기 전일 때만 보인다")
    void cancelOnlyForOwnRecruitingApplications() {
        MyApplicationRow mine = row(1L, "대기", "모집 중", NOW.plusDays(1), null);
        MyApplicationRow assigned = row(2L, "확정", "매칭 완료", NOW.plusDays(1), "A");
        MyApplicationRow clubMember = row(3L, "확정", "모집 중", NOW.plusDays(1), null);
        clubMember.setApplicantUserId(99L);
        given(mapper.findMyApplications(eq(USER_ID), anyList())).willReturn(List.of(mine, assigned, clubMember));

        List<Entry> current = service.find(USER_ID, NOW).getCurrent();

        assertThat(current).extracting(Entry::isCanCancel).containsExactly(true, false, false);
    }

    private static MyApplicationRow row(long applicationId, String status, String matchStatus,
                                        LocalDateTime startAt, String side) {
        MyApplicationRow r = new MyApplicationRow();
        r.setApplicationId(applicationId);
        r.setMatchId(100L + applicationId);
        r.setApplicantUserId(USER_ID);
        r.setApplicationType("개인");
        r.setStatus(status);
        r.setTitle("매칭방 " + applicationId);
        r.setSportName("풋살");
        r.setFacilityName("시설");
        r.setStartAt(startAt);
        r.setEndAt(startAt.plusHours(2));
        r.setMatchStatus(matchStatus);
        r.setMatchSide(side);
        return r;
    }
}
