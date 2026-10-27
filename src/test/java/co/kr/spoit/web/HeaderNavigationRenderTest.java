package co.kr.spoit.web;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.admin;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.member;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.club.controller.ClubController;
import co.kr.spoit.club.service.ClubService;
import co.kr.spoit.global.config.SecurityConfig;
import co.kr.spoit.member.security.MemberLoginFailureHandler;
import co.kr.spoit.member.security.MemberLoginSuccessHandler;
import co.kr.spoit.member.service.MemberUserDetailsService;

/**
 * 공통 헤더가 로그인 회원의 역할에 따라 관리자 메뉴를 실제로 렌더링하는지 검증한다.
 * ClubService를 mock으로 바꾼 {@code /teams/manage} 화면과 실제 템플릿·{@code GlobalModelAdvice}·
 * 보안 필터 체인을 사용한다.
 */
@WebMvcTest(controllers = ClubController.class)
@Import(SecurityConfig.class)
class HeaderNavigationRenderTest {

    private static final String FACILITY_LINK = "href=\"/admin/facility\"";
    private static final String SYSTEM_LINK = "href=\"/admin/system\"";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberLoginSuccessHandler loginSuccessHandler;

    @MockitoBean
    private MemberLoginFailureHandler loginFailureHandler;

    @MockitoBean
    private MemberUserDetailsService memberUserDetailsService;

    @MockitoBean
    private ClubService clubService;

    @Test
    @DisplayName("비로그인 사용자 헤더에는 로그인 링크만 있고 관리자 메뉴는 없다")
    void anonymousHeaderHasNoAdminLinks() throws Exception {
        mockMvc.perform(get("/teams/manage"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-auth-guest")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("data-auth-member"))))
                .andExpect(content().string(not(containsString(FACILITY_LINK))))
                .andExpect(content().string(not(containsString(SYSTEM_LINK))));
    }

    @Test
    @DisplayName("일반 회원 헤더에는 로그아웃만 있고 관리자 메뉴는 없다")
    void memberHeaderHasNoAdminLinks() throws Exception {
        mockMvc.perform(get("/teams/manage").with(user(member())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-auth-member")))
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(not(containsString("data-auth-guest"))))
                .andExpect(content().string(not(containsString(FACILITY_LINK))))
                .andExpect(content().string(not(containsString(SYSTEM_LINK))));
    }

    @Test
    @DisplayName("시설 관리자 헤더에는 시설 관리 링크만 있고 시스템 관리 링크는 없다")
    void facilityManagerHeaderHasOnlyFacilityLink() throws Exception {
        mockMvc.perform(get("/teams/manage").with(user(facilityManager())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-auth-member")))
                .andExpect(content().string(containsString(FACILITY_LINK)))
                .andExpect(content().string(containsString("시설 관리")))
                .andExpect(content().string(not(containsString(SYSTEM_LINK))));
    }

    @Test
    @DisplayName("시스템 관리자 헤더에는 시스템 관리 링크만 있고 시설 관리 링크는 없다")
    void systemAdminHeaderHasOnlySystemLink() throws Exception {
        mockMvc.perform(get("/teams/manage").with(user(admin())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-auth-member")))
                .andExpect(content().string(containsString(SYSTEM_LINK)))
                .andExpect(content().string(containsString("시스템 관리")))
                .andExpect(content().string(not(containsString(FACILITY_LINK))));
    }
}
