package co.kr.spoit.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import co.kr.spoit.member.domain.MemberPrincipalFixture;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;
import co.kr.spoit.notification.dto.NotificationListResponse;
import co.kr.spoit.notification.service.NotificationCommandService;
import co.kr.spoit.notification.service.NotificationQueryService;

/**
 * 알림 API의 인증·CSRF·소유권 계약을 검증한다.
 *
 * <p>모든 변경은 로그인 주체의 회원 번호로만 호출되므로, 경로의 알림 번호를 바꿔도 다른 사용자의
 * 알림을 건드릴 수 없다. 비로그인 요청은 401, CSRF 토큰이 없으면 403이다.</p>
 */
@WebMvcTest(controllers = NotificationApiController.class)
@Import(SecurityConfig.class)
class NotificationApiControllerTest {

    private static final long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationQueryService queryService;

    @MockitoBean
    private NotificationCommandService commandService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("알림 목록은 items와 unreadCount를 함께 돌려준다")
    void listReturnsItemsAndUnreadCount() throws Exception {
        given(queryService.findInbox(USER_ID, 30)).willReturn(NotificationListResponse.of(List.of(), 0));

        mockMvc.perform(get("/api/notifications").with(user(MemberPrincipalFixture.member())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.unreadCount").value(0));
        verify(queryService).findInbox(USER_ID, 30);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    @DisplayName("관리자의 알림 API 조회에도 화면 모델 조회가 중복 실행되지 않는다")
    void adminListDoesNotQueryViewModel() throws Exception {
        given(queryService.findInbox(USER_ID, 30)).willReturn(NotificationListResponse.of(List.of(), 0));

        mockMvc.perform(get("/api/notifications").with(user(MemberPrincipalFixture.facilityManager())))
                .andExpect(status().isOk());

        verify(queryService).findInbox(USER_ID, 30);
        verifyNoMoreInteractions(queryService);
    }

    @Test
    @DisplayName("비로그인 알림 목록 요청은 로그인으로 보낸다")
    void listRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().is3xxRedirection());

        verify(queryService, never()).findInbox(any(), anyInt());
    }

    @Test
    @DisplayName("개별 읽음은 로그인 주체의 회원 번호로만 호출된다")
    void readUsesSessionUserId() throws Exception {
        given(commandService.markRead(eq(USER_ID), eq(99L), any())).willReturn(true);

        mockMvc.perform(post("/api/notifications/99/read")
                        .with(user(MemberPrincipalFixture.member())).with(csrf()))
                .andExpect(status().isNoContent());

        // 다른 사용자 번호를 파라미터로 주입할 경로가 없다.
        verify(commandService).markRead(eq(USER_ID), eq(99L), any());
    }

    @Test
    @DisplayName("소유하지 않았거나 없는 알림 읽음은 404로 응답한다")
    void readMissingReturnsNotFound() throws Exception {
        given(commandService.markRead(eq(USER_ID), eq(99L), any())).willReturn(false);

        mockMvc.perform(post("/api/notifications/99/read")
                        .with(user(MemberPrincipalFixture.member())).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("알림 변경 요청에 CSRF 토큰이 없으면 거부한다")
    void readRequiresCsrf() throws Exception {
        mockMvc.perform(post("/api/notifications/99/read").with(user(MemberPrincipalFixture.member())))
                .andExpect(status().isForbidden());

        verify(commandService, never()).markRead(any(), any(), any());
    }

    @Test
    @DisplayName("삭제와 모두 읽음도 로그인 주체의 회원 번호로만 호출된다")
    void deleteAndReadAllUseSessionUserId() throws Exception {
        given(commandService.delete(USER_ID, 99L)).willReturn(true);

        mockMvc.perform(post("/api/notifications/99/delete")
                        .with(user(MemberPrincipalFixture.member())).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/notifications/read-all")
                        .with(user(MemberPrincipalFixture.member())).with(csrf()))
                .andExpect(status().isNoContent());

        verify(commandService).delete(USER_ID, 99L);
        verify(commandService).markAllRead(eq(USER_ID), any());
    }

    @Test
    @DisplayName("응답 본문에는 다른 사용자의 존재 여부를 흘리는 정보가 없다")
    void deleteMissingReturnsEmptyNotFound() throws Exception {
        given(commandService.delete(USER_ID, 99L)).willReturn(false);

        mockMvc.perform(post("/api/notifications/99/delete")
                        .with(user(MemberPrincipalFixture.member())).with(csrf()))
                .andExpect(status().isNotFound());
    }
}
