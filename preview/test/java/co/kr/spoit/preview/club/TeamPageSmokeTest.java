package co.kr.spoit.preview.club;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TeamPageSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void teamListRenders() throws Exception {
        mockMvc.perform(get("/teams"))
                .andExpect(status().isOk())
                .andExpect(view().name("teams/list"));
    }

    @Test
    void teamDetailRenders() throws Exception {
        mockMvc.perform(get("/teams/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("teams/detail"));
    }

    @Test
    void teamManageRendersAllTabs() throws Exception {
        for (String tab : new String[] {"team", "members", "applications"}) {
            mockMvc.perform(get("/teams/manage").param("tab", tab))
                    .andExpect(status().isOk())
                    .andExpect(view().name("teams/manage"));
        }
    }
}
