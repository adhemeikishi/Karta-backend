package com.qrmenu.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QrCodeAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getByIdReturnsQrCodeDetails() throws Exception {
        String restaurantBody = mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto QR Detail\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String restaurantId = com.jayway.jsonpath.JsonPath.read(restaurantBody, "$.id");

        String qrBody = mockMvc.perform(post("/api/admin/restaurants/" + restaurantId + "/qr-codes")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"QR test\",\"destinationUrl\":\"https://example.com/menu.pdf\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String qrId = com.jayway.jsonpath.JsonPath.read(qrBody, "$.id");

        mockMvc.perform(get("/api/admin/qr-codes/" + qrId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(qrId))
                .andExpect(jsonPath("$.destinationUrl").value("https://example.com/menu.pdf"))
                .andExpect(jsonPath("$.redirectUrl").exists());
    }

    @Test
    void getByIdReturns404ForUnknownQrCode() throws Exception {
        mockMvc.perform(get("/api/admin/qr-codes/00000000-0000-0000-0000-000000000000")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }
}
