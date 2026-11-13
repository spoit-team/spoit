package co.kr.spoit.member.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.mat.mapper.MyRatingRecordMapper;
import co.kr.spoit.mat.service.MatchResultPageService;
import co.kr.spoit.mat.service.MyRatingRecordService;
import co.kr.spoit.match.dto.MyReservations;
import co.kr.spoit.match.dto.MyReservations.Entry;
import co.kr.spoit.match.service.MatchApplicationService;
import co.kr.spoit.match.service.MyReservationService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberService;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 지난 참여·취소 목록은 서버가 전부 그리고, 5건을 넘을 때만 스크립트가 쓸 접기 버튼을 숨긴 채 둔다. */
@WebMvcTest(controllers = MyPageController.class)
@Import({SecurityConfig.class, MyRatingRecordService.class})
class MyPagePastReservationsRenderTest {

    private static final Pattern TOGGLE = Pattern.compile("<button[^>]*data-past-toggle[^>]*>([^<]*)</button>");

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

    @Test
    @DisplayName("지난 내역이 5건을 넘으면 전체 목록과 함께 숨긴 펼치기 버튼을 그리고, 버튼 문구에 실제 전체 건수를 쓴다")
    void rendersHiddenToggleWithTotalCount() throws Exception {
        String html = render(7);

        assertThat(pastTitles(html)).isEqualTo(7);
        Matcher toggle = TOGGLE.matcher(html);
        assertThat(toggle.find()).isTrue();
        assertThat(toggle.group()).contains("aria-controls=\"past-reservation-list\"", "aria-expanded=\"true\"")
                .containsPattern("\\shidden[\\s>=]");
        assertThat(toggle.group(1)).isEqualTo("지난 참여 전체 보기 (7건)");
        assertThat(html).contains("id=\"past-reservation-list\"");
    }

    @Test
    @DisplayName("지난 내역이 5건 이하면 펼치기 버튼을 그리지 않는다")
    void omitsToggleUpToFiveEntries() throws Exception {
        String html = render(5);

        assertThat(pastTitles(html)).isEqualTo(5);
        assertThat(html).doesNotContain("data-past-toggle");
    }

    private String render(int pastCount) throws Exception {
        List<Entry> past = IntStream.rangeClosed(1, pastCount).mapToObj(i -> new Entry((long) i, 100L + i, "풋살",
                "개인 신청", "이용 완료", "text-ink-2", "지난 경기 " + i, "2026.10.0" + (i % 9) + " · 시설", "참가 1명", null,
                false, "신청 취소", false, null, false, List.of(), null, 0L)).toList();
        given(reservationService.find(7L)).willReturn(new MyReservations(List.of(), past, 0, pastCount, 0));
        return mockMvc.perform(get("/mypage").with(user(member())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    // 지난 내역 제목(">지난 경기 n<")으로 서버가 그린 항목 수를 센다.
    private static int pastTitles(String html) {
        return html.split(Pattern.quote(">지난 경기 "), -1).length - 1;
    }
}
