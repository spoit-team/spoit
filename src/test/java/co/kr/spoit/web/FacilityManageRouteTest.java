package co.kr.spoit.web;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.facility.controller.FacilityAdminController;
import co.kr.spoit.facility.domain.MatchPeriod;
import co.kr.spoit.facility.dto.FacilityForm;
import co.kr.spoit.facility.service.FacilityAccessDeniedException;
import co.kr.spoit.facility.service.FacilityDeleteBlockedException;
import co.kr.spoit.facility.service.FacilityService;
import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 시설 등록·수정·삭제 요청이 서비스 계약으로 전달되고 결과 안내를 돌려주는지 검증한다. */
@WebMvcTest(controllers = FacilityAdminController.class)
@Import(SecurityConfig.class)
class FacilityManageRouteTest {

    /** 수정·삭제는 시설 목록 섹션으로 복귀해야 한다. */
    private static final String ASSETS_REDIRECT = "/admin/facility";

    private static final String ASSETS_FRAGMENT = "#facility-assets";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityService facilityService;

    @MockitoBean
    private co.kr.spoit.facility.service.FacilityScheduleService facilityScheduleService;

    @MockitoBean
    private co.kr.spoit.match.service.RoomManagementService roomManagementService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("시설 관리자가 등록 폼을 제출하면 서비스에 저장을 위임한다")
    void facilityManagerCanRegister() throws Exception {
        when(facilityService.registerFacility(eq(7L), any(FacilityForm.class))).thenReturn(21L);

        mockMvc.perform(post("/admin/facilities").with(user(facilityManager())).with(csrf())
                        .param("name", "송파 체육관")
                        .param("address", "서울특별시 송파구 올림픽로 25")
                        .param("sports", "농구", "축구")
                        .param("concurrentMatchLimit", "3")
                        .param("status", "운영 중"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(ASSETS_REDIRECT + "?message="
                        + encoded("시설을 등록했습니다.") + ASSETS_FRAGMENT));

        verify(facilityService).registerFacility(eq(7L), any(FacilityForm.class));
    }

    @Test
    @DisplayName("시설 관리자가 수정하면 시설 목록 섹션으로 성공 안내와 함께 돌아간다")
    void facilityManagerCanUpdate() throws Exception {
        mockMvc.perform(post("/admin/facilities/10").with(user(facilityManager())).with(csrf())
                        .param("name", "수정 체육관")
                        .param("address", "서울특별시 송파구 올림픽로 25")
                        .param("sports", "농구")
                        .param("concurrentMatchLimit", "5")
                        .param("status", "점검 중"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(ASSETS_REDIRECT + "?message="
                        + encoded("시설 정보를 수정했습니다.") + ASSETS_FRAGMENT));

        verify(facilityService).updateFacility(eq(7L), eq(10L), any(FacilityForm.class));
    }

    @Test
    @DisplayName("시설 관리자가 삭제하면 시설 목록 섹션으로 성공 안내와 함께 돌아간다")
    void facilityManagerCanDelete() throws Exception {
        mockMvc.perform(post("/admin/facilities/10/delete").with(user(facilityManager())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(ASSETS_REDIRECT + "?message="
                        + encoded("시설을 삭제했습니다.") + ASSETS_FRAGMENT));

        verify(facilityService).deleteFacility(7L, 10L);
    }

    @Test
    @DisplayName("수정 폼 바인딩에 실패하면 시설 목록 섹션의 오류 안내로 되돌아온다")
    void invalidUpdateFormShowsError() throws Exception {
        mockMvc.perform(post("/admin/facilities/10").with(user(facilityManager())).with(csrf())
                        .param("name", "수정 체육관")
                        .param("address", "주소")
                        .param("sports", "농구")
                        .param("concurrentMatchLimit", "구역수아님")
                        .param("status", "운영 중"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(ASSETS_REDIRECT + "?error="
                        + encoded("입력 정보를 확인해 주세요.") + ASSETS_FRAGMENT));

        verifyNoInteractions(facilityService);
    }

    @Test
    @DisplayName("서비스가 권한 예외를 던지면 시설 목록 섹션의 오류 안내와 함께 되돌아온다")
    void accessDeniedShowsError() throws Exception {
        doThrow(new FacilityAccessDeniedException("담당 시설만 관리할 수 있습니다."))
                .when(facilityService).deleteFacility(7L, 10L);

        mockMvc.perform(post("/admin/facilities/10/delete").with(user(facilityManager())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(ASSETS_REDIRECT + "?error="
                        + encoded("담당 시설만 관리할 수 있습니다.") + ASSETS_FRAGMENT));
    }

    @Test
    @DisplayName("매칭방이 남아 삭제가 거부되면 삭제를 막는 방 목록을 flash로 넘겨 경고창을 띄운다")
    void deleteBlockedByRemainingMatchShowsError() throws Exception {
        String blocked = "모집 중이거나 매칭 완료된 매칭방이 있어 시설을 삭제할 수 없습니다.";
        List<MatchPeriod> conflicts = List.of(match(500L, "첫 방"), match(501L, "둘째 방"));
        doThrow(new FacilityDeleteBlockedException(blocked, conflicts))
                .when(facilityService).deleteFacility(7L, 10L);

        mockMvc.perform(post("/admin/facilities/10/delete").with(user(facilityManager())).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(ASSETS_REDIRECT + ASSETS_FRAGMENT))
                .andExpect(flash().attribute("deleteBlockMessage", blocked))
                .andExpect(flash().attribute("deleteBlockConflicts", conflicts))
                .andExpect(flash().attribute("deleteBlockSummary", "첫 방, 둘째 방"));
    }

    @Test
    @DisplayName("일반 회원의 시설 저장·삭제 요청은 서비스를 호출하지 않는다")
    void memberCannotWriteFacilities() throws Exception {
        mockMvc.perform(post("/admin/facilities").with(user(member())).with(csrf())
                        .param("name", "체육관")
                        .param("address", "주소")
                        .param("sports", "농구")
                        .param("concurrentMatchLimit", "3")
                        .param("status", "운영 중"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/facilities/10/delete").with(user(member())).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(facilityService);
    }

    @Test
    @DisplayName("시스템 관리자의 시설 저장 요청도 거부한다")
    void adminCannotWriteFacilities() throws Exception {
        mockMvc.perform(post("/admin/facilities").with(user(co.kr.spoit.member.domain.MemberPrincipalFixture.admin())).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(facilityService);
    }

    @Test
    @DisplayName("비로그인 시설 저장 요청은 CSRF 토큰이 있어도 로그인 화면으로 보낸다")
    void anonymousWriteRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/admin/facilities").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        verifyNoInteractions(facilityService);
    }

    @Test
    @DisplayName("시설 저장 요청에는 CSRF 토큰이 필요하다")
    void writeRequiresCsrf() throws Exception {
        mockMvc.perform(post("/admin/facilities").with(user(facilityManager())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(facilityService);
    }

    @Test
    @DisplayName("존재하지 않는 시설 수정은 오류 안내로 되돌아온다")
    void unknownFacilityShowsError() throws Exception {
        doThrow(new co.kr.spoit.facility.service.FacilityNotFoundException("수정할 시설을 찾을 수 없습니다."))
                .when(facilityService).updateFacility(anyLong(), eq(9999L), any(FacilityForm.class));

        mockMvc.perform(post("/admin/facilities/9999").with(user(facilityManager())).with(csrf())
                        .param("name", "체육관")
                        .param("address", "주소")
                        .param("sports", "농구")
                        .param("concurrentMatchLimit", "3")
                        .param("status", "운영 중"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(ASSETS_REDIRECT + "?error="
                        + encoded("수정할 시설을 찾을 수 없습니다.") + ASSETS_FRAGMENT));
    }

    private static String encoded(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static MatchPeriod match(long matchId, String title) {
        MatchPeriod period = new MatchPeriod();
        period.setMatchId(matchId);
        period.setTitle(title);
        period.setStartAt(LocalDateTime.of(2026, 11, 3, 10, 0));
        period.setEndAt(LocalDateTime.of(2026, 11, 3, 11, 0));
        return period;
    }
}
