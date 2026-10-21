package co.kr.spoit.preview.admin;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPreviewPageSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void facilityPageRendersPreviewFixtures() throws Exception {
        mockMvc.perform(get("/admin/facility"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/facility"))
                .andExpect(content().string(containsString("시설 대시보드")))
                .andExpect(content().string(containsString("강남 저녁 테니스 복식")))
                .andExpect(content().string(containsString("알림 3건 · 미확인 2건")));
    }

    @Test
    void operationsPageRendersPreviewMatch() throws Exception {
        mockMvc.perform(get("/admin/operations").param("matchId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/operations"))
                .andExpect(content().string(containsString("매치 운영")))
                .andExpect(content().string(containsString("강남 저녁 테니스 복식")))
                .andExpect(content().string(containsString("A/B팀 구성")));
    }

    @Test
    void systemPageRendersPreviewFixtures() throws Exception {
        mockMvc.perform(get("/admin/system"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/system"))
                .andExpect(content().string(containsString("시스템 모니터링")))
                .andExpect(content().string(containsString("RPT-2026-1042")))
                .andExpect(content().string(containsString("알림 3건 · 미확인 2건")));
    }
}
