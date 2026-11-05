package co.kr.spoit.web;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.controller.MatchResultPageController;
import co.kr.spoit.mat.domain.ResultPageMatch;
import co.kr.spoit.mat.domain.ResultTeamMember;
import co.kr.spoit.mat.mapper.MatchResultMapper;
import co.kr.spoit.mat.service.MatchResultPageService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 결과 제출 화면이 팀장에게만 경기 정보·명단과 제출 영역을 그리는지 검증한다. 로그인 회원 ID는 7이다. */
@WebMvcTest(controllers = MatchResultPageController.class)
@Import({SecurityConfig.class, MatchResultPageService.class})
class MatchResultPageRouteTest {

    private static final String PATH = "/mypage/matches/10/result-form";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchResultMapper resultMapper;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("로그인하지 않으면 로그인 화면으로 보낸다")
    void anonymousRedirectsToLogin() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        verifyNoInteractions(resultMapper);
    }

    @Test
    @DisplayName("팀장에게는 경기 정보, 양 팀 명단, 결과 API 주소와 CSRF 헤더를 그린다")
    void rendersMatchAndResultAreaForCaptain() throws Exception {
        givenMatch();
        givenMembers(teamMember(1L, 7L, "박민서", "A"), teamMember(3L, 13L, "김지우", "A"),
                teamMember(2L, 12L, "정하늘", "B"));

        mockMvc.perform(get(PATH).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/match-result"))
                .andExpect(content().string(containsString("토요 풋살")))
                .andExpect(content().string(containsString("풋살")))
                .andExpect(content().string(containsString("테스트 체육관")))
                .andExpect(content().string(containsString("2026년 11월 3일 (화) 10:00 ~ 12:00")))
                .andExpect(content().string(containsString("2026년 11월 4일 (수) 12:00")))
                .andExpect(content().string(containsString("박민서")))
                .andExpect(content().string(containsString("김지우")))
                .andExpect(content().string(containsString("정하늘")))
                .andExpect(content().string(containsString("class=\"match-result-captain\">팀장</b>")))
                .andExpect(content().string(containsString("data-result-url=\"/mypage/matches/10/result\"")))
                .andExpect(content().string(containsString("data-my-side=\"A\"")))
                .andExpect(content().string(containsString("data-csrf-header=\"X-CSRF-TOKEN\"")))
                .andExpect(content().string(containsString("/js/match-result.js")))
                .andExpect(content().string(not(containsString("이 경기의 팀장만 결과를 제출할 수 있습니다"))));
    }

    @Test
    @DisplayName("팀장에게는 결과 섹션 아래에 평가 섹션 자리와 평가 API 주소를 그린다")
    void rendersEvaluationAreaBelowResultForCaptain() throws Exception {
        givenMatch();
        givenMembers(teamMember(1L, 7L, "박민서", "A"), teamMember(2L, 12L, "정하늘", "B"));

        mockMvc.perform(get(PATH).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(stringContainsInOrder("id=\"match-result\"", "id=\"match-evaluation\"")))
                .andExpect(content().string(containsString("data-evaluation-url=\"/mypage/matches/10/evaluation\"")))
                .andExpect(content().string(containsString("선수 평가")))
                .andExpect(content().string(containsString("/js/match-evaluation.js")));
    }

    @Test
    @DisplayName("팀장이 아니면 경기 정보와 명단 없이 안내만 보여 준다")
    void showsOnlyNoticeForNonCaptain() throws Exception {
        givenMatch();
        // 회원 7은 확정 참가자지만 팀장 자리(참가자 1·2)가 아니다.
        givenMembers(teamMember(1L, 11L, "박민서", "A"), teamMember(2L, 12L, "정하늘", "B"),
                teamMember(3L, 7L, "김지우", "A"));

        mockMvc.perform(get(PATH).with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이 경기의 팀장만 결과를 제출할 수 있습니다")))
                .andExpect(content().string(not(containsString("토요 풋살"))))
                .andExpect(content().string(not(containsString("박민서"))))
                .andExpect(content().string(not(containsString("data-result-url"))))
                .andExpect(content().string(not(containsString("/js/match-result.js"))));
    }

    @Test
    @DisplayName("경기가 없으면 404로 안내한다")
    void rendersNotFoundForMissingMatch() throws Exception {
        when(resultMapper.findResultPageMatch(10L)).thenReturn(Optional.empty());

        mockMvc.perform(get(PATH).with(user(member())))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("경기 정보를 찾을 수 없습니다.")))
                .andExpect(content().string(not(containsString("data-result-url"))));
    }

    /** A팀장은 참가자 1, B팀장은 참가자 2 */
    private void givenMatch() {
        var match = new ResultPageMatch();
        match.setMatchId(10L);
        match.setTitle("토요 풋살");
        match.setStartAt(LocalDateTime.of(2026, 11, 3, 10, 0));
        match.setEndAt(LocalDateTime.of(2026, 11, 3, 12, 0));
        match.setStatus("매칭 완료");
        match.setFacilityName("테스트 체육관");
        match.setSportName("풋살");
        match.setCaptainAParticipantId(1L);
        match.setCaptainBParticipantId(2L);
        when(resultMapper.findResultPageMatch(10L)).thenReturn(Optional.of(match));
    }

    private void givenMembers(ResultTeamMember... members) {
        when(resultMapper.findConfirmedTeamMembers(10L, "확정")).thenReturn(List.of(members));
    }

    private static ResultTeamMember teamMember(Long participantId, Long userId, String nickname, String side) {
        var member = new ResultTeamMember();
        member.setParticipantId(participantId);
        member.setUserId(userId);
        member.setNickname(nickname);
        member.setMatchSide(side);
        return member;
    }
}
