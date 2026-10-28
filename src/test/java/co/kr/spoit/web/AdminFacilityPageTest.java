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

import co.kr.spoit.facility.controller.FacilityAdminController;
import co.kr.spoit.facility.domain.FacilitySummary;
import co.kr.spoit.facility.dto.FacilityListItem;
import co.kr.spoit.facility.service.FacilityService;
import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/** 시설 관리 화면이 인증 주체의 담당 시설만 실제 서비스 조회로 렌더링하는지 검증한다. */
@WebMvcTest(controllers = FacilityAdminController.class)
@Import(SecurityConfig.class)
class AdminFacilityPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityService facilityService;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @Test
    @DisplayName("로그인한 시설 관리자의 회원 ID로 담당 시설을 조회해 목록을 렌더링한다")
    void rendersFacilitiesLookedUpByPrincipalUserId() throws Exception {
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of(
                facility(10L, "송파 풋살파크", "농구, 축구", 3, "운영 중"),
                facility(11L, "마포 체육관", null, 2, "특별 운영")));

        mockMvc.perform(get("/admin/facility").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/facility"))
                .andExpect(content().string(containsString("송파 풋살파크")))
                .andExpect(content().string(containsString("농구, 축구")))
                .andExpect(content().string(containsString("마포 체육관")))
                .andExpect(content().string(containsString("미설정")))
                .andExpect(content().string(containsString("특별 운영")))
                .andExpect(content().string(containsString("2구역")))
                .andExpect(content().string(not(containsString("수용 인원"))))
                .andExpect(content().string(not(containsString("facility-capacity"))))
                .andExpect(content().string(not(containsString("등록된 시설이 없습니다"))))
                .andExpect(content().string(not(containsString("/admin/system"))));

        verify(facilityService).findManagedFacilities(7L);
    }

    @Test
    @DisplayName("담당 시설이 없으면 빈 상태 문구를 렌더링한다")
    void rendersEmptyStateWhenNoManagedFacility() throws Exception {
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/facility").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("등록된 시설이 없습니다")));
    }

    @Test
    @DisplayName("시설 관리 권한이 없는 회원은 조회 서비스를 호출하지 못한다")
    void memberWithoutFacilityRoleCannotReadFacilities() throws Exception {
        mockMvc.perform(get("/admin/facility").with(user(member())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(facilityService);
    }

    @Test
    @DisplayName("변경 성공 메시지를 시설 화면에 렌더링한다")
    void rendersSuccessMessageFromQuery() throws Exception {
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/facility").param("message", "시설 정보를 수정했습니다.")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("시설 정보를 수정했습니다.")))
                .andExpect(content().string(containsString("admin-flash")))
                .andExpect(content().string(not(containsString("admin-flash is-error"))));
    }

    @Test
    @DisplayName("변경 실패 메시지를 시설 화면에 오류 안내로 렌더링한다")
    void rendersErrorMessageFromQuery() throws Exception {
        when(facilityService.findManagedFacilities(7L)).thenReturn(List.of());

        mockMvc.perform(get("/admin/facility").param("error", "담당 시설만 관리할 수 있습니다.")
                        .with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("담당 시설만 관리할 수 있습니다.")))
                .andExpect(content().string(containsString("admin-flash is-error")))
                .andExpect(content().string(containsString("role=\"alert\"")));
    }

    private static FacilityListItem facility(Long id, String name, String sportsLabel,
            Integer concurrentMatchLimit, String status) {
        FacilitySummary facility = new FacilitySummary();
        facility.setFacilityId(id);
        facility.setName(name);
        facility.setAddress("서울특별시 테스트구 " + id);
        facility.setSportsLabel(sportsLabel);
        facility.setConcurrentMatchLimit(concurrentMatchLimit);
        facility.setStatus(status);
        return FacilityListItem.from(facility);
    }
}
