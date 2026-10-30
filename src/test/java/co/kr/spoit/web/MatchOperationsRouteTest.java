package co.kr.spoit.web;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.match.controller.MatchOperationsController;
import co.kr.spoit.match.dto.RoomManagementDetail;
import co.kr.spoit.match.service.RoomManagementService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 매치 운영 상세가 인증 주체의 담당 시설 범위로만 조회되고, 없으면 안전하게 빈 상태가 되는지 검증한다. */
@WebMvcTest(controllers = MatchOperationsController.class)
@Import(SecurityConfig.class)
class MatchOperationsRouteTest {

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
    @DisplayName("상세는 클라이언트 값이 아니라 인증 주체 ID로 조회해 화면에 전달한다")
    void rendersDetailLookedUpByPrincipalUserId() throws Exception {
        when(roomManagementService.findDetail(7L, 500L)).thenReturn(detail(500L, "토요 풋살"));

        mockMvc.perform(get("/admin/operations").param("matchId", "500").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/operations"))
                .andExpect(model().attribute("match",
                        org.hamcrest.Matchers.hasProperty("matchId", org.hamcrest.Matchers.is(500L))))
                .andExpect(content().string(containsString("토요 풋살")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("매칭방을 찾을 수 없습니다."))));

        verify(roomManagementService).findDetail(7L, 500L);
    }

    @Test
    @DisplayName("상세는 실제 신청·참가자 명단과 상태를 렌더링하고 변경 폼은 두지 않는다")
    void rendersApplicationsAndParticipantsWithoutWriteForms() throws Exception {
        RoomManagementDetail detail = new RoomManagementDetail(500L, "토요 풋살",
                "테스트 체육관", "풋살", "11/1(토) 10:00~12:00", "3 / 8명", "모집 중", "good",
                List.of(new RoomManagementDetail.Application("김철수", "개인 신청", "참가 확정",
                        "R-2026-0001", "2026-10-29 09:30")),
                List.of(new RoomManagementDetail.Participant("이영희", "A팀", "참가 확정")));
        when(roomManagementService.findDetail(7L, 500L)).thenReturn(detail);

        String html = mockMvc.perform(
                        get("/admin/operations").param("matchId", "500").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("김철수")))
                .andExpect(content().string(containsString("개인 신청")))
                .andExpect(content().string(containsString("R-2026-0001")))
                .andExpect(content().string(containsString("2026-10-29 09:30")))
                .andExpect(content().string(containsString("이영희")))
                .andExpect(content().string(containsString("A팀")))
                .andExpect(content().string(containsString("참가 확정")))
                // 조회 전용: 승인·반려·팀 배정·완료 처리 경로와 제출 폼이 남아 있으면 안 된다.
                .andExpect(content().string(not(containsString("/admin/rooms/"))))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // 공통 셸(로그아웃·알림)의 폼과 섞이지 않도록 상세 본문 구간만 잘라 확인한다.
        String opsContent = html.substring(html.indexOf("id=\"ops-content\""), html.indexOf("</main>"));
        org.assertj.core.api.Assertions.assertThat(opsContent).doesNotContain("<form");
        org.assertj.core.api.Assertions.assertThat(opsContent).doesNotContain("type=\"submit\"");
        org.assertj.core.api.Assertions.assertThat(opsContent).doesNotContain("/admin/rooms/");
    }

    @Test
    @DisplayName("담당하지 않거나 삭제된 매칭방은 match=null로 사용할 수 없음 안내를 렌더링한다")
    void rendersUnavailableForMissingOrForeignRoom() throws Exception {
        when(roomManagementService.findDetail(7L, 9999L)).thenReturn(null);

        mockMvc.perform(get("/admin/operations").param("matchId", "9999").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/operations"))
                .andExpect(model().attribute("match", org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(containsString("매칭방을 찾을 수 없습니다.")));

        verify(roomManagementService).findDetail(7L, 9999L);
    }

    @Test
    @DisplayName("matchId가 없으면 상세를 조회하지 않고 안내만 렌더링한다")
    void rendersUnavailableWithoutMatchId() throws Exception {
        mockMvc.perform(get("/admin/operations").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/operations"))
                .andExpect(model().attribute("match", org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(containsString("매칭방을 찾을 수 없습니다.")));

        verifyNoInteractions(roomManagementService);
    }

    @Test
    @DisplayName("시설 관리 권한이 없는 회원은 운영 상세를 조회할 수 없다")
    void memberCannotReadOperations() throws Exception {
        mockMvc.perform(get("/admin/operations").param("matchId", "500").with(user(member())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomManagementService);
    }

    /**
     * 상세 DTO의 조립 규칙은 서비스 계층의 책임이므로 이 화면 테스트는 화면 계약에 필요한 값만 채운다.
     */
    private static RoomManagementDetail detail(Long matchId, String title) {
        return new RoomManagementDetail(matchId, title, "테스트 체육관", "풋살",
                "2026년 11월 3일 (화) 10:00 ~ 12:00", "2 / 8명", "모집 중", "success",
                List.of(), List.of());
    }
}
