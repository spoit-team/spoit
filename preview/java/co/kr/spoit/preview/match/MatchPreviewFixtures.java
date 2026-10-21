package co.kr.spoit.preview.match;

import co.kr.spoit.preview.match.MatchCommonModels.ActiveFilter;
import co.kr.spoit.preview.match.MatchCommonModels.ClubMemberView;
import co.kr.spoit.preview.match.MatchCommonModels.ClubView;
import co.kr.spoit.preview.match.MatchCommonModels.DateFilter;
import co.kr.spoit.preview.match.MatchCommonModels.Fact;
import co.kr.spoit.preview.match.MatchCommonModels.FacilityView;
import co.kr.spoit.preview.match.MatchJoinModels.JoinCompletionView;
import co.kr.spoit.preview.match.MatchJoinModels.JoinMatchView;
import co.kr.spoit.preview.match.MatchJoinModels.JoinSelectionView;
import co.kr.spoit.preview.match.MatchListModels.MatchCardView;
import co.kr.spoit.preview.match.MatchDetailModels.MatchDetailPage;
import co.kr.spoit.preview.match.MatchDetailModels.MatchDetailView;
import co.kr.spoit.preview.match.MatchJoinModels.MatchJoinPage;
import co.kr.spoit.preview.match.MatchListModels.MatchListPage;
import co.kr.spoit.preview.match.MatchDetailModels.MyApplicationView;
import co.kr.spoit.preview.match.MatchCommonModels.Person;
import co.kr.spoit.preview.match.MatchCommonModels.RegionFilter;
import co.kr.spoit.preview.match.MatchListModels.RoomDialogDetail;
import co.kr.spoit.preview.match.MatchCommonModels.SearchCriteria;
import co.kr.spoit.preview.match.MatchCommonModels.SportFilter;
import co.kr.spoit.preview.match.MatchCommonModels.TeamView;
import java.util.List;

/**
 * Preview-only fixture data. These values exist so every match screen can render
 * without a database; they are never used for real reservation behaviour.
 */
public final class MatchPreviewFixtures {

    private static final List<String> HOUR_OPTIONS = List.of(
            "09:00", "10:00", "11:00", "12:00",
            "13:00", "14:00", "15:00", "16:00",
            "17:00", "18:00", "19:00", "20:00");

    private MatchPreviewFixtures() {
    }

    public static MatchListPage listPage() {
        return MatchListPage.builder()
                .search(SearchCriteria.builder()
                        .sportId(0L)
                        .regionId(null)
                        .dateIdx(null)
                        .hours(List.of())
                        .build())
                .sports(List.of(
                        SportFilter.builder().sportId(0L).name("전체").count(3).build(),
                        SportFilter.builder().sportId(1L).name("테니스").count(2).build(),
                        SportFilter.builder().sportId(2L).name("배드민턴").count(1).build(),
                        SportFilter.builder().sportId(3L).name("족구").count(0).build()))
                .districts(List.of(
                        RegionFilter.builder().regionId(0L).name("전체 지역").build(),
                        RegionFilter.builder().regionId(11L).name("마포구").build(),
                        RegionFilter.builder().regionId(12L).name("송파구").build(),
                        RegionFilter.builder().regionId(13L).name("영등포구").build()))
                .dates(List.of(
                        DateFilter.builder().idx(0).label("오늘").build(),
                        DateFilter.builder().idx(1).label("내일").build(),
                        DateFilter.builder().idx(2).label("이번 주말").build()))
                .hourOptions(HOUR_OPTIONS)
                .selectedHours(List.of())
                .selectedHoursLabel(null)
                .selectedSportName("전체")
                .sportImageUrl(null)
                .totalRoomCount(3)
                .roomCount(3)
                .recruitingCount(2)
                .matchedCount(1)
                .activeFilters(List.of())
                .matches(List.of(match101(), match102(), match103()))
                .build();
    }

