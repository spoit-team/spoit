package co.kr.spoit.web;

import static co.kr.spoit.member.domain.MemberPrincipalFixture.admin;
import static co.kr.spoit.member.domain.MemberPrincipalFixture.facilityManager;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.member.domain.MemberPrincipal;
import co.kr.spoit.member.domain.MemberPrincipalFixture;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
class CorePageRouteTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} renders {1}")
    @MethodSource("pageRoutes")
    void existingPageTemplateRenders(String path, String viewName, String visibleContent) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(view().name(viewName))
                .andExpect(content().string(containsString(visibleContent)));
    }

    @ParameterizedTest(name = "{0} renders {1} for {3}")
    @MethodSource("adminPageRoutes")
    void adminPageTemplateRendersForAllowedRole(String path, String viewName, String visibleContent,
            MemberPrincipal principal, String hiddenContent) throws Exception {
        mockMvc.perform(get(path).with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name(viewName))
                .andExpect(content().string(containsString(visibleContent)))
                .andExpect(content().string(not(containsString(hiddenContent))));
    }

    @Test
    void matchApplyRequiresLogin() throws Exception {
        mockMvc.perform(get("/matches/1/apply"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void matchApplyRendersForMember() throws Exception {
        mockMvc.perform(get("/matches/1/apply").with(user(MemberPrincipalFixture.member())))
                .andExpect(status().isOk())
                .andExpect(view().name("matches/join"))
                .andExpect(content().string(containsString("매치 참가 신청")));
    }

    @Test
    void clubPagesRequireLogin() throws Exception {
        mockMvc.perform(get("/teams/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/teams/manage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void clubPagesRenderForMember() throws Exception {
        mockMvc.perform(get("/teams/1").with(user(principal("일반 회원"))))
                .andExpect(status().isOk())
                .andExpect(view().name("teams/detail"))
                .andExpect(content().string(containsString("팀 목록으로 돌아가기")));
        mockMvc.perform(get("/teams/manage").with(user(principal("일반 회원"))))
                .andExpect(status().isOk())
                .andExpect(view().name("teams/manage"))
                .andExpect(content().string(containsString("팀 관리")));
    }

    private static Stream<Arguments> pageRoutes() {
        return Stream.of(
                Arguments.of("/login", "auth/login", "로그인"),
                Arguments.of("/signup", "auth/signup", "회원가입"),
                Arguments.of("/matches", "matches/cards", "참여 가능한 매칭방"),
                Arguments.of("/matches/1", "matches/detail", "매칭방 목록"),
                Arguments.of("/teams", "teams/list", "SPO:IT 팀"));
    }

    private static Stream<Arguments> adminPageRoutes() {
        return Stream.of(
                // /admin/facility 렌더링은 AdminFacilityPageTest가 담당한다.
                Arguments.of("/admin/operations", "admin/operations", "매치 운영", facilityManager(),
                        "/admin/system"),
                Arguments.of("/admin/system", "admin/system", "시스템 모니터링", admin(),
                        "/admin/facility"));
    }
}
