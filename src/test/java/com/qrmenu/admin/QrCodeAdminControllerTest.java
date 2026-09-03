package com.qrmenu.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QrCodeAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /** Le QR est désormais créé automatiquement à la création du restaurant — jamais un second appel manuel. */
    @Test
    void getByIdReturnsQrCodeDetails() throws Exception {
        String restaurantBody = mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto QR Detail\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String restaurantId = com.jayway.jsonpath.JsonPath.read(restaurantBody, "$.id");

        String qrListBody = mockMvc.perform(get("/api/admin/restaurants/" + restaurantId + "/qr-codes")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String qrId = com.jayway.jsonpath.JsonPath.read(qrListBody, "$[0].id");
        String code = com.jayway.jsonpath.JsonPath.read(qrListBody, "$[0].code");

        mockMvc.perform(get("/api/admin/qr-codes/" + qrId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(qrId))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId))
                // Destination auto-générée : la page de menu de ce QR, jamais saisie par l'admin.
                .andExpect(jsonPath("$.destinationUrl", endsWith("/m/" + code)))
                .andExpect(jsonPath("$.redirectUrl").exists());
    }

    /**
     * Un restaurant n'a jamais qu'un seul QR : tenter d'en créer un second — même
     * manuellement, via l'endpoint de secours — est refusé.
     */
    @Test
    void secondManualQrCreationIsRejected() throws Exception {
        String restaurantBody = mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto QR Unique " + System.nanoTime() + "\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String restaurantId = com.jayway.jsonpath.JsonPath.read(restaurantBody, "$.id");

        mockMvc.perform(post("/api/admin/restaurants/" + restaurantId + "/qr-codes")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"QR bis\",\"destinationUrl\":\"https://example.com/menu\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/admin/restaurants/" + restaurantId + "/qr-codes")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /** L'endpoint de modification de la destination n'existe plus : la destination est permanente. */
    @Test
    void destinationCannotBeModifiedAnymore() throws Exception {
        String restaurantBody = mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Destination Verrouillee " + System.nanoTime() + "\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String restaurantId = com.jayway.jsonpath.JsonPath.read(restaurantBody, "$.id");
        String qrListBody = mockMvc.perform(get("/api/admin/restaurants/" + restaurantId + "/qr-codes")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andReturn().getResponse().getContentAsString();
        String qrId = com.jayway.jsonpath.JsonPath.read(qrListBody, "$[0].id");

        mockMvc.perform(put("/api/admin/qr-codes/" + qrId)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinationUrl\":\"https://example.com/tentative\"}"))
                // Le chemin existe toujours (GET /api/admin/qr-codes/{id}), mais plus aucun
                // mapping PUT : Spring répond 405, pas 404 — la route de modification a
                // bien disparu, ce qui est ce qui compte ici.
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void getByIdReturns404ForUnknownQrCode() throws Exception {
        mockMvc.perform(get("/api/admin/qr-codes/00000000-0000-0000-0000-000000000000")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }
}
