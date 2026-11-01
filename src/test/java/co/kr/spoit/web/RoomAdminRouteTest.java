package co.kr.spoit.web;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.facility.service.FacilityScheduleUnavailableException;
import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.match.controller.RoomAdminController;
import co.kr.spoit.match.service.RoomCommandException;
import co.kr.spoit.match.service.RoomCommandService;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 매칭방 생성·수정·취소 요청이 인증 주체 ID로 처리되고 결과가 매칭방 관리 화면 안내로 돌아가는지 검증한다. */
@WebMvcTest(controllers = RoomAdminController.class)
@Import(SecurityConfig.class)
class RoomAdminRouteTest {

    private static final String BOOKINGS = "/admin/facility?view=bookings";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomCommandService roomCommandService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("생성은 폼 값을 바인딩해 인증 주체 ID로 저장하고 성공 안내와 함께 목록으로 돌아간다")
    void createsRoomWithPrincipalId() throws Exception {
        when(roomCommandService.create(eq(7L), any())).thenReturn(812L);

        mockMvc.perform(post("/admin/rooms").with(user(facilityManager())).with(csrf())
                        .param("facilityId", "30").param("sportId", "2").param("title", "토요 풋살")
                        .param("date", "2026-11-08").param("hours", "10:00", "11:00")
                        .param("teamCapacity", "3")
                        .param("feePerPerson", "9000").param("requiredSkill", "중급"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(BOOKINGS + "&message=" + encode("812번 매칭방을 만들었습니다.")
                        + "#facility-bookings"));

        verify(roomCommandService).create(eq(7L), argThat(form -> form.getFacilityId() == 30L
                && form.getHours().equals(List.of("10:00", "11:00"))
                && "중급".equals(form.getRequiredSkill())));
    }

    @Test
    @DisplayName("검증 실패 문구는 error 안내로 그대로 돌려준다")
    void returnsServiceErrorMessage() throws Exception {
        doThrow(new FacilityScheduleUnavailableException("운영 시간이 아닙니다."))
                .when(roomCommandService).update(eq(7L), eq(500L), any());

        mockMvc.perform(post("/admin/rooms/500").with(user(facilityManager())).with(csrf())
                        .param("title", "변경"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(BOOKINGS + "&error=" + encode("운영 시간이 아닙니다.")
                        + "#facility-bookings"));
    }

    @Test
    @DisplayName("취소는 인증 주체 ID로 처리하고 실패 문구도 안내로 돌려준다")
    void cancelsRoom() throws Exception {
        mockMvc.perform(post("/admin/rooms/500/cancel").with(user(facilityManager())).with(csrf()))
                .andExpect(redirectedUrl(BOOKINGS + "&message=" + encode("500번 매칭방을 취소했습니다.")
                        + "#facility-bookings"));
        verify(roomCommandService).cancel(7L, 500L);

        doThrow(new RoomCommandException("매칭방을 찾을 수 없습니다."))
                .when(roomCommandService).cancel(7L, 501L);
        mockMvc.perform(post("/admin/rooms/501/cancel").with(user(facilityManager())).with(csrf()))
                .andExpect(redirectedUrl(BOOKINGS + "&error=" + encode("매칭방을 찾을 수 없습니다.")
                        + "#facility-bookings"));
    }

    @Test
    @DisplayName("시설 관리자가 아니면 막고, CSRF 토큰 없는 요청도 막는다")
    void requiresFacilityManagerAndCsrf() throws Exception {
        mockMvc.perform(post("/admin/rooms/500/cancel").with(user(member())).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/rooms/500/cancel").with(user(facilityManager())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(roomCommandService);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