    public static MatchDetailPage detailPage(long matchId) {
        MatchDetailView view = MatchDetailView.builder()
                .matchId(matchId)
                .title("주말 테니스 복식 4인 매치")
                .statusClass("is-recruiting")
                .statusLabel("모집 중")
                .sportName("테니스")
                .place("마포구민체육센터 테니스장")
                .dateLabel("10월 24일 (토)")
                .time("18:00 ~ 20:00")
                .feeText("12,000")
                .facility(FacilityView.builder()
                        .image("/images/tennis-court.jpg")
                        .imageAlt("마포구민체육센터 테니스장 전경")
                        .fallbackName("테니스장")
                        .caption("마포구민체육센터 · 테니스장 2번 코트")
                        .visible(true)
                        .facts(List.of(
                                Fact.builder().label("주소").value("서울 마포구 월드컵로 12").build(),
                                Fact.builder().label("코트").value("테니스장 2번 코트").build(),
                                Fact.builder().label("이용 시간").value("18:00 ~ 20:00").build()))
                        .build())
                .facts(List.of(
                        Fact.builder().label("종목·실력").value("테니스 · 초중급").build(),
                        Fact.builder().label("경기 방식").value("복식 4인 1세트").build(),
                        Fact.builder().label("참가비").value("12,000원 / 1인").build(),
                        Fact.builder().label("준비물").value("라켓, 실내 테니스화").build()))
                .note("경기 시작 10분 전까지 코트 앞에서 만나 주세요. 우천 시 일정은 개별 안내됩니다.")
                .confirmedCount(2)
                .pendingConfirmationCount(1)
                .maxParticipants(4)
                .ratingNote("최근 10경기 기준")
                .teamA(TeamView.builder()
                        .members(List.of(
                                Person.builder().nickname("김민준").initial("김").host(true)
                                        .meta("A팀 · 방장").roleLabel("방장").build(),
                                Person.builder().nickname("이서연").initial("이")
                                        .meta("A팀 · 확정").roleLabel("참가자").build()))
                        .averageText("3.4").build())
                .teamB(TeamView.builder()
                        .members(List.of(
                                Person.builder().nickname("박지훈").initial("박")
                                        .meta("B팀 · 확정").roleLabel("참가자").build()))
                        .averageText("3.1").build())
                .pendingParticipants(List.of(
                        Person.builder().nickname("최유진").initial("최")
                                .meta("신청 확인 대기").roleLabel("확정 대기").build()))
                .applyMessageClass("is-open")
                .applyMessage("1자리 남았습니다. 참가 신청을 기다리고 있어요.")
                .occupancyLabel("3 / 4명")
                .occupiedSeats(3)
                .occupancyPercent(75)
                .occupancyCaption("확정 2명 · 확정 대기 1명 포함")
                .myApplication(MyApplicationView.builder()
                        .visible(false)
                        .kindLabel("개인 참가")
                        .seatsText("1명")
                        .receiptNo("")
                        .createdAtText("")
                        .statusClass("")
                        .statusLabel("")
                        .teamLabel(null)
                        .note("")
                        .build())
                .canApply(true)
                .hasTeams(true)
                .clubs(List.of(
                        ClubView.builder()
                                .clubId(21L)
                                .clubName("마포 테니스 클럽")
                                .leaderName("김민준")
                                .leaderInitial("김")
                                .leaderCaption("팀장 · 자동 포함")
                                .memberCount(5)
                                .averageRatingText("3.5")
                                .selectionCount(2)
                                .leaderAuto(true)
                                .leaderIsApplicant(false)
                                .members(List.of(
                                        ClubMemberView.builder().clubMemberId(2101L).nickname("이서연")
                                                .initial("이").ratingText("3.2").leaderOnly(false)
                                                .applicant(false).build(),
                                        ClubMemberView.builder().clubMemberId(2102L).nickname("박지훈")
                                                .initial("박").ratingText("3.1").leaderOnly(false)
                                                .applicant(false).build()))
                                .build(),
                        ClubView.builder()
                                .clubId(22L)
                                .clubName("한강 스매시")
                                .leaderName("최유진")
                                .leaderInitial("최")
                                .leaderCaption("팀장 · 자동 포함")
                                .memberCount(4)
                                .averageRatingText("3.8")
                                .selectionCount(1)
                                .leaderAuto(true)
                                .leaderIsApplicant(false)
                                .members(List.of())
                                .build()))
                .availableSeats(1)
                .build();

        return MatchDetailPage.builder()
                .unavailableMessage(null)
                .match(view)
                .build();
    }

