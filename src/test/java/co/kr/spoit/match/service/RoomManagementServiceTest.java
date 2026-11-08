package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.kr.spoit.match.dto.FacilityDashboard;
import co.kr.spoit.match.dto.RoomManagementApplicationRow;
import co.kr.spoit.match.dto.RoomManagementDetail;
import co.kr.spoit.match.dto.RoomManagementPage;
import co.kr.spoit.match.dto.RoomManagementParticipantRow;
import co.kr.spoit.match.dto.RoomManagementQuery;
import co.kr.spoit.match.dto.RoomManagementRecentRequestRow;
import co.kr.spoit.match.dto.RoomManagementRow;
import co.kr.spoit.match.dto.RoomManagementSport;
import co.kr.spoit.match.mapper.RoomManagementMapper;

/** 시설 관리자 매칭방 목록·상세의 정규화, 점유 표시, 권한 차단 규칙을 검증한다. */
class RoomManagementServiceTest {

    private static final long MANAGER_ID = 9201L;

    private RoomManagementMapper mapper;
    private RoomManagementService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RoomManagementMapper.class);
        service = new RoomManagementService(mapper);
    }

    @Test
    @DisplayName("관리자 식별자가 없으면 조회 없이 빈 페이지를 돌려준다")
    void returnsEmptyPageWithoutManager() {
        RoomManagementPage page = service.findRooms(null, "농구", "recruiting", 9201L, 9301L);

        assertThat(page.getMatches()).isEmpty();
        assertThat(page.getSports()).isEmpty();
        assertThat(page.getFilters().getStatus()).isEqualTo("all");
        assertThat(page.getFilters().getFacilityId()).isNull();
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("알 수 없는 상태·종목은 전체로 되돌리고 정규화된 필터를 함께 돌려준다")
    void normalizesUnknownFilters() {
        given(mapper.findSports(MANAGER_ID)).willReturn(List.of(
                new RoomManagementSport(9201L, "농구"),
                new RoomManagementSport(9202L, "축구")));
        given(mapper.findRooms(any())).willReturn(List.of(row(9401L, "모집 중", 2, 8)));

        RoomManagementPage page = service.findRooms(MANAGER_ID, "  금요  ", "unknown", 9299L, null);

        assertThat(page.getFilters().getQ()).isEqualTo("금요");
        assertThat(page.getFilters().getStatus()).isEqualTo("all");
        assertThat(page.getFilters().getSportId()).isNull();
        assertThat(page.getSports()).extracting(RoomManagementPage.Sport::getName)
                .containsExactly("농구", "축구");

        RoomManagementQuery query = capturedQuery();
        assertThat(query.getManagerUserId()).isEqualTo(MANAGER_ID);
        assertThat(query.getStatusValue()).isNull();
        assertThat(query.getSportId()).isNull();
        assertThat(query.getQ()).isEqualTo("금요");
    }

    @Test
    @DisplayName("담당 활성 시설 필터는 그대로 유지하고 조회 조건에 넘긴다")
    void keepsManagedFacilityFilter() {
        given(mapper.findSports(MANAGER_ID)).willReturn(List.of());
        given(mapper.findManagedFacilityId(MANAGER_ID, 9301L)).willReturn(9301L);
        given(mapper.findRooms(any())).willReturn(List.of());

        RoomManagementPage page = service.findRooms(MANAGER_ID, null, "all", null, 9301L);

        assertThat(page.getFilters().getFacilityId()).isEqualTo(9301L);
        assertThat(capturedQuery().getFacilityId()).isEqualTo(9301L);
    }

    @Test
    @DisplayName("담당 밖·삭제·없는·양수 아님 시설 ID는 전체로 되돌리고 조회 조건에 넣지 않는다")
    void normalizesUnusableFacilityFilter() {
        given(mapper.findSports(MANAGER_ID)).willReturn(List.of());
        given(mapper.findRooms(any())).willReturn(List.of());
        // 담당이 아니거나 삭제된 시설은 매퍼가 null을 돌려준다.
        given(mapper.findManagedFacilityId(MANAGER_ID, 9303L)).willReturn(null);
        given(mapper.findManagedFacilityId(MANAGER_ID, 9302L)).willReturn(null);
        given(mapper.findManagedFacilityId(MANAGER_ID, 9999L)).willReturn(null);

        assertThat(service.findRooms(MANAGER_ID, null, "all", null, 9303L).getFilters().getFacilityId())
                .isNull();
        assertThat(capturedQuery().getFacilityId()).isNull();
        assertThat(service.findRooms(MANAGER_ID, null, "all", null, 9302L).getFilters().getFacilityId())
                .isNull();
        assertThat(service.findRooms(MANAGER_ID, null, "all", null, 9999L).getFilters().getFacilityId())
                .isNull();

        // 양수가 아닌 ID는 검증 쿼리조차 호출하지 않는다.
        assertThat(service.findRooms(MANAGER_ID, null, "all", null, 0L).getFilters().getFacilityId())
                .isNull();
        assertThat(service.findRooms(MANAGER_ID, null, "all", null, -5L).getFilters().getFacilityId())
                .isNull();
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .findManagedFacilityId(MANAGER_ID, 0L);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .findManagedFacilityId(MANAGER_ID, -5L);
    }

    @Test
    @DisplayName("시설 필터는 검색어·상태·종목과 함께 조회 조건에 조합된다")
    void combinesFacilityWithOtherFilters() {
        given(mapper.findSports(MANAGER_ID)).willReturn(List.of(new RoomManagementSport(9201L, "농구")));
        given(mapper.findManagedFacilityId(MANAGER_ID, 9304L)).willReturn(9304L);
        given(mapper.findRooms(any())).willReturn(List.of());

        service.findRooms(MANAGER_ID, "축구", "completed", 9201L, 9304L);

        RoomManagementQuery query = capturedQuery();
        assertThat(query.getQ()).isEqualTo("축구");
        assertThat(query.getStatusValue()).isEqualTo("종료");
        assertThat(query.getSportId()).isEqualTo(9201L);
        assertThat(query.getFacilityId()).isEqualTo(9304L);
        assertThat(query.getManagerUserId()).isEqualTo(MANAGER_ID);
    }

    @Test
    @DisplayName("상태 필터는 DB 값으로 변환하고 '이용 완료'는 종료 상태를 조회한다")
    void mapsStatusFilterToDatabaseValue() {
        given(mapper.findSports(MANAGER_ID)).willReturn(List.of());
        given(mapper.findRooms(any())).willReturn(List.of());

        service.findRooms(MANAGER_ID, null, "completed", null, null);

        assertThat(capturedQuery().getStatusValue()).isEqualTo("종료");
    }

    @Test
    @DisplayName("검색어는 표시값과 조회 파라미터를 분리해 이스케이프한다")
    void separatesDisplayQueryFromLikePattern() {
        assertThat(RoomManagementService.normalizeQuery("   ")).isNull();
        assertThat(RoomManagementService.normalizeQuery(null)).isNull();
        assertThat(RoomManagementService.normalizeQuery(" a ".repeat(200)))
                .hasSize(RoomManagementService.QUERY_MAX_LENGTH);

        // 표시값은 사용자가 입력한 그대로이고, LIKE 이스케이프는 조회 파라미터에만 적용한다.
        assertThat(RoomManagementService.normalizeQuery(" 100%_! ")).isEqualTo("100%_!");
        assertThat(RoomManagementService.escapeLikePattern("100%_!")).isEqualTo("100!%!_!!");
        assertThat(RoomManagementService.escapeLikePattern("금요")).isEqualTo("금요");
        assertThat(RoomManagementService.escapeLikePattern(null)).isNull();
    }

    @Test
    @DisplayName("필터에는 원본 검색어를, 조회 파라미터에는 이스케이프한 검색어를 넘긴다")
    void keepsRawQueryInFilters() {
        given(mapper.findSports(MANAGER_ID)).willReturn(List.of());
        given(mapper.findRooms(any())).willReturn(List.of());

        RoomManagementPage page = service.findRooms(MANAGER_ID, " 50% _ ", "all", null, null);

        assertThat(page.getFilters().getQ()).isEqualTo("50% _");
        assertThat(capturedQuery().getQ()).isEqualTo("50!% !_");
    }

    @Test
    @DisplayName("목록 행은 점유 인원·상태·자정 넘김 일시를 화면 문구로 만든다")
    void buildsListRowLabels() {
        given(mapper.findSports(MANAGER_ID)).willReturn(List.of());
        RoomManagementRow overnight = row(9402L, "매칭 완료", 2, 10);
        overnight.setStartAt(LocalDateTime.of(2026, 11, 7, 22, 0));
        overnight.setEndAt(LocalDateTime.of(2026, 11, 8, 0, 30));
        given(mapper.findRooms(any())).willReturn(List.of(overnight));

        RoomManagementPage.Match match = service.findRooms(MANAGER_ID, null, "all", null, null)
                .getMatches().get(0);

        assertThat(match.getParticipation()).isEqualTo("2 / 10명");
        assertThat(match.getStatusLabel()).isEqualTo("매칭 완료");
        assertThat(match.getStatusTone()).isEqualTo("warn");
        assertThat(match.getDateLabel()).isEqualTo("2026-11-07(토) 22:00~2026-11-08(일) 00:30");
        assertThat(match.getFacilityName()).isEqualTo("관리 시설 알파");
        assertThat(match.getSportName()).isEqualTo("농구");
    }

    @Test
    @DisplayName("연말을 넘기는 방은 종료 연도까지 표시한다")
    void showsEndDateAcrossYearBoundary() {
        given(mapper.findSports(MANAGER_ID)).willReturn(List.of());
        RoomManagementRow yearEnd = row(9404L, "종료", 0, 6);
        yearEnd.setStartAt(LocalDateTime.of(2026, 12, 31, 23, 0));
        yearEnd.setEndAt(LocalDateTime.of(2027, 1, 1, 1, 0));
        given(mapper.findRooms(any())).willReturn(List.of(yearEnd));

        RoomManagementPage.Match match = service.findRooms(MANAGER_ID, null, "all", null, null)
                .getMatches().get(0);

        assertThat(match.getDateLabel()).isEqualTo("2026-12-31(목) 23:00~2027-01-01(금) 01:00");
        assertThat(match.getStatusLabel()).isEqualTo("이용 완료");
        assertThat(match.getStatusTone()).isEqualTo("neutral");
    }

    @Test
    @DisplayName("양수가 아닌 매칭방 ID는 조회하지 않고 null을 돌려준다")
    void rejectsNonPositiveMatchId() {
        assertThat(service.findDetail(MANAGER_ID, 0L)).isNull();
        assertThat(service.findDetail(MANAGER_ID, -1L)).isNull();
        assertThat(service.findDetail(MANAGER_ID, null)).isNull();
        assertThat(service.findDetail(null, 9401L)).isNull();
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("다른 관리자 시설·삭제된 시설·없는 방은 상세 대신 null이다")
    void hidesForeignOrMissingRoom() {
        given(mapper.findRoom(any())).willReturn(null);

        assertThat(service.findDetail(MANAGER_ID, 9406L)).isNull();
    }

    @Test
    @DisplayName("팀 배정 전 상세는 모든 신청을 보여 주고 대기 신청만 승인·반려할 수 있으며 참가자 표는 비운다")
    void buildsDetailBeforeAssignment() {
        RoomManagementRow room = row(9401L, "모집 중", 2, 8);
        given(mapper.findRoom(any())).willReturn(room);
        given(mapper.findApplications(any())).willReturn(List.of(
                application(9501L, "개인", "대기", "신청자하나", null),
                application(9504L, "동호회", "확정", "호스트비", " 테스트 동호회 ")));

        // 경기(11/6 18:00) 전 시각으로 고정해 실행 날짜와 관계없이 승인·반려 가능 여부를 확인한다.
        RoomManagementDetail detail = serviceAt(LocalDateTime.of(2026, 11, 5, 9, 0)).findDetail(MANAGER_ID, 9401L);

        assertThat(detail).isNotNull();
        assertThat(detail.isTeamAssigned()).isFalse();
        assertThat(detail.getParticipation()).isEqualTo("2 / 8명");
        assertThat(detail.getStatusLabel()).isEqualTo("모집 중");
        assertThat(detail.getStatusTone()).isEqualTo("good");
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::getApplicantName)
                .containsExactly("신청자하나", "호스트비");
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::getClubName)
                .containsExactly(null, "테스트 동호회");
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::getKindLabel)
                .containsExactly("개인 신청", "동호회 신청");
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::getStatusLabel)
                .containsExactly("확정 대기", "참가 확정");
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::isDecidable)
                .containsExactly(true, false);
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::getHistoryUserId)
                .containsExactly(10501L, 10504L);
        assertThat(detail.getApplications().get(0).getCreatedAtLabel()).isEqualTo("2026-11-01 10:00");
        assertThat(detail.getParticipants()).isEmpty();
        verify(mapper, never()).findParticipants(any());
    }

    @Test
    @DisplayName("팀 배정 후 상세는 확정 신청을 참가자 표로 옮기고 반려·취소 신청은 기록으로 남긴다")
    void movesConfirmedToParticipantsAfterAssignment() {
        RoomManagementRow room = row(9401L, "매칭 완료", 2, 8);
        given(mapper.findRoom(any())).willReturn(room);
        given(mapper.findApplications(any())).willReturn(List.of(
                application(9501L, "개인", "반려", "신청자하나", null),
                application(9502L, "개인", "확정", "참가자둘", null),
                application(9503L, "동호회", "취소", "호스트비", "테스트 동호회")));
        RoomManagementParticipantRow clubMember = participant("참가자셋", "확정", "B");
        clubMember.setClubName("테스트 동호회");
        given(mapper.findParticipants(any())).willReturn(List.of(
                participant("참가자둘", "확정", "A"), clubMember));

        RoomManagementDetail detail = service.findDetail(MANAGER_ID, 9401L);

        assertThat(detail.isTeamAssigned()).isTrue();
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::getStatusLabel)
                .containsExactly("신청 반려", "신청 취소");
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::isDecidable)
                .containsOnly(false);
        assertThat(detail.getParticipants()).extracting(RoomManagementDetail.Participant::getTeamLabel)
                .containsExactly("A팀", "B팀");
        assertThat(detail.getParticipants()).extracting(RoomManagementDetail.Participant::getClubName)
                .containsExactly(null, "테스트 동호회");
    }

    @Test
    @DisplayName("이미 시작한 모집 중 경기의 대기 신청은 승인·반려 버튼을 두지 않는다")
    void closesDecisionAfterStart() {
        RoomManagementRow room = row(9401L, "모집 중", 2, 8);
        room.setStartAt(LocalDateTime.of(2020, 1, 1, 10, 0));
        room.setEndAt(LocalDateTime.of(2020, 1, 1, 12, 0));
        given(mapper.findRoom(any())).willReturn(room);
        given(mapper.findApplications(any())).willReturn(List.of(
                application(9501L, "개인", "대기", "신청자하나", null)));

        RoomManagementDetail detail = service.findDetail(MANAGER_ID, 9401L);

        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::isDecidable)
                .containsExactly(false);
    }

    @Test
    @DisplayName("시작한 배정 완료 경기가 레이팅 반영 전이면 레이팅 제외 버튼을 활성화한다")
    void enablesRatingExclusionAfterStart() {
        RoomManagementRow room = room(9401L, "매칭 완료", 2026, 11, 6, 18, 0);
        room.setRatingStatus("WAITING");
        given(mapper.findRoom(any())).willReturn(room);

        RoomManagementDetail.Rating rating = serviceAt(LocalDateTime.of(2026, 11, 6, 18, 0))
                .findDetail(MANAGER_ID, 9401L).getRating();

        assertThat(rating.getLabel()).isEqualTo("반영 대기");
        assertThat(rating.isWaiting()).isTrue();
        assertThat(rating.isExcludable()).isTrue();
    }

    @Test
    @DisplayName("시작 전 배정 완료 경기는 레이팅 제외 버튼을 비활성으로 둔다")
    void disablesRatingExclusionBeforeStart() {
        RoomManagementRow room = room(9401L, "매칭 완료", 2026, 11, 6, 18, 0);
        room.setRatingStatus("WAITING");
        given(mapper.findRoom(any())).willReturn(room);

        RoomManagementDetail.Rating rating = serviceAt(LocalDateTime.of(2026, 11, 6, 17, 59))
                .findDetail(MANAGER_ID, 9401L).getRating();

        assertThat(rating.getLabel()).isEqualTo("경기 전");
        assertThat(rating.isWaiting()).isTrue();
        assertThat(rating.isExcludable()).isFalse();
    }

    @Test
    @DisplayName("이미 반영·제외된 경기는 상태만 보여 주고 제외 버튼을 두지 않는다")
    void showsProcessedRatingWithoutButton() {
        RoomManagementRow applied = room(9401L, "종료", 2026, 11, 6, 18, 0);
        applied.setRatingStatus("APPLIED");
        RoomManagementRow excluded = room(9402L, "매칭 완료", 2026, 11, 6, 18, 0);
        excluded.setRatingStatus("EXCLUDED");
        given(mapper.findRoom(any())).willReturn(applied, excluded);
        RoomManagementService later = serviceAt(LocalDateTime.of(2026, 11, 7, 10, 0));

        RoomManagementDetail.Rating appliedRating = later.findDetail(MANAGER_ID, 9401L).getRating();
        RoomManagementDetail.Rating excludedRating = later.findDetail(MANAGER_ID, 9402L).getRating();

        assertThat(appliedRating.getLabel()).isEqualTo("반영 완료");
        assertThat(excludedRating.getLabel()).isEqualTo("레이팅 제외됨");
        assertThat(List.of(appliedRating, excludedRating)).extracting(RoomManagementDetail.Rating::isWaiting)
                .containsOnly(false);
    }

    @Test
    @DisplayName("팀 배정 전이거나 레이팅 상태를 알 수 없으면 레이팅 섹션을 숨긴다")
    void hidesRatingWhenNotApplicable() {
        RoomManagementRow recruiting = room(9401L, "모집 중", 2026, 11, 6, 18, 0);
        recruiting.setRatingStatus("WAITING");
        RoomManagementRow unknown = room(9402L, "매칭 완료", 2026, 11, 6, 18, 0);
        unknown.setRatingStatus("READY");
        given(mapper.findRoom(any())).willReturn(recruiting, unknown);
        RoomManagementService later = serviceAt(LocalDateTime.of(2026, 11, 7, 10, 0));

        assertThat(later.findDetail(MANAGER_ID, 9401L).getRating()).isNull();
        assertThat(later.findDetail(MANAGER_ID, 9402L).getRating()).isNull();
    }

    @Test
    @DisplayName("상세 조회도 담당자 식별자를 조회 조건으로 넘긴다")
    void scopesDetailQueryByManager() {
        given(mapper.findRoom(any())).willReturn(row(9401L, "모집 중", 2, 8));
        given(mapper.findApplications(any())).willReturn(List.of());
        given(mapper.findParticipants(any())).willReturn(List.of());

        service.findDetail(MANAGER_ID, 9401L);

        ArgumentCaptor<RoomManagementQuery> captor = ArgumentCaptor.forClass(RoomManagementQuery.class);
        org.mockito.Mockito.verify(mapper).findApplications(captor.capture());
        assertThat(captor.getValue().getManagerUserId()).isEqualTo(MANAGER_ID);
        assertThat(captor.getValue().getMatchId()).isEqualTo(9401L);
        assertThat(captor.getValue().getParticipationStatuses()).containsExactly("대기", "확정");
    }

    @Test
    @DisplayName("대시보드는 필터와 무관하게 담당 시설 전체를 한 번 조회하고 24칸을 만든다")
    void dashboardAggregatesAllManagedRoomsWithoutFilters() {
        RoomManagementService roomService = serviceAt(LocalDateTime.of(2026, 11, 2, 14, 5));
        given(mapper.findRooms(any())).willReturn(List.of(
                room(9501L, "모집 중", 2026, 11, 2, 10, 0),
                room(9502L, "종료", 2026, 11, 2, 10, 30),
                room(9503L, "취소", 2026, 11, 2, 18, 0),
                room(9504L, "매칭 완료", 2026, 11, 3, 10, 0),
                room(9505L, "모집 중", 2026, 10, 31, 9, 0)));

        FacilityDashboard dashboard = roomService.findDashboard(MANAGER_ID);

        // 취소·종료·다른 날짜 방까지 담당 시설 전체가 집계된다.
        assertThat(dashboard.getMatchCount()).isEqualTo(5);
        assertThat(dashboard.getRecruitingCount()).isEqualTo(2);
        assertThat(dashboard.getHourlyBars()).hasSize(24);
        assertThat(dashboard.getHourlyBars()).extracting(FacilityDashboard.HourlyBar::getLabel)
                .first().isEqualTo("00시");
        assertThat(dashboard.getHourlyBars().get(23).getLabel()).isEqualTo("23시");

        // 오늘(11/2) 방만 차트에 들어가고, 그중 종료 상태만 '이용 완료'다.
        FacilityDashboard.HourlyBar ten = dashboard.getHourlyBars().get(10);
        assertThat(ten.getMatchCount()).isEqualTo(2);
        assertThat(ten.getCompletedCount()).isEqualTo(1);
        FacilityDashboard.HourlyBar eighteen = dashboard.getHourlyBars().get(18);
        assertThat(eighteen.getMatchCount()).isEqualTo(1);
        assertThat(eighteen.getCompletedCount()).isZero();
        assertThat(dashboard.getHourlyBars().get(9).getMatchCount()).isZero();

        assertThat(dashboard.getTodayLabel()).isEqualTo("2026-11-02(월)");
        assertThat(dashboard.getCollectedAt()).isEqualTo("2026-11-02 14:05");
        assertThat(dashboard.getScopeName()).isEqualTo("담당 시설 전체");

        // 집계 조회는 검색어·상태·종목·시설 조건을 넣지 않는다.
        ArgumentCaptor<RoomManagementQuery> captor = ArgumentCaptor.forClass(RoomManagementQuery.class);
        org.mockito.Mockito.verify(mapper).findRooms(captor.capture());
        RoomManagementQuery query = captor.getValue();
        assertThat(query.getManagerUserId()).isEqualTo(MANAGER_ID);
        assertThat(query.getQ()).isNull();
        assertThat(query.getStatusValue()).isNull();
        assertThat(query.getSportId()).isNull();
        assertThat(query.getFacilityId()).isNull();
        assertThat(query.getParticipationStatuses()).containsExactly("대기", "확정");
    }

    @Test
    @DisplayName("차트 높이는 24칸 중 최대 방 수의 상대 백분율이고 모두 0이면 0이다")
    void dashboardBarHeightsAreRelativeToBusiestHour() {
        RoomManagementService roomService = serviceAt(LocalDateTime.of(2026, 11, 2, 14, 5));
        given(mapper.findRooms(any())).willReturn(List.of(
                room(9601L, "종료", 2026, 11, 2, 10, 0),
                room(9602L, "종료", 2026, 11, 2, 10, 0),
                room(9603L, "모집 중", 2026, 11, 2, 10, 0),
                room(9604L, "모집 중", 2026, 11, 2, 10, 0),
                room(9605L, "모집 중", 2026, 11, 2, 11, 0)));

        FacilityDashboard dashboard = roomService.findDashboard(MANAGER_ID);

        FacilityDashboard.HourlyBar ten = dashboard.getHourlyBars().get(10);
        assertThat(ten.getMatchHeight()).isEqualTo(100);
        assertThat(ten.getCompletedHeight()).isEqualTo(50);
        FacilityDashboard.HourlyBar eleven = dashboard.getHourlyBars().get(11);
        assertThat(eleven.getMatchHeight()).isEqualTo(25);
        assertThat(eleven.getCompletedHeight()).isZero();

        // 모든 칸이 0이면 높이도 0이다(0으로 나누지 않는다).
        given(mapper.findRooms(any())).willReturn(List.of());
        assertThat(roomService.findDashboard(MANAGER_ID).getHourlyBars())
                .allSatisfy(bar -> {
                    assertThat(bar.getMatchHeight()).isZero();
                    assertThat(bar.getCompletedHeight()).isZero();
                    assertThat(bar.getMatchCount()).isZero();
                });
    }

    @Test
    @DisplayName("최근 매칭방은 번호 내림차순 5개이고 없으면 빈 목록이다")
    void dashboardRecentMatchesUseMatchIdOrder() {
        RoomManagementService roomService = serviceAt(LocalDateTime.of(2026, 11, 2, 14, 5));
        given(mapper.findRooms(any())).willReturn(List.of(
                room(9701L, "모집 중", 2026, 11, 2, 10, 0),
                room(9706L, "종료", 2026, 11, 2, 11, 0),
                room(9703L, "취소", 2026, 11, 2, 12, 0),
                room(9705L, "매칭 완료", 2026, 11, 2, 13, 0),
                room(9702L, "모집 중", 2026, 11, 2, 14, 0),
                room(9704L, "종료", 2026, 11, 2, 15, 0)));

        List<RoomManagementPage.Match> recent = roomService.findDashboard(MANAGER_ID)
                .getRecentMatches();

        assertThat(recent).hasSize(RoomManagementService.RECENT_MATCH_LIMIT);
        assertThat(recent).extracting(RoomManagementPage.Match::getMatchId)
                .containsExactly(9706L, 9705L, 9704L, 9703L, 9702L);
        // 목록 화면과 같은 표시 모델을 쓰므로 상태·참여 문구도 그대로 채워진다.
        assertThat(recent.get(0).getStatusLabel()).isEqualTo("이용 완료");
        assertThat(recent.get(0).getParticipation()).isEqualTo("2 / 8명");

        given(mapper.findRooms(any())).willReturn(List.of());
        assertThat(roomService.findDashboard(MANAGER_ID).getRecentMatches()).isEmpty();
    }

    @Test
    @DisplayName("관리자 식별자가 없으면 조회 없이 0건 대시보드를 돌려준다")
    void dashboardIsEmptyWithoutManager() {
        FacilityDashboard dashboard = service.findDashboard(null);

        assertThat(dashboard.getMatchCount()).isZero();
        assertThat(dashboard.getRecruitingCount()).isZero();
        assertThat(dashboard.getHourlyBars()).hasSize(24);
        assertThat(dashboard.getRecentMatches()).isEmpty();
        assertThat(dashboard.getRecentRequests()).isEmpty();
        assertThat(dashboard.getScopeName()).isEqualTo("담당 시설 전체");
        assertThat(dashboard.getTodayLabel()).isNotBlank();
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("최근 요청은 매퍼가 돌려준 순서대로 문구를 만들고 동호회 신청은 동호회명을 쓴다")
    void dashboardMapsRecentRequests() {
        RoomManagementService roomService = serviceAt(LocalDateTime.of(2026, 11, 2, 14, 5));
        given(mapper.findRooms(any())).willReturn(List.of());
        given(mapper.findRecentApplications(MANAGER_ID, RoomManagementService.RECENT_REQUEST_LIMIT))
                .willReturn(List.of(
                        recentRequest(9511L, 9403L, "개인", "확정", "신청자하나", null),
                        recentRequest(9507L, 9402L, "동호회", "대기", "호스트비", "Match 테스트 동호회"),
                        recentRequest(9503L, 9401L, "개인", "취소", "  ", null)));

        List<FacilityDashboard.RecentRequest> requests = roomService.findDashboard(MANAGER_ID)
                .getRecentRequests();

        assertThat(requests).hasSize(3);
        assertThat(requests).extracting(FacilityDashboard.RecentRequest::getMatchId)
                .containsExactly(9403L, 9402L, 9401L);
        assertThat(requests).extracting(FacilityDashboard.RecentRequest::getStatusLabel)
                .containsExactly("참가 확정", "확정 대기", "신청 취소");
        assertThat(requests).extracting(FacilityDashboard.RecentRequest::getStatusTone)
                .containsExactly("good", "warn", "neutral");
        assertThat(requests).extracting(FacilityDashboard.RecentRequest::getApplicantName)
                .containsExactly("신청자하나", "Match 테스트 동호회", "신청자 미상");
        assertThat(requests.get(0).getMatchTitle()).isEqualTo("금요 농구 모집");
        assertThat(requests.get(0).getFacilityName()).isEqualTo("관리 시설 알파");
        assertThat(requests.get(0).getCreatedAtLabel()).isEqualTo("2026-11-01 10:00");
    }

    @Test
    @DisplayName("최근 요청이 없으면 빈 목록이고 조회는 담당자·최대 5건으로 제한한다")
    void dashboardRecentRequestsDefaultToEmptyList() {
        RoomManagementService roomService = serviceAt(LocalDateTime.of(2026, 11, 2, 14, 5));
        given(mapper.findRooms(any())).willReturn(List.of());
        given(mapper.findRecentApplications(MANAGER_ID, RoomManagementService.RECENT_REQUEST_LIMIT))
                .willReturn(List.of());

        assertThat(roomService.findDashboard(MANAGER_ID).getRecentRequests()).isEmpty();

        org.mockito.Mockito.verify(mapper).findRecentApplications(MANAGER_ID,
                RoomManagementService.RECENT_REQUEST_LIMIT);
        assertThat(RoomManagementService.RECENT_REQUEST_LIMIT).isEqualTo(5);

        // 매퍼가 null을 돌려줘도 화면 모델은 빈 목록을 유지한다.
        given(mapper.findRecentApplications(MANAGER_ID, RoomManagementService.RECENT_REQUEST_LIMIT))
                .willReturn(null);
        assertThat(roomService.findDashboard(MANAGER_ID).getRecentRequests()).isEmpty();
    }

    private RoomManagementQuery capturedQuery() {
        ArgumentCaptor<RoomManagementQuery> captor = ArgumentCaptor.forClass(RoomManagementQuery.class);
        org.mockito.Mockito.verify(mapper).findRooms(captor.capture());
        return captor.getValue();
    }

    /** 날짜 경계 검증을 위해 조회 시각을 고정한 서비스. */
    private RoomManagementService serviceAt(LocalDateTime at) {
        return new RoomManagementService(mapper,
                Clock.fixed(at.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")));
    }

    private static RoomManagementRow room(long matchId, String status, int year, int month, int day,
            int hour, int minute) {
        RoomManagementRow row = row(matchId, status, 2, 8);
        row.setStartAt(LocalDateTime.of(year, month, day, hour, minute));
        row.setEndAt(LocalDateTime.of(year, month, day, hour, minute).plusHours(2));
        return row;
    }

    private static RoomManagementRow row(long matchId, String status, int occupied, int max) {
        RoomManagementRow row = new RoomManagementRow();
        row.setMatchId(matchId);
        row.setTitle("금요 농구 모집");
        row.setFacilityId(9301L);
        row.setFacilityName("관리 시설 알파");
        row.setSportId(9201L);
        row.setSportName("농구");
        row.setStartAt(LocalDateTime.of(2026, 11, 6, 18, 0));
        row.setEndAt(LocalDateTime.of(2026, 11, 6, 20, 0));
        row.setMaxParticipants(max);
        row.setOccupiedCount(occupied);
        row.setStatus(status);
        return row;
    }

    private static RoomManagementApplicationRow application(long applicationId, String type, String status,
            String applicantName, String clubName) {
        RoomManagementApplicationRow row = new RoomManagementApplicationRow();
        row.setApplicationId(applicationId);
        row.setApplicantUserId(applicationId + 1000);
        row.setApplicationType(type);
        row.setStatus(status);
        row.setApplicantName(applicantName);
        row.setClubName(clubName);
        row.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return row;
    }

    private static RoomManagementParticipantRow participant(String nickname, String status, String side) {
        RoomManagementParticipantRow row = new RoomManagementParticipantRow();
        row.setNickname(nickname);
        row.setParticipationStatus(status);
        row.setMatchSide(side);
        row.setJoinedAt(LocalDateTime.of(2026, 11, 1, 10, 30));
        return row;
    }

    private static RoomManagementRecentRequestRow recentRequest(long applicationId, long matchId,
            String type, String status, String applicantName, String clubName) {
        RoomManagementRecentRequestRow row = new RoomManagementRecentRequestRow();
        row.setApplicationId(applicationId);
        row.setMatchId(matchId);
        row.setMatchTitle("금요 농구 모집");
        row.setFacilityName("관리 시설 알파");
        row.setApplicationType(type);
        row.setStatus(status);
        row.setApplicantName(applicantName);
        row.setClubName(clubName);
        row.setCreatedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        return row;
    }
}
