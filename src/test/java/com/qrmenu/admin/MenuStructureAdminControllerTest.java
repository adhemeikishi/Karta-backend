package com.qrmenu.admin;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API admin du menu structuré : le menu est une ressource unique
 * ({@code GET / POST / PUT / DELETE} sur {@code .../menu}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuStructureAdminControllerTest {

    private static final String MENU_JSON = """
            {
              "categories": [
                {
                  "name": "Burgers",
                  "sortOrder": 1,
                  "items": [
                    {
                      "name": "Cheeseburger",
                      "description": "Steak, cheddar, salade",
                      "price": 1290,
                      "currency": "EUR",
                      "sortOrder": 1,
                      "available": true
                    }
                  ]
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    private String createRestaurant(String offer) throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Struct " + System.nanoTime() + "\",\"offer\":\"" + offer + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String menuUrl(String restaurantId) {
        return "/api/admin/restaurants/" + restaurantId + "/menu";
    }

    @Test
    void structuredMenuEndpointsRequireAuthentication() throws Exception {
        String url = menuUrl(UUID.randomUUID().toString());

        mockMvc.perform(post(url)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(MENU_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void createsMenuThenRejectsDuplicate() throws Exception {
        String id = createRestaurant("PRO");

        mockMvc.perform(post(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("STRUCTURED"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.structure.categories").isEmpty());

        mockMvc.perform(post(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isConflict());
    }

    @Test
    void savesAndReadsBackTheCanonicalMenuJson() throws Exception {
        String id = createRestaurant("PREMIUM");

        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MENU_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("STRUCTURED"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.published").value(false));

        mockMvc.perform(get(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structure.restaurantName").exists())
                .andExpect(jsonPath("$.structure.currency").value("EUR"))
                .andExpect(jsonPath("$.structure.categories.length()").value(1))
                .andExpect(jsonPath("$.structure.categories[0].name").value("Burgers"))
                .andExpect(jsonPath("$.structure.categories[0].sortOrder").value(1))
                .andExpect(jsonPath("$.structure.categories[0].items.length()").value(1))
                .andExpect(jsonPath("$.structure.categories[0].items[0].name").value("Cheeseburger"))
                .andExpect(jsonPath("$.structure.categories[0].items[0].price").value(1290))
                .andExpect(jsonPath("$.structure.categories[0].items[0].currency").value("EUR"))
                .andExpect(jsonPath("$.structure.categories[0].items[0].imageAssetId")
                        .value(Matchers.nullValue()))
                .andExpect(jsonPath("$.pdf").value(Matchers.nullValue()));
    }

    @Test
    void rejectsInvalidPayload() throws Exception {
        String id = createRestaurant("PRO");

        // Nom vide -> validation Bean Validation.
        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categories\":[{\"name\":\"\",\"items\":[]}]}"))
                .andExpect(status().isBadRequest());

        // Prix négatif.
        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categories\":[{\"name\":\"Burgers\",\"items\":"
                                + "[{\"name\":\"X\",\"price\":-5}]}]}"))
                .andExpect(status().isBadRequest());

        // Devise inconnue.
        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categories\":[{\"name\":\"Burgers\",\"items\":"
                                + "[{\"name\":\"X\",\"price\":100,\"currency\":\"XXX\"}]}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsStructuredMenuForBasicClient() throws Exception {
        String id = createRestaurant("BASIC");

        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MENU_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void deletesMenu() throws Exception {
        String id = createRestaurant("PRO");
        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MENU_JSON))
                .andExpect(status().isOk());

        mockMvc.perform(delete(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.structure.categories").isEmpty());

        mockMvc.perform(delete(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }
}
