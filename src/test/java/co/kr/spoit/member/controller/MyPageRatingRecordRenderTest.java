package co.kr.spoit.member.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.domain.MyRatingHistoryRow;
import co.kr.spoit.mat.domain.MyRatingRow;
import co.kr.spoit.mat.mapper.MyRatingRecordMapper;
import co.kr.spoit.mat.service.MatchResultPageService;
import co.kr.spoit.mat.service.MyRatingRecordService;
import co.kr.spoit.match.dto.MyReservations;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MyReservationService;
import co.kr.spoit.member.dto.SportOption;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberService;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 마이페이지 종목별 레이팅·경기 이력을 실제 템플릿과 화면 값 조립으로 그린다. 로그인 회원 ID는 7이다. */
@WebMvcTest(controllers = MyPageController.class)
@Import({SecurityConfig.class, MyRatingRecordService.class})
class MyPageRatingRecordRenderTest {

    private static final long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MyRatingRecordMapper recordMapper;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private MyReservationService reservationService;

    @MockitoBean
    private MatchApplicationService applicationService;

    @MockitoBean
    private MatchResultPageService resultPageService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @BeforeEach
    void setUp() {
        given(reservationService.find(USER_ID)).willReturn(MyReservations.empty());
        given(recordMapper.findRatings(USER_ID)).willReturn(List.of(
                rating(2L, "풋살", "1031.60", 12),
                rating(3L, "농구", "1000.00", 3)));
        given(recordMapper.countHistory(USER_ID, null)).willReturn(2);
        given(recordMapper.countHistory(USER_ID, 2L)).willReturn(2);
    }

