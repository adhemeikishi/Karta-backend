package com.qrmenu.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dashboardReturnsGlobalTotals() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRestaurants").exists())
                .andExpect(jsonPath("$.totalQrCodes").exists())
                .andExpect(jsonPath("$.activeQrCodes").exists())
                .andExpect(jsonPath("$.scansToday").exists())
                .andExpect(jsonPath("$.scansThisWeek").exists())
                .andExpect(jsonPath("$.scansThisMonth").exists())
                .andExpect(jsonPath("$.scansTotal").exists());
    }
}
