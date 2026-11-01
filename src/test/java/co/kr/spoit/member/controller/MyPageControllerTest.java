package co.kr.spoit.member.controller;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.kr.spoit.mat.dto.SportRatingResponse;
import co.kr.spoit.mat.service.SportRatingService;
import co.kr.spoit.match.dto.MyReservations;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MatchApplyException;
import co.kr.spoit.match.service.MyReservationService;
import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.dto.SignupRegion;
import co.kr.spoit.member.dto.SportOption;
import co.kr.spoit.member.service.MemberService;

/** 마이페이지 화면이 세션 주체의 정보만 노출하는지 검증한다. DB 없이 standalone MockMvc를 쓴다. */
class MyPageControllerTest {

    private static final long USER_ID = 7L;
    private static final String EMAIL = "player@example.com";
    private static final String NICKNAME = "플레이어";
    private static final String PHONE = "010-1234-5678";
    private static final String RAW_PASSWORD = "spoit1234!";

    private MemberService memberService;
    private SportRatingService sportRatingService;
    private MyReservationService reservationService;
    private MatchApplicationService applicationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        sportRatingService = mock(SportRatingService.class);
        reservationService = mock(MyReservationService.class);
        given(reservationService.find(anyLong())).willReturn(MyReservations.empty());
        applicationService = mock(MatchApplicationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MyPageController(
                memberService, sportRatingService, reservationService, applicationService)).build();
    }

    @Test
    @DisplayName("로그인 회원은 마이페이지에서 본인 닉네임과 이메일을 본다")
    void myPageShowsOwnIdentity() throws Exception {
        given(memberService.findRegions(USER_ID)).willReturn(List.of());
        given(memberService.findRepresentativeSport(USER_ID)).willReturn(Optional.empty());

        mockMvc.perform(get("/mypage").with(principal()))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/index"))
                .andExpect(model().attribute("member", hasEntry("nickname", NICKNAME)))
                .andExpect(model().attribute("member", hasEntry("email", EMAIL)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("선호 지역이 있으면 이름을 이어 붙이고, 없으면 안내 문구를 보여준다")
    @MethodSource("regionCases")
    void regionTextFollowsStoredRegions(List<SignupRegion> regions, String expectedText)
            throws Exception {
        given(memberService.findRegions(USER_ID)).willReturn(regions);
        given(memberService.findRepresentativeSport(USER_ID)).willReturn(Optional.empty());

        mockMvc.perform(get("/mypage").with(principal()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("member", hasEntry("regionText", expectedText)));
    }

    @Test
    @DisplayName("마이페이지 요약은 설정한 대표 종목과 레이팅을 보여준다")
    void summaryShowsRepresentativeSportAndRating() throws Exception {
        given(memberService.findRegions(USER_ID)).willReturn(List.of());
        given(memberService.findRepresentativeSport(USER_ID)).willReturn(Optional.of(sportOption(2L, "농구")));
        given(sportRatingService.findByUserIdAndSportId(USER_ID, 2L)).willReturn(
                Optional.of(new SportRatingResponse(USER_ID, 2L, new BigDecimal("1015.66"), 4, null)));

        mockMvc.perform(get("/mypage").with(principal()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("summary", hasEntry("representativeSportName", "농구")))
                .andExpect(model().attribute("summary", hasEntry("representativeRatingText", "1015.66")));
    }

    @Test
    @DisplayName("신청 내역과 요약 집계는 본인 userId로 조회한 결과를 쓴다")
    void reservationsComeFromOwnApplications() throws Exception {
        given(memberService.findRegions(USER_ID)).willReturn(List.of());
        given(memberService.findRepresentativeSport(USER_ID)).willReturn(Optional.empty());
        MyReservations.Entry entry = new MyReservations.Entry(1L, 3L, "풋살", "개인 신청", "확정 대기",
                "text-book-ink", "매칭방", "내일 19:00~21:00 · 시설", "참가 1명", null, false, "신청 취소", false, null, false, null);
        given(reservationService.find(USER_ID)).willReturn(new MyReservations(List.of(entry), List.of(), 2, 1, 1));

        mockMvc.perform(get("/mypage").with(principal()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentReservations", hasSize(1)))
                .andExpect(model().attribute("pastReservations", hasSize(0)))
                .andExpect(model().attribute("summary", hasEntry("confirmedCount", 2)))
                .andExpect(model().attribute("summary", hasEntry("completedCount", 1)))
                .andExpect(model().attribute("summary", hasEntry("waitingCount", 1)));
        verify(reservationService).find(USER_ID);
    }

    @Test
    @DisplayName("신청 취소는 본인 userId로 처리하고 결과를 마이페이지 안내로 돌려준다")    void cancelApplicationRedirectsWithMessage() throws Exception {
        mockMvc.perform(post("/mypage/applications/11/cancel").with(principal()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage?message="
                        + URLEncoder.encode("신청을 취소했습니다.", StandardCharsets.UTF_8) + "#reservations"));
        verify(applicationService).cancel(11L, USER_ID);
    }

    @Test
    @DisplayName("취소할 수 없는 신청이면 사유를 안내한다")
    void cancelApplicationShowsReason() throws Exception {
        willThrow(new MatchApplyException("이미 취소되었거나 처리된 신청입니다."))
                .given(applicationService).cancel(11L, USER_ID);

        mockMvc.perform(post("/mypage/applications/11/cancel").with(principal()))
                .andExpect(redirectedUrl("/mypage?message="
                        + URLEncoder.encode("이미 취소되었거나 처리된 신청입니다.", StandardCharsets.UTF_8)
                        + "#reservations"));
    }

    @Test
    @DisplayName("비로그인 취소 요청은 로그인으로 보낸다")
    void cancelApplicationRequiresLogin() throws Exception {
        mockMvc.perform(post("/mypage/applications/11/cancel"))
                .andExpect(redirectedUrl("/login"));
        verify(applicationService, never()).cancel(anyLong(), anyLong());
    }

    @Test
    @DisplayName("레이팅이 없거나 확정 경기가 0건이거나 MMR이 null이면 기록 없음으로 표시한다")
    void summaryShowsNoRatingTextWhenRatingMissing() throws Exception {
        given(memberService.findRegions(USER_ID)).willReturn(List.of());
        given(memberService.findRepresentativeSport(USER_ID)).willReturn(Optional.of(sportOption(2L, "농구")));
        given(sportRatingService.findByUserIdAndSportId(USER_ID, 2L))
                .willReturn(Optional.empty(),
                        Optional.of(new SportRatingResponse(USER_ID, 2L, new BigDecimal("1015.66"), 0, null)),
                        Optional.of(new SportRatingResponse(USER_ID, 2L, null, 4, null)));

        mockMvc.perform(get("/mypage").with(principal()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("summary", hasEntry("representativeRatingText", "기록 없음")));
        mockMvc.perform(get("/mypage").with(principal()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("summary", hasEntry("representativeRatingText", "기록 없음")));
        mockMvc.perform(get("/mypage").with(principal()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("summary", hasEntry("representativeRatingText", "기록 없음")));
    }

    @Test
    @DisplayName("프로필 화면의 오류·안내 파라미터는 빈 값을 null로 바꿔 모델에 담는다")
    void profileRendersErrorAndMessageQueryParams() throws Exception {
        given(memberService.findRegions(USER_ID)).willReturn(List.of());
        given(memberService.findSports()).willReturn(List.of());
        given(memberService.findSignupRegions()).willReturn(List.of());

        mockMvc.perform(get("/mypage/profile").with(principal()).param("error", " ").param("message", "저장"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("error", nullValue()))
                .andExpect(model().attribute("message", "저장"));
    }

    @Test
    @DisplayName("로그인 회원은 프로필 수정 화면에서 본인 정보와 지역 선택지를 본다")
    void profileShowsOwnIdentity() throws Exception {
        given(memberService.findRegions(USER_ID))
                .willReturn(List.of(region(11L, "강남구"), region(12L, "송파구")));
        given(memberService.findSignupRegions())
                .willReturn(List.of(region(11L, "강남구"), region(12L, "송파구")));
        given(memberService.findSports()).willReturn(List.of(sportOption(2L, "농구")));
        given(memberService.findRepresentativeSport(USER_ID)).willReturn(Optional.of(sportOption(2L, "농구")));

        mockMvc.perform(get("/mypage/profile").with(principal()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("profile", hasEntry("nickname", NICKNAME)))
                .andExpect(model().attribute("profile", hasEntry("email", EMAIL)))
                .andExpect(model().attribute("profile", hasEntry("phone", PHONE)))
                .andExpect(model().attribute("profile", hasEntry("regionId", 11L)))
                .andExpect(model().attribute("profile", hasEntry("representativeSportId", 2L)))
                .andExpect(model().attribute("districts", hasSize(2)));
    }

    @Test
    @DisplayName("프로필 저장은 폼 값을 서비스에 전달하고 프로필 화면으로 리다이렉트한다")
    void saveProfileDelegatesToServiceAndRedirects() throws Exception {
        given(memberService.findSports()).willReturn(List.of(sportOption(5L, "테니스")));

        mockMvc.perform(post("/mypage/profile").with(principal())
                        .param("nickname", NICKNAME)
                        .param("phone", PHONE)
                        .param("representativeSportId", "5")
                        .param("regionId", "11"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/profile?message=%ED%94%84%EB%A1%9C%ED%95%84%EC%9D%84+%EC%A0%80%EC%9E%A5%ED%96%88%EC%8A%B5%EB%8B%88%EB%8B%A4."));

        verify(memberService).updateProfile(eq(USER_ID), argThat(form ->
                form.getRepresentativeSportId() == 5L && NICKNAME.equals(form.getNickname())));
    }

    @Test
    @DisplayName("닉네임이 형식에 맞지 않으면 저장하지 않고 오류와 함께 다시 그린다")
    void invalidNicknameRendersProfileWithError() throws Exception {
        // 서비스 계약: 닉네임 형식 오류 시 IllegalArgumentException으로 저장을 거부한다.
        given(memberService.findSports()).willReturn(List.of(sportOption(2L, "농구")));
        given(memberService.findSignupRegions()).willReturn(List.of(region(11L, "강남구")));
        willThrow(new IllegalArgumentException("닉네임은 한글, 영문, 숫자, 밑줄로 2~12자 입력해 주세요."))
                .given(memberService).updateProfile(eq(USER_ID), argThat(form -> true));

        mockMvc.perform(post("/mypage/profile").with(principal())
                        .param("nickname", "!")
                        .param("phone", PHONE))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/profile"))
                .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("비로그인으로 프로필을 저장하면 로그인 화면으로 보낸다")
    void anonymousSaveRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/mypage/profile")
                        .param("nickname", NICKNAME)
                        .param("phone", PHONE))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @DisplayName("비밀번호가 맞으면 탈퇴를 위임하고 로그인 화면으로 보낸다")
    void withdrawalDelegatesAndRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/mypage/withdrawal-requests").with(principal())
                        .param("password", RAW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?message=" + URLEncoder.encode("탈퇴가 완료되었습니다.", StandardCharsets.UTF_8)));

        verify(memberService).withdraw(USER_ID, RAW_PASSWORD);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 탈퇴하지 않고 프로필 화면에 오류를 전달한다")
    void withdrawalWithWrongPasswordShowsError() throws Exception {
        willThrow(new IllegalArgumentException("비밀번호가 일치하지 않습니다."))
                .given(memberService).withdraw(USER_ID, "wrong-password");

        mockMvc.perform(post("/mypage/withdrawal-requests").with(principal())
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage/profile?error="
                        + URLEncoder.encode("비밀번호가 일치하지 않습니다.", StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("비로그인 탈퇴 요청은 서비스를 호출하지 않고 로그인 화면으로 보낸다")
    void anonymousWithdrawalRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/mypage/withdrawal-requests")
                        .param("password", RAW_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(memberService, never()).withdraw(anyLong(), anyString());
    }

    @Test
    @DisplayName("비로그인 사용자는 마이페이지와 프로필 화면을 빈 값으로 본다")
    void anonymousSeesEmptyValues() throws Exception {
        given(memberService.findSignupRegions()).willReturn(List.of());
        given(memberService.findSports()).willReturn(List.of());

        mockMvc.perform(get("/mypage"))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/index"))
                .andExpect(model().attribute("member", hasEntry("nickname", "")));

        mockMvc.perform(get("/mypage/profile"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("profile", nullValue()));
    }

    private static Stream<Arguments> regionCases() {
        return Stream.of(
                Arguments.of(List.of(region(11L, "강남구"), region(12L, "송파구")), "강남구 · 송파구"),
                Arguments.of(List.of(), MyPageController.NO_REGION_TEXT));
    }

    private static RequestPostProcessor principal() {
        Authentication authentication = loginAuthentication();
        return request -> {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setUserPrincipal(authentication);
            return request;
        };
    }

    private static Authentication loginAuthentication() {
        MemberPrincipal principal = MemberPrincipal.from(member());
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private static Member member() {
        Member member = new Member();
        member.setUserId(USER_ID);
        member.setEmail(EMAIL);
        member.setNickname(NICKNAME);
        member.setPhone(PHONE);
        member.setRole("일반 회원");
        return member;
    }

    private static SignupRegion region(Long regionId, String name) {
        SignupRegion region = new SignupRegion();
        region.setRegionId(regionId);
        region.setName(name);
        return region;
    }

    private static SportOption sportOption(Long sportId, String name) {
        SportOption sport = new SportOption();
        sport.setSportId(sportId);
        sport.setName(name);
        return sport;
    }
}
