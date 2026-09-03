package com.qrmenu.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RestaurantAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsRestaurantWithValidAdminCredentials() throws Exception {
        mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kebab Central\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Kebab Central"))
                .andExpect(jsonPath("$.offer").value("BASIC"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void createsRestaurantWithChosenOffer() throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Premium\",\"offer\":\"PREMIUM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.offer").value("PREMIUM"))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/admin/restaurants/" + id)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer").value("PREMIUM"));
    }

    /**
     * Règle produit : 1 restaurant = 1 QR permanent, créé automatiquement — jamais une
     * action séparée du restaurateur.
     */
    @Test
    void automaticallyCreatesTheRestaurantsUniqueQrCode() throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Auto QR " + System.nanoTime() + "\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/admin/restaurants/" + id + "/qr-codes")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].destinationUrl").exists());
    }

    @Test
    void rejectsMissingOffer() throws Exception {
        mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sans Offre\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBlankRestaurantName() throws Exception {
        mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void renamesRestaurant() throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ancien Nom\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/admin/restaurants/" + id)
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nouveau Nom\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nouveau Nom"));
    }

    @Test
    void changesOfferViaApi() throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Changement Offre\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/admin/restaurants/" + id + "/offer")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offer\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer").value("PRO"));

        mockMvc.perform(get("/api/admin/restaurants/" + id)
                        .with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.offer").value("PRO"));
    }

    @Test
    void rejectsOfferChangeWithoutOffer() throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Offre Invalide\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(put("/api/admin/restaurants/" + id + "/offer")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesClientAndCascadesQrCode() throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A Supprimer\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        // Le QR est désormais créé automatiquement à la création du restaurant.
        String qrBody = mockMvc.perform(get("/api/admin/restaurants/" + id + "/qr-codes")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String code = com.jayway.jsonpath.JsonPath.read(qrBody, "$[0].code");

        mockMvc.perform(delete("/api/admin/restaurants/" + id)
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/restaurants/" + id)
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());

        // Le QR (cascade) n'existe plus -> scan public en 404
        mockMvc.perform(get("/q/" + code)).andExpect(status().isNotFound());
    }

    @Test
    void deletingClientAlsoRemovesItsMenuPdf() throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Client PDF\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        String menuBody = mockMvc.perform(multipart("/api/admin/restaurants/" + id + "/menu/pdf")
                        .file(new MockMultipartFile("file", "carte.pdf", "application/pdf",
                                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8)))
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String assetId = com.jayway.jsonpath.JsonPath.read(menuBody, "$.pdf.assetId");

        mockMvc.perform(delete("/api/admin/restaurants/" + id)
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/media/" + assetId)).andExpect(status().isNotFound());
    }

    @Test
    void deleteUnknownClientReturns404() throws Exception {
        mockMvc.perform(delete("/api/admin/restaurants/" + java.util.UUID.randomUUID())
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- statistiques

    @Test
    void statsEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants/" + java.util.UUID.randomUUID() + "/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void statsForUnknownClientReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants/" + java.util.UUID.randomUUID() + "/stats")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void statsExposeFourCountersAndAThirtyDaySeries() throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Stats " + System.nanoTime() + "\",\"offer\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/admin/restaurants/" + id + "/stats")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value(0))
                .andExpect(jsonPath("$.last7Days").value(0))
                .andExpect(jsonPath("$.last30Days").value(0))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.daily.length()").value(30))
                // La date est une date locale sérialisée en ISO, pas un instant.
                .andExpect(jsonPath("$.daily[0].date").exists())
                .andExpect(jsonPath("$.daily[0].scans").value(0));
    }

    @Test
    void listReturnsCountsForEachRestaurant() throws Exception {
        mockMvc.perform(post("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Compteurs\",\"offer\":\"PRO\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/restaurants")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offer").exists())
                .andExpect(jsonPath("$[0].qrCodeCount").exists())
                .andExpect(jsonPath("$[0].activeQrCodeCount").exists())
                .andExpect(jsonPath("$[0].totalScans").exists());
    }
}
