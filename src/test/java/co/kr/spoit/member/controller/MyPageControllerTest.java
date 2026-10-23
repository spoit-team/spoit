package co.kr.spoit.member.controller;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
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

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.dto.SignupRegion;
import co.kr.spoit.member.service.MemberService;

/** 마이페이지 화면이 세션 주체의 정보만 노출하는지 검증한다. DB 없이 standalone MockMvc를 쓴다. */
class MyPageControllerTest {

    private static final long USER_ID = 7L;
    private static final String EMAIL = "player@example.com";
    private static final String NICKNAME = "플레이어";
    private static final String PHONE = "010-1234-5678";

    private MemberService memberService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MyPageController(memberService)).build();
    }

    @Test
    @DisplayName("로그인 회원은 마이페이지에서 본인 닉네임과 이메일을 본다")
    void myPageShowsOwnIdentity() throws Exception {
        given(memberService.findRegions(USER_ID)).willReturn(List.of());

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

        mockMvc.perform(get("/mypage").with(principal()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("member", hasEntry("regionText", expectedText)));
    }

    @Test
    @DisplayName("로그인 회원은 프로필 수정 화면에서 본인 정보를 본다")
    void profileShowsOwnIdentity() throws Exception {
        given(memberService.findRegions(USER_ID))
                .willReturn(List.of(region(11L, "강남구"), region(12L, "송파구")));
        given(memberService.findSignupRegions()).willReturn(List.of(region(11L, "강남구")));

        mockMvc.perform(get("/mypage/profile").with(principal()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("profile", hasEntry("nickname", NICKNAME)))
                .andExpect(model().attribute("profile", hasEntry("email", EMAIL)))
                .andExpect(model().attribute("profile", hasEntry("phone", PHONE)))
                .andExpect(model().attribute("profile", hasEntry("regionId", 11L)));
    }

    @Test
    @DisplayName("프로필 화면은 저장된 지역을 선택할 수 있도록 자치구 선택지를 채운다")
    void profileFillsRegionOptions() throws Exception {
        given(memberService.findRegions(USER_ID)).willReturn(List.of(region(11L, "강남구")));
        given(memberService.findSignupRegions()).willReturn(List.of(region(11L, "강남구"), region(12L, "송파구")));

        mockMvc.perform(get("/mypage/profile").with(principal()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("districts", hasSize(2)));
    }

    @Test
    @DisplayName("비로그인 사용자는 마이페이지와 프로필 화면을 빈 값으로 본다")
    void anonymousSeesEmptyValues() throws Exception {
        given(memberService.findSignupRegions()).willReturn(List.of());

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
        member.setStatus("활성");
        return member;
    }

    private static SignupRegion region(Long regionId, String name) {
        SignupRegion region = new SignupRegion();
        region.setRegionId(regionId);
        region.setName(name);
        return region;
    }
}
