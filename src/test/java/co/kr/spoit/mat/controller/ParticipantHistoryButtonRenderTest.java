package co.kr.spoit.mat.controller;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.match.controller.MatchOperationsController;
import co.kr.spoit.match.dto.RoomManagementDetail;
import co.kr.spoit.match.service.RoomManagementService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 운영 화면의 신청자·참가자 이름이 참가자 이력 모달 버튼으로 그려지는지 확인한다. */
@WebMvcTest(controllers = MatchOperationsController.class)
@Import(SecurityConfig.class)
class ParticipantHistoryButtonRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomManagementService roomManagementService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    void rendersHistoryButtonsExceptClubApplicationAndModalPlaceholder() throws Exception {
        when(roomManagementService.findDetail(7L, 500L)).thenReturn(new RoomManagementDetail(500L, "토요 풋살",
                "테스트 체육관", "풋살", "11/1(토) 10:00~12:00", "3 / 8명", "모집 중", "good",
                List.of(new RoomManagementDetail.Application("김철수", "개인 신청", "참가 확정",
                                "R-2026-0001", "2026-10-29 09:30", 21L),
                        new RoomManagementDetail.Application("주말 풋살 클럽", "동호회 신청", "참가 확정",
                                "R-2026-0002", "2026-10-29 09:40", null)),
                List.of(new RoomManagementDetail.Participant("이영희", "A팀", "참가 확정", 22L))));

        String html = mockMvc.perform(get("/admin/operations").param("matchId", "500").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("data-history-user-id=\"21\"", "aria-label=\"김철수 참가자 이력 보기\"",
                "data-history-user-id=\"22\"", "aria-label=\"이영희 참가자 이력 보기\"");
        // 동호회 신청 행은 동호회명만 보여 주고 버튼을 두지 않는다.
        assertThat(html).contains("<span class=\"cell-primary\">주말 풋살 클럽</span>");
        assertThat(html).doesNotContain("aria-label=\"주말 풋살 클럽 참가자 이력 보기\"");
        assertThat(html).contains("id=\"participant-history-modal\"",
                "data-history-url=\"/admin/operations/matches/500/participants\"",
                "/js/participant-history.js", "/css/participant-history.css");
    }
}