    public static MatchJoinPage joinPage(long matchId) {
        return MatchJoinPage.builder()
                .matchId(matchId)
                .completed(false)
                .unavailableMessage(null)
                .match(JoinMatchView.builder()
                        .matchId(matchId)
                        .sportName("테니스")
                        .statusLabel("모집 중")
                        .title("주말 테니스 복식 4인 매치")
                        .facilityName("마포구민체육센터")
                        .dateLabel("10월 24일 (토)")
                        .time("18:00 ~ 20:00")
                        .place("마포구민체육센터 테니스장")
                        .levelLabel("초중급")
                        .feeText("12,000")
                        .capacityLabel("3 / 4명")
                        .maxParticipants(4)
                        .occupiedSeats(3)
                        .occupancyPercent(75)
                        .remainingLabel("1자리 남음")
                        .build())
                .selection(JoinSelectionView.builder()
                        .kindLabel("개인 신청")
                        .applicantName("김민준")
                        .seats(1)
                        .applicantPhone("010-1234-5678")
                        .applicationType("individual")
                        .participants(List.of(
                                Person.builder().nickname("김민준").initial("김").host(true)
                                        .meta("신청자").roleLabel("신청 대표").build()))
                        .build())
                .canReserve(true)
                .capacityNotice("현재 남은 자리를 확인해 주세요.")
                .error(null)
                .feeTotalText("12,000원")
                .feeSummaryNote("참가 인원 기준 안내 금액입니다.")
                .completion(JoinCompletionView.builder()
                        .title("매치 참가 신청이 접수되었습니다")
                        .message("신청이 접수되었습니다. 참가 확정은 서버 처리 결과를 따릅니다.")
                        .roomTitle("주말 테니스 복식 4인 매치")
                        .applicantText("김민준 · 1명 신청")
                        .participantText("김민준")
                        .receiptNo("P-20261024-001")
                        .stateLabel("신청 접수")
                        .paymentGuidance("결제는 이 화면에서 처리하지 않습니다.")
                        .totalText("12,000원")
                        .teamAssigned(false)
                        .teamACount(0)
                        .teamBCount(0)
                        .build())
                .build();
    }

    private static MatchCardView match101() {
        return MatchCardView.builder()
                .matchId(101L)
                .sportName("테니스")
                .photoUrl("/images/tennis-court.jpg")
                .photoAlt("테니스 코트")
                .facilityDistrictName("마포구")
                .levelLabel("초중급")
                .statusClass("is-matched")
                .statusLabel("팀 배정 완료")
                .title("평일 저녁 테니스 복식 매치")
                .dateLabel("10월 22일 (목)")
                .time("19:00 ~ 21:00")
                .place("마포구민체육센터 테니스장")
                .feeText("14,000")
                .gender("성별 무관")
                .age("20대~40대")
                .avatarParticipants(List.of(
                        Person.builder().nickname("김민준").initial("김").host(true).build(),
                        Person.builder().nickname("이서연").initial("이").build(),
                        Person.builder().nickname("박지훈").initial("박").build()))
                .extraParticipantCount(1)
                .participantSummary("4명 확정 / 4명")
                .remainingSeats(0)
                .remainingLabel("정원 마감")
                .pendingConfirmationCount(0)
                .maxParticipants(4)
                .occupiedSeats(4)
                .occupiedPercent(100)
                .showTeams(true)
                .teamACount(2)
                .teamBCount(2)
                .canApply(false)
                .actionLabel("예약 마감")
                .participantCount(4)
                .detail(RoomDialogDetail.builder()
                        .badge("팀 배정 완료")
                        .title("평일 저녁 테니스 복식 매치")
                        .facilityName("마포구민체육센터")
                        .facts(List.of(
                                Fact.builder().label("일시").value("10월 22일 (목) 19:00 ~ 21:00").build(),
                                Fact.builder().label("종목·실력").value("테니스 · 초중급").build(),
                                Fact.builder().label("참가비").value("14,000원 / 1인").build()))
                        .note("정원이 모두 채워져 A·B팀이 자동 배정되었습니다.")
                        .facilityVisible(true)
                        .facilityFacts(List.of(
                                Fact.builder().label("주소").value("서울 마포구 월드컵로 12").build(),
                                Fact.builder().label("코트").value("테니스장 1번 코트").build()))
                        .matched(true)
                        .teamACount(2)
                        .teamBCount(2)
                        .teamA(List.of(
                                Person.builder().nickname("김민준").initial("김").host(true).build(),
                                Person.builder().nickname("이서연").initial("이").build()))
                        .teamB(List.of(
                                Person.builder().nickname("박지훈").initial("박").build(),
                                Person.builder().nickname("최유진").initial("최").build()))
                        .participants(List.of())
                        .pendingParticipants(List.of())
                        .canJoin(false)
                        .build())
                .build();
    }

