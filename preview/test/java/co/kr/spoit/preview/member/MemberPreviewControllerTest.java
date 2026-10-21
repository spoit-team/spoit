package co.kr.spoit.preview.member;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import co.kr.spoit.preview.member.MyPageDto;
import co.kr.spoit.preview.member.ProfilePageDto;
import co.kr.spoit.preview.member.MemberAuthModels.SignupPageDto;

@SpringBootTest
@AutoConfigureMockMvc
class MemberPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageRendersWithFixture() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(model().attribute("email", ""))
                .andExpect(model().attribute("remember", true))
                .andExpect(content().string(containsString("로그인")));
    }

    @Test
    void signupPageRendersWithTypedFormAndDistricts() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/signup"))
                .andExpect(model().attribute("signupForm", instanceOf(SignupPageDto.SignupForm.class)))
                .andExpect(model().attribute("signupForm", hasProperty("agreeService", is(false))))
                .andExpect(model().attribute("signupForm", hasProperty("agreePrivacy", is(false))))
                .andExpect(model().attribute("signupForm", hasProperty("email", is(""))))
                .andExpect(model().attribute("districts", instanceOf(java.util.List.class)))
                .andExpect(content().string(containsString("회원가입")));
    }

    @Test
    void myPageRendersWithTypedSections() throws Exception {
        mockMvc.perform(get("/mypage"))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/index"))
                .andExpect(model().attribute("member", instanceOf(MyPageDto.MemberView.class)))
                .andExpect(model().attribute("summary", instanceOf(MyPageDto.SummaryView.class)))
                .andExpect(model().attribute("sports", instanceOf(java.util.List.class)))
                .andExpect(model().attribute("history", instanceOf(java.util.List.class)))
                .andExpect(model().attribute("currentReservations", instanceOf(java.util.List.class)))
                .andExpect(model().attribute("pastReservations", instanceOf(java.util.List.class)))
                .andExpect(model().attribute("notifications", instanceOf(java.util.List.class)))
                .andExpect(content().string(containsString("마이페이지")));
    }

    @Test
    void profilePageRendersWithTypedProfileAndSports() throws Exception {
        mockMvc.perform(get("/mypage/profile"))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage/profile"))
                .andExpect(model().attribute("profile", instanceOf(ProfilePageDto.ProfileForm.class)))
                .andExpect(model().attribute("sports", instanceOf(java.util.List.class)))
                .andExpect(model().attribute("districts", instanceOf(java.util.List.class)))
                .andExpect(content().string(containsString("프로필 수정")));
    }
}