    @Test
    @DisplayName("대표 종목을 정하지 않았으면 가장 많이 뛴 종목을 경기 기록 기준으로 보이고, 다른 종목 목록에서는 뺀다")
    void marksFallbackRepresentativeAndLeavesItOutOfOtherSports() throws Exception {
        mockMvc.perform(get("/mypage").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("대표 종목 레이팅 · 경기 기록 기준</span>"),
                        containsString("<strong class=\"is-tier-silver\">실버 4 · 63P</strong>"),
                        containsString(">풋살</a>"),
                        containsString("aria-label=\"농구 경기 기록 보기\""),
                        containsString("<span>농구</span><strong>배치 중 (3/5)</strong><small>경기 3회</small>"))))
                .andExpect(content().string(not(containsString("aria-label=\"풋살 경기 기록 보기\""))));

        // 프로필 영역은 아래 종목 카드와 같은 조회 결과를 쓴다.
        verify(recordMapper, times(1)).findRatings(USER_ID);
    }

    @Test
    @DisplayName("대표 종목이 배치 중이면 큰 글자는 중립색이고, 다른 종목 줄의 티어에는 티어 색 클래스가 붙는다")
    void tierColorClassFollowsTierAndSkipsPlacement() throws Exception {
        given(memberService.findRepresentativeSport(USER_ID)).willReturn(Optional.of(sportOption(3L, "농구")));

        mockMvc.perform(get("/mypage").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<strong>배치 중 (3/5)</strong>\n        <a class=\"mypage-skill-sport-link\""),
                        containsString("<span>풋살</span><strong class=\"is-tier-silver\">실버 4 · 63P</strong>"))));
    }

    @Test
    @DisplayName("반영된 경기가 없고 대표 종목도 없으면 기록 없음만 보이고 종목 링크는 그리지 않는다")
    void hidesSportLinkWithoutAnyRecord() throws Exception {
        given(recordMapper.findRatings(USER_ID)).willReturn(List.of());

        mockMvc.perform(get("/mypage").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<span class=\"mypage-summary-label\">대표 종목 레이팅</span>"),
                        containsString("<strong>기록 없음</strong>"))))
                .andExpect(content().string(not(containsString("mypage-skill-sport-link"))));
    }

    @Test
    @DisplayName("프로필 수정 화면의 종목 option은 그 종목 레이팅을 data-rating으로 싣고, 처음 미리보기는 저장된 대표 종목 레이팅이다")
    void profileOptionsCarrySportRatings() throws Exception {
        given(memberService.findSports()).willReturn(List.of(
                sportOption(2L, "풋살"), sportOption(3L, "농구"), sportOption(4L, "배구")));
        given(memberService.findRepresentativeSport(USER_ID)).willReturn(Optional.of(sportOption(2L, "풋살")));

        mockMvc.perform(get("/mypage/profile").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("value=\"2\" data-rating=\"실버 4 · 63P\""),
                        containsString("value=\"3\" data-rating=\"배치 중 (3/5)\""),
                        containsString("value=\"4\" data-rating=\"기록 없음\""),
                        containsString("id=\"representative-level-preview\">실버 4 · 63P</strong>"))));
    }

    @Test
    @DisplayName("종목 카드는 티어·경기 수를, 이력은 승패·포인트 변동·평가 상세를 보여 주고 MMR과 5점제 문구는 없다")
    void rendersRatingsAndHistory() throws Exception {
        given(recordMapper.findHistory(USER_ID, null, 20)).willReturn(List.of(
                history(31L, "주말 풋살", "동호회", "A", "1015.07", "16.00", "0.53", "7.05", "1.0", 12),
                history(30L, "평일 풋살", "개인", "B", "1031.60", "-16.00", "0.68", null, "0.0", 11)));

        mockMvc.perform(get("/mypage").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<small>경기 12회</small>"),
                        containsString("<strong class=\"is-tier-silver\">실버 4 · 63P</strong>\n                  <span class=\"mypage-rating-meter is-tier-silver\""),
                        // 배치 중 종목 카드는 티어 클래스 없이 중립색이다.
                        containsString("<strong>배치 중 (3/5)</strong>"),
                        containsString("aria-label=\"풋살 티어 진행도 63P\""),
                        containsString("배치 중 (3/5)"),
                        containsString("2경기"),
                        containsString("<p>전체 종목</p>"),
                        containsString(">승</span>"),
                        containsString(">패</span>"),
                        containsString("<span>팀 신청</span>"),
                        containsString("<span>개인 신청</span>"),
                        containsString("<small>레이팅</small><strong class=\"is-up\">+33P</strong>"),
                        containsString("<strong class=\"is-down\">-31P</strong>"),
                        containsString("<span class=\"mypage-rating-step is-tier-silver\">실버 4 · 30P</span><span class=\"mypage-rating-arrow\">→</span><span class=\"mypage-rating-step is-tier-silver\">실버 4 · 63P</span>"),
                        containsString("<span class=\"mypage-rating-step is-tier-silver\">실버 4 · 63P</span><span class=\"mypage-rating-arrow\">→</span><span class=\"mypage-rating-step is-tier-silver\">실버 4 · 32P</span>"),
                        containsString("A팀 평균 <b class=\"is-tier-silver\">실버 4</b>"),
                        containsString("B팀 평균 <b class=\"is-tier-bronze\">브론즈 1</b>"),
                        containsString("<dt>승패</dt><dd>+32P</dd>"),
                        containsString("<dt>평가</dt><dd>+1P</dd>"),
                        containsString("<dt>승패</dt><dd>-32P</dd>"),
                        containsString("7.05점"),
                        containsString("<dd>없음</dd>"),
                        containsString("내 팀"))))
                // 배치 중 종목에는 티어 진행도 막대를 그리지 않는다.
                .andExpect(content().string(not(containsString("농구 티어 진행도"))))
                .andExpect(content().string(not(containsString("MMR"))))
                .andExpect(content().string(not(containsString("승급"))))
                .andExpect(content().string(not(containsString("/ 5.00"))))
                .andExpect(content().string(not(containsString("5점 만점"))));
    }

    @Test
    @DisplayName("배치 경기 이력은 티어·포인트 대신 배치 진행만, 상세에는 평가 점수만 보이고 승급·강등은 표시한다")
    void rendersPlacementAndDivisionChange() throws Exception {
        given(recordMapper.findHistory(USER_ID, null, 20)).willReturn(List.of(
                history(33L, "승급 경기", "개인", "A", "999.00", "16.00", "0.50", "5.50", "1.0", 7),
                history(32L, "배치 완료 경기", "개인", "A", "1000.00", "15.50", "0.00", "5.00", "1.0", 5),
                history(31L, "배치 경기", "개인", "B", "1000.00", "-31.09", "-0.66", "4.34", "0.0", 1)));

        mockMvc.perform(get("/mypage").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<span class=\"mypage-rating-step is-tier-bronze\">브론즈 1 · 98P</span><span class=\"mypage-rating-arrow\">→</span><span class=\"mypage-rating-step is-tier-silver\">실버 4 · 31P</span><em class=\"mypage-match-division-change is-up\">승급</em>"),
                        containsString("<strong class=\"is-placement\">배치 완료</strong>"),
                        containsString("<span class=\"mypage-rating-step\">배치 완료</span><span class=\"mypage-rating-arrow\">→</span><span class=\"mypage-rating-step is-tier-silver\">실버 4 · 31P</span>"),
                        containsString("<strong class=\"is-placement\">배치 1/5</strong>"),
                        containsString("<span class=\"mypage-rating-step\">배치 경기 1/5</span></strong>"),
                        containsString("4.34점"))))
                // 배치 경기 두 줄에는 승패·평가 포인트가 없어 승급 경기 한 줄만 남는다.
                .andExpect(result -> assertThat(result.getResponse().getContentAsString().split("<dt>승패</dt>", -1)).hasSize(2))
                .andExpect(content().string(not(containsString("MMR"))));
    }

    @Test
    @DisplayName("내 레이팅 종목을 고르면 그 종목 이력만 조회한다")
    void filtersHistoryBySelectedSport() throws Exception {
        mockMvc.perform(get("/mypage").param("recordSport", "2").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<p>풋살</p>")));

        verify(recordMapper).findHistory(USER_ID, 2L, 20);
    }

    @Test
    @DisplayName("숫자가 아니거나 내 레이팅이 없는 종목 값이면 전체 종목으로 본다")
    void invalidSportFilterFallsBackToAll() throws Exception {
        mockMvc.perform(get("/mypage").param("recordSport", "abc").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<p>전체 종목</p>")));
        mockMvc.perform(get("/mypage").param("recordSport", "99").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<p>전체 종목</p>")));

        verify(recordMapper, times(2)).findHistory(eq(USER_ID), isNull(), eq(20));
    }

    private static SportOption sportOption(Long sportId, String name) {
        var sport = new SportOption();
        sport.setSportId(sportId);
        sport.setName(name);
        return sport;
    }

    private static MyRatingRow rating(Long sportId, String sportName, String mmr, int ratedMatchCount) {
        var row = new MyRatingRow();
        row.setSportId(sportId);
        row.setSportName(sportName);
        row.setCurrentMmr(new BigDecimal(mmr));
        row.setRatedMatchCount(ratedMatchCount);
        return row;
    }

    private static MyRatingHistoryRow history(Long historyId, String title, String applicationType, String side,
                                              String mmrBefore, String eloDelta, String evaluationDelta,
                                              String evaluationScore, String actualScore, int ratedMatchCountAfter) {
        BigDecimal before = new BigDecimal(mmrBefore);
        BigDecimal elo = new BigDecimal(eloDelta);
        BigDecimal evaluation = new BigDecimal(evaluationDelta);
        var row = new MyRatingHistoryRow();
        row.setHistoryId(historyId);
        row.setSportId(2L);
        row.setTitle(title);
        row.setStartAt(LocalDateTime.of(2026, 10, 3, 10, 0));
        row.setSportName("풋살");
        row.setFacilityName("테스트 체육관");
        row.setApplicationType(applicationType);
        row.setMatchSide(side);
        row.setMmrBefore(before);
        row.setMmrAfter(before.add(elo).add(evaluation));
        row.setEloDelta(elo);
        row.setEvaluationDelta(evaluation);
        row.setFinalDelta(elo.add(evaluation));
        row.setEvaluationScore(evaluationScore == null ? null : new BigDecimal(evaluationScore));
        row.setActualScore(new BigDecimal(actualScore));
        row.setTeamAvgMmrA(new BigDecimal("1010.25"));
        row.setTeamAvgMmrB(new BigDecimal("990.75"));
        row.setCalculatedAt(LocalDateTime.of(2026, 10, 4, 12, 0));
        row.setRatedMatchCountAfter(ratedMatchCountAfter);
        return row;
    }
}
