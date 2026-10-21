package co.kr.spoit.preview.member;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import co.kr.spoit.preview.member.MemberAuthModels.LoginPageDto;
import co.kr.spoit.preview.member.MemberAuthModels.RegionOptionDto;
import co.kr.spoit.preview.member.MemberAuthModels.SignupPageDto;

/**
 * Preview-only member/auth screens backed by deterministic fixtures.
 */
@Controller
public class MemberPreviewController {

    @GetMapping("/login")
    public String login(Model model) {
        LoginPageDto page = LoginPageDto.builder()
                .redirect("/mypage")
                .email("")
                .remember(true)
                .build();

        model.addAttribute("error", page.getError());
        model.addAttribute("message", page.getMessage());
        model.addAttribute("redirect", page.getRedirect());
        model.addAttribute("email", page.getEmail());
        model.addAttribute("remember", page.isRemember());
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signup(Model model) {
        SignupPageDto page = SignupPageDto.builder()
                .signupForm(SignupPageDto.SignupForm.builder()
                        .agreeService(false)
                        .agreePrivacy(false)
                        .marketingOptIn(false)
                        .role("member")
                        .facilityName("")
                        .email("")
                        .phone("010-0000-0000")
                        .nickname("새플레이어")
                        .regionId(1L)
                        .build())
                .districts(regionOptions())
                .build();

        model.addAttribute("signupForm", page.getSignupForm());
        model.addAttribute("districts", page.getDistricts());
        model.addAttribute("error", page.getError());
        model.addAttribute("message", page.getMessage());
        return "auth/signup";
    }

    @GetMapping("/mypage")
    public String myPage(Model model) {
        MyPageDto page = myPageFixture();

        model.addAttribute("member", page.getMember());
        model.addAttribute("summary", page.getSummary());
        model.addAttribute("otherSports", page.getOtherSports());
        model.addAttribute("sports", page.getSports());
        model.addAttribute("selectedSport", page.getSelectedSport());
        model.addAttribute("historyTotalCount", page.getHistoryTotalCount());
        model.addAttribute("historyFilterLabel", page.getHistoryFilterLabel());
        model.addAttribute("historyCount", page.getHistoryCount());
        model.addAttribute("history", page.getHistory());
        model.addAttribute("historyEmptyNote", page.getHistoryEmptyNote());
        model.addAttribute("currentReservations", page.getCurrentReservations());
        model.addAttribute("pastReservations", page.getPastReservations());
        model.addAttribute("notifications", page.getNotifications());
        model.addAttribute("message", page.getMessage());
        return "mypage/index";
    }

    @GetMapping("/mypage/profile")
    public String profile(Model model) {
        ProfilePageDto page = ProfilePageDto.builder()
                .profile(ProfilePageDto.ProfileForm.builder()
                        .representativeSportId(1L)
                        .representativeRatingText("3.75")
                        .representativeRatingNote("농구 확정 매치 7경기 평균 레이팅입니다.")
                        .email("player@example.com")
                        .nickname("플레이어")
                        .phone("010-1234-5678")
                        .regionId(1L)
                        .build())
                .sports(List.of(
                        ProfilePageDto.SportOption.builder()
                                .sportId(1L)
                                .name("농구")
                                .ratingText("3.75")
                                .ratingNote("확정 매치 7경기 평균 레이팅입니다.")
                                .build(),
                        ProfilePageDto.SportOption.builder()
                                .sportId(2L)
                                .name("풋살")
                                .ratingText("3.50")
                                .ratingNote("확정 매치 3경기 평균 레이팅입니다.")
                                .build(),
                        ProfilePageDto.SportOption.builder()
                                .sportId(3L)
                                .name("배드민턴")
                                .ratingText(null)
                                .ratingNote(null)
                                .build()))
                .districts(regionOptions())
                .build();

        model.addAttribute("profile", page.getProfile());
        model.addAttribute("sports", page.getSports());
        model.addAttribute("districts", page.getDistricts());
        model.addAttribute("error", page.getError());
        model.addAttribute("message", page.getMessage());
        return "mypage/profile";
    }

    private MyPageDto myPageFixture() {
        return MyPageDto.builder()
                .member(MyPageDto.MemberView.builder()
                        .initial("플")
                        .nickname("플레이어")
                        .roleLabel("일반 사용자")
                        .email("player@example.com")
                        .regionText("서울 송파구 · 잠실권")
                        .build())
                .summary(MyPageDto.SummaryView.builder()
                        .representativeRatingText("3.75")
                        .representativeSportId(1L)
                        .representativeSportName("농구")
                        .skillNote("참가 확정 매치의 개인 레이팅 평균을 표시합니다.")
                        .confirmedCount(12)
                        .completedCount(9)
                        .waitingCount(3)
                        .otherSportsNote("매칭 참가 기록이 생기면 종목별 레이팅을 표시합니다.")
                        .build())
                .otherSports(List.of(
                        MyPageDto.OtherSportView.builder()
                                .sportId(2L)
                                .name("풋살")
                                .ratingText("3.50")
                                .note("최근 3경기")
                                .build()))
                .sports(List.of(
                        MyPageDto.SportView.builder()
                                .sportId(1L)
                                .selected(true)
                                .name("농구")
                                .count(7)
                                .note("최근 3개월")
                                .grade("3.75")
                                .caption("평균 레이팅")
                                .hasAverage(true)
                                .meterPercent(75)
                                .build(),
                        MyPageDto.SportView.builder()
                                .sportId(2L)
                                .selected(false)
                                .name("풋살")
                                .count(3)
                                .note("최근 3개월")
                                .grade("3.50")
                                .caption("평균 레이팅")
                                .hasAverage(true)
                                .meterPercent(70)
                                .build()))
                .selectedSport("all")
                .historyTotalCount(10)
                .historyFilterLabel("전체 종목의 확정 매치")
                .historyCount(2)
                .history(List.of(
                        MyPageDto.HistoryItemView.builder()
                                .id(101L)
                                .date("2026.10.18 (일)")
                                .title("잠실 농구 3on3")
                                .sportName("농구")
                                .place("잠실체육관")
                                .applicationType("개인 신청")
                                .statusComplete(true)
                                .statusLabel("완료")
                                .ratingText("4.00")
                                .hasRating(true)
                                .ratingPercent(80)
                                .hasTeamAverages(true)
                                .myTeam("A")
                                .averageAText("3.80")
                                .averageBText("3.60")
                                .facts(List.of(
                                        MyPageDto.HistoryFactView.builder()
                                                .label("일시")
                                                .value("10.18 19:00")
                                                .build(),
                                        MyPageDto.HistoryFactView.builder()
                                                .label("팀")
                                                .value("A팀")
                                                .build(),
                                        MyPageDto.HistoryFactView.builder()
                                                .label("결과")
                                                .value("승")
                                                .build()))
                                .note("경기 종료 후 확정된 개인 레이팅입니다.")
                                .build(),
                        MyPageDto.HistoryItemView.builder()
                                .id(102L)
                                .date("2026.10.11 (일)")
                                .title("송파 풋살 5대5")
                                .sportName("풋살")
                                .place("송파구민운동장")
                                .applicationType("팀 신청")
                                .statusComplete(true)
                                .statusLabel("완료")
                                .ratingText("3.50")
                                .hasRating(true)
                                .ratingPercent(70)
                                .hasTeamAverages(false)
                                .facts(List.of(
                                        MyPageDto.HistoryFactView.builder()
                                                .label("일시")
                                                .value("10.11 18:00")
                                                .build(),
                                        MyPageDto.HistoryFactView.builder()
                                                .label("결과")
                                                .value("무")
                                                .build()))
                                .note("확정된 경기 기록을 기준으로 표시합니다.")
                                .build()))
                .historyEmptyNote("확정된 경기 이력이 없습니다.")
                .currentReservations(List.of(
                        MyPageDto.ReservationView.builder()
                                .applicationId(201L)
                                .sportName("농구")
                                .typeLabel("개인 신청")
                                .stateClass("text-book-ink")
                                .stateLabel("참가 중")
                                .title("잠실 농구 3on3")
                                .metaLine("10.25 (일) 19:00 · 잠실체육관")
                                .seatLine("참가 8/12명")
                                .statusNote("참가가 확정되었습니다.")
                                .canViewPaymentGuidance(true)
                                .paymentGuidanceUrl("/mypage#reservations")
                                .canCancel(true)
                                .cancelLabel("신청 취소")
                                .build()))
                .pastReservations(List.of(
                        MyPageDto.ReservationView.builder()
                                .applicationId(202L)
                                .sportName("풋살")
                                .typeLabel("팀 신청")
                                .stateClass("text-faint")
                                .stateLabel("이용 완료")
                                .title("송파 풋살 5대5")
                                .metaLine("10.11 (일) 18:00 · 송파구민운동장")
                                .seatLine("참가 10/10명")
                                .canViewPaymentGuidance(false)
                                .paymentGuidanceUrl("")
                                .canCancel(false)
                                .cancelLabel("신청 취소")
                                .build()))
                .notifications(List.of(
                        MyPageDto.NotificationView.builder()
                                .notificationId(301L)
                                .read(false)
                                .type("예약")
                                .title("참가가 확정되었습니다")
                                .body("잠실 농구 3on3 참가가 확정되었습니다.")
                                .time("10분 전")
                                .href("/mypage#reservations")
                                .build(),
                        MyPageDto.NotificationView.builder()
                                .notificationId(302L)
                                .read(true)
                                .type("매치")
                                .title("경기 기록이 등록되었습니다")
                                .body("송파 풋살 5대5 레이팅이 등록되었습니다.")
                                .time("1일 전")
                                .href("/mypage#match-records")
                                .build()))
                .build();
    }

    private List<RegionOptionDto> regionOptions() {
        return List.of(
                RegionOptionDto.builder().regionId(1L).name("서울 송파구").build(),
                RegionOptionDto.builder().regionId(2L).name("서울 강남구").build(),
                RegionOptionDto.builder().regionId(3L).name("경기 성남시").build());
    }
}
