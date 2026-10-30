package co.kr.spoit.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.kr.spoit.match.dto.RoomManagementApplicationRow;
import co.kr.spoit.match.dto.RoomManagementDetail;
import co.kr.spoit.match.dto.RoomManagementPage;
import co.kr.spoit.match.dto.RoomManagementParticipantRow;
import co.kr.spoit.match.dto.RoomManagementQuery;
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
    @DisplayName("상세는 신청·참가자를 결정적 순서의 화면 문구로 만들고 컬렉션은 비어 있지 않다")
    void buildsDetailLabels() {
        RoomManagementRow room = row(9401L, "모집 중", 2, 8);
        given(mapper.findRoom(any())).willReturn(room);
        given(mapper.findApplications(any())).willReturn(List.of(
                application("개인", "대기", "ROOM-TEST-9501", "신청자하나", null),
                application("동호회", "확정", "ROOM-TEST-9504", "호스트비", "테스트 동호회")));
        given(mapper.findParticipants(any())).willReturn(List.of(
                participant("참가자둘", "확정", "A"),
                participant("참가자셋", "대기", null)));

        RoomManagementDetail detail = service.findDetail(MANAGER_ID, 9401L);

        assertThat(detail).isNotNull();
        assertThat(detail.getParticipation()).isEqualTo("2 / 8명");
        assertThat(detail.getStatusLabel()).isEqualTo("모집 중");
        assertThat(detail.getStatusTone()).isEqualTo("good");
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::getApplicantName)
                .containsExactly("신청자하나", "테스트 동호회");
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::getKindLabel)
                .containsExactly("개인 신청", "동호회 신청");
        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::getStatusLabel)
                .containsExactly("확정 대기", "참가 확정");
        assertThat(detail.getApplications().get(0).getCreatedAtLabel()).isEqualTo("2026-11-01 10:00");
        assertThat(detail.getParticipants()).extracting(RoomManagementDetail.Participant::getTeamLabel)
                .containsExactly("A팀", "미배정");
        assertThat(detail.getParticipants()).extracting(RoomManagementDetail.Participant::getStatusLabel)
                .containsExactly("참가 확정", "확정 대기");
    }

    @Test
    @DisplayName("신청 상태 문구는 DB 상태 값으로 매핑하고 null·알 수 없는 값은 원문/기본 문구로 둔다")
    void labelsApplicationStatusesFromDbValues() {
        given(mapper.findRoom(any())).willReturn(row(9401L, "모집 중", 2, 8));
        given(mapper.findApplications(any())).willReturn(List.of(
                application("개인", "취소", "ROOM-TEST-9601", "신청자하나", null),
                application("개인", "반려", "ROOM-TEST-9602", "신청자둘", null),
                application("개인", "알 수 없음", "ROOM-TEST-9603", "신청자셋", null),
                application("개인", null, "ROOM-TEST-9604", "신청자넷", null)));
        given(mapper.findParticipants(any())).willReturn(List.of());

        RoomManagementDetail detail = service.findDetail(MANAGER_ID, 9401L);

        assertThat(detail.getApplications()).extracting(RoomManagementDetail.Application::getStatusLabel)
                .containsExactly("신청 취소", "신청 반려", "알 수 없음", "상태 미설정");
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

    private RoomManagementQuery capturedQuery() {
        ArgumentCaptor<RoomManagementQuery> captor = ArgumentCaptor.forClass(RoomManagementQuery.class);
        org.mockito.Mockito.verify(mapper).findRooms(captor.capture());
        return captor.getValue();
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

    private static RoomManagementApplicationRow application(String type, String status, String receiptNo,
            String applicantName, String clubName) {
        RoomManagementApplicationRow row = new RoomManagementApplicationRow();
        row.setApplicationType(type);
        row.setStatus(status);
        row.setReceiptNo(receiptNo);
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
}
