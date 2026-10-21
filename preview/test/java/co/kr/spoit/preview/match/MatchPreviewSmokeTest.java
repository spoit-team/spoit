package co.kr.spoit.preview.match;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MatchPreviewSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listPageRendersFixtureRooms() throws Exception {
        mockMvc.perform(get("/matches"))
                .andExpect(status().isOk())
                .andExpect(view().name("matches/list"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("참여 가능한 매칭방")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("주말 테니스 복식 4인 매치")));
    }

    @Test
    void cardsPageRendersFixtureRooms() throws Exception {
        mockMvc.perform(get("/matches/cards"))
                .andExpect(status().isOk())
                .andExpect(view().name("matches/cards"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("카드형")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("초보 환영 배드민턴 복식")));
    }

    @Test
    void detailPageRendersFixtureMatch() throws Exception {
        mockMvc.perform(get("/matches/102"))
                .andExpect(status().isOk())
                .andExpect(view().name("matches/detail"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("주말 테니스 복식 4인 매치")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1자리 남았습니다")));
    }

    @Test
    void applyPageRendersFixtureSelection() throws Exception {
        mockMvc.perform(get("/matches/102/apply"))
                .andExpect(status().isOk())
                .andExpect(view().name("matches/join"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("매치 참가 신청")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("김민준님 신청")));
    }
}
