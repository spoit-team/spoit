package co.kr.spoit.member.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
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
import co.kr.spoit.mat.service.SportRatingService;
import co.kr.spoit.match.dto.MyReservations;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MyReservationService;
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
    private SportRatingService sportRatingService;

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
    @DisplayName("종목 카드는 티어·MMR·경기 수를, 이력은 승패·MMR 변동·평가 상세를 보여 주고 5점제 문구는 없다")
    void rendersRatingsAndHistory() throws Exception {
        given(recordMapper.findHistory(USER_ID, null, 20)).willReturn(List.of(
                history(31L, "주말 풋살", "동호회", "A", "1015.07", "16.00", "0.53", "7.05", "1.0"),
                history(30L, "평일 풋살", "개인", "B", "1031.60", "-16.00", "0.68", null, "0.0")));

        mockMvc.perform(get("/mypage").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("경기 12회 · MMR 1,032"),
                        containsString("실버 4 · 63P"),
                        containsString("aria-label=\"풋살 티어 진행도 63P\""),
                        containsString("배치 중 (3/5)"),
                        containsString("2경기"),
                        containsString("<p>전체 종목</p>"),
                        containsString(">승</span>"),
                        containsString(">패</span>"),
                        containsString("<span>팀 신청</span>"),
                        containsString("<span>개인 신청</span>"),
                        containsString(">+16.53</strong>"),
                        containsString(">-15.32</strong>"),
                        containsString("1,015 → 1,032"),
                        containsString("7.05점"),
                        containsString("<dd>없음</dd>"),
                        containsString("내 팀"))))
                // 배치 중 종목에는 티어 진행도 막대를 그리지 않는다.
                .andExpect(content().string(not(containsString("농구 티어 진행도"))))
                .andExpect(content().string(not(containsString("/ 5.00"))))
                .andExpect(content().string(not(containsString("5점 만점"))));
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
                                              String evaluationScore, String actualScore) {
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
        return row;
    }
}
