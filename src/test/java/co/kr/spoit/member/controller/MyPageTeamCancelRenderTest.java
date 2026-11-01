package co.kr.spoit.member.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.service.SportRatingService;
import co.kr.spoit.match.dto.MyReservations;
import co.kr.spoit.match.dto.MyReservations.Entry;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MyReservationService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberService;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** RES-04 팀 신청 대표의 취소 폼에만 팀 전체 취소 확인창 데이터가 붙는지 실제 템플릿으로 검증한다. */
@WebMvcTest(controllers = MyPageController.class)
@Import(SecurityConfig.class)
class MyPageTeamCancelRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private SportRatingService sportRatingService;

    @MockitoBean
    private MyReservationService reservationService;

    @MockitoBean
    private MatchApplicationService applicationService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("팀 신청 대표의 취소 폼은 확인창 문구를 갖고, 개인 신청 폼은 확인창 없이 바로 제출된다")
    void teamCancelFormCarriesConfirmData() throws Exception {
        Entry team = new Entry(10L, 3L, "배드민턴", "팀 신청", "확정 대기", "text-book-ink", "토요일 저녁 복식 매치",
                "내일 19:00~21:00 · 강남 스포츠센터", "참가 3명", null, true, "신청 취소", false, null,
                true, "팀원 3명(김채원, 박민수, 이서연)의 신청이 모두 함께 취소됩니다.");
        Entry individual = new Entry(20L, 4L, "풋살", "개인 신청", "확정 대기", "text-book-ink", "풋살 매치",
                "내일 20:00~22:00 · 시설", "참가 1명", null, true, "신청 취소", false, null, false, null);
        given(reservationService.find(7L)).willReturn(new MyReservations(List.of(team, individual), List.of(), 0, 0, 2));

        mockMvc.perform(get("/mypage").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("id=\"team-cancel-dialog\""),
                        containsString("data-team-cancel=\"팀원 3명(김채원, 박민수, 이서연)의 신청이 모두 함께 취소됩니다.\""),
                        containsString("data-team-cancel-title=\"토요일 저녁 복식 매치\""),
                        containsString("action=\"/mypage/applications/10/cancel\""),
                        containsString("action=\"/mypage/applications/20/cancel\""))))
                // 개인 신청 폼에는 확인창 속성이 붙지 않는다 (제목은 확인창 속성으로만 나타난다).
                .andExpect(content().string(not(containsString("data-team-cancel-title=\"풋살 매치\""))));
    }
}