    private static MatchCardView match102() {
        return MatchCardView.builder()
                .matchId(102L)
                .sportName("테니스")
                .photoUrl("/images/tennis-court.jpg")
                .photoAlt("테니스 코트")
                .facilityDistrictName("마포구")
                .levelLabel("초중급")
                .statusClass("is-recruiting")
                .statusLabel("모집 중")
                .title("주말 테니스 복식 4인 매치")
                .dateLabel("10월 24일 (토)")
                .time("18:00 ~ 20:00")
                .place("마포구민체육센터 테니스장")
                .feeText("12,000")
                .gender("성별 무관")
                .age("20대~40대")
                .avatarParticipants(List.of(
                        Person.builder().nickname("김민준").initial("김").host(true).build(),
                        Person.builder().nickname("이서연").initial("이").build(),
                        Person.builder().nickname("박지훈").initial("박").build()))
                .extraParticipantCount(0)
                .participantSummary("2명 확정 / 4명")
                .remainingSeats(1)
                .remainingLabel("1자리 남음")
                .pendingConfirmationCount(1)
                .maxParticipants(4)
                .occupiedSeats(3)
                .occupiedPercent(75)
                .showTeams(false)
                .teamACount(0)
                .teamBCount(0)
                .canApply(true)
                .actionLabel("매치 참가하기")
                .participantCount(2)
                .detail(RoomDialogDetail.builder()
                        .badge("모집 중")
                        .title("주말 테니스 복식 4인 매치")
                        .facilityName("마포구민체육센터")
                        .facts(List.of(
                                Fact.builder().label("일시").value("10월 24일 (토) 18:00 ~ 20:00").build(),
                                Fact.builder().label("종목·실력").value("테니스 · 초중급").build(),
                                Fact.builder().label("참가비").value("12,000원 / 1인").build()))
                        .note("경기 시작 10분 전까지 코트 앞에서 만나 주세요.")
                        .facilityVisible(true)
                        .facilityFacts(List.of(
                                Fact.builder().label("주소").value("서울 마포구 월드컵로 12").build(),
                                Fact.builder().label("코트").value("테니스장 2번 코트").build()))
                        .matched(false)
                        .teamACount(0)
                        .teamBCount(0)
                        .teamA(List.of())
                        .teamB(List.of())
                        .participants(List.of(
                                Person.builder().nickname("김민준").initial("김").host(true).build(),
                                Person.builder().nickname("이서연").initial("이").build()))
                        .pendingParticipants(List.of(
                                Person.builder().nickname("최유진").initial("최").build()))
                        .canJoin(true)
                        .build())
                .build();
    }

    private static MatchCardView match103() {
        return MatchCardView.builder()
                .matchId(103L)
                .sportName("배드민턴")
                .photoUrl("/images/badminton-court.jpg")
                .photoAlt("배드민턴 코트")
                .facilityDistrictName("송파구")
                .levelLabel("입문")
                .statusClass("is-recruiting")
                .statusLabel("모집 중")
                .title("초보 환영 배드민턴 복식")
                .dateLabel("10월 25일 (일)")
                .time("10:00 ~ 12:00")
                .place("송파체육문화회관 배드민턴장")
                .feeText("8,000")
                .gender("성별 무관")
                .age("전 연령")
                .avatarParticipants(List.of(
                        Person.builder().nickname("정하늘").initial("정").host(true).build()))
                .extraParticipantCount(0)
                .participantSummary("1명 확정 / 6명")
                .remainingSeats(5)
                .remainingLabel("5자리 남음")
                .pendingConfirmationCount(0)
                .maxParticipants(6)
                .occupiedSeats(1)
                .occupiedPercent(17)
                .showTeams(false)
                .teamACount(0)
                .teamBCount(0)
                .canApply(true)
                .actionLabel("매치 참가하기")
                .participantCount(1)
                .detail(RoomDialogDetail.builder()
                        .badge("모집 중")
                        .title("초보 환영 배드민턴 복식")
                        .facilityName("송파체육문화회관")
                        .facts(List.of(
                                Fact.builder().label("일시").value("10월 25일 (일) 10:00 ~ 12:00").build(),
                                Fact.builder().label("종목·실력").value("배드민턴 · 입문").build(),
                                Fact.builder().label("참가비").value("8,000원 / 1인").build()))
                        .note("라켓은 현장에서 대여할 수 있습니다.")
                        .facilityVisible(true)
                        .facilityFacts(List.of(
                                Fact.builder().label("주소").value("서울 송파구 올림픽로 44").build(),
                                Fact.builder().label("코트").value("배드민턴장 3번 코트").build()))
                        .matched(false)
                        .teamACount(0)
                        .teamBCount(0)
                        .teamA(List.of())
                        .teamB(List.of())
                        .participants(List.of(
                                Person.builder().nickname("정하늘").initial("정").host(true).build()))
                        .pendingParticipants(List.of())
                        .canJoin(true)
                        .build())
                .build();
    }
}
