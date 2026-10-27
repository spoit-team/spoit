package co.kr.spoit.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.member.domain.Member;
import co.kr.spoit.member.domain.MemberPrincipal;

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
    void adminPageTemplateRendersForAllowedRole(String path, String viewName, String visibleContent, String role)
            throws Exception {
        mockMvc.perform(get(path).with(user(principal(role))))
                .andExpect(status().isOk())
                .andExpect(view().name(viewName))
                .andExpect(content().string(containsString(visibleContent)));
    }

    private static Stream<Arguments> pageRoutes() {
        return Stream.of(
                Arguments.of("/login", "auth/login", "로그인"),
                Arguments.of("/signup", "auth/signup", "회원가입"),
                Arguments.of("/matches", "matches/cards", "참여 가능한 매칭방"),
                Arguments.of("/matches/1", "matches/detail", "매칭방 목록"),
                Arguments.of("/matches/1/apply", "matches/join", "매치 참가 신청"),
                Arguments.of("/teams", "teams/list", "SPO:IT 팀"),
                Arguments.of("/teams/1", "teams/detail", "팀 목록으로 돌아가기"),
                Arguments.of("/teams/manage", "teams/manage", "팀 관리"));
    }

    private static Stream<Arguments> adminPageRoutes() {
        return Stream.of(
                Arguments.of("/admin/facility", "admin/facility", "시설 대시보드", "시설 관리자"),
                Arguments.of("/admin/operations", "admin/operations", "매치 운영", "시설 관리자"),
                Arguments.of("/admin/system", "admin/system", "시스템 모니터링", "시스템 관리자"));
    }

    private static MemberPrincipal principal(String role) {
        Member member = new Member();
        member.setUserId(7L);
        member.setEmail("admin@example.com");
        member.setNickname("관리자");
        member.setPhone("010-1234-5678");
        member.setRole(role);
        return MemberPrincipal.from(member);
    }
}
