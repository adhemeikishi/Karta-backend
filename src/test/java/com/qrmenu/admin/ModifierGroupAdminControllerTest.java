package com.qrmenu.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Groupes d'options d'un produit :
 * {@code /api/admin/restaurants/{id}/menu/items/{itemId}/modifier-groups}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModifierGroupAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private record Item(String restaurantId, String itemId) {
        String url() {
            return "/api/admin/restaurants/" + restaurantId + "/menu/items/" + itemId + "/modifier-groups";
        }
    }

    private Item createItem() throws Exception {
        String restaurantBody = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Options " + System.nanoTime() + "\",\"offer\":\"PRO\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String restaurantId = JsonPath.read(restaurantBody, "$.id");

        String menuBody = mockMvc.perform(put("/api/admin/restaurants/" + restaurantId + "/menu")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories":[{"name":"Burgers","items":[
                                  {"name":"Cheeseburger","price":1290,"currency":"EUR"}]}]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(menuBody, "$.structure.categories[0].items[0].id");

        return new Item(restaurantId, itemId);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants/" + UUID.randomUUID()
                        + "/menu/items/" + UUID.randomUUID() + "/modifier-groups"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyByDefault() throws Exception {
        Item item = createItem();

        mockMvc.perform(get(item.url()).with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void savesAndReadsBackGroupsAndOptions() throws Exception {
        Item item = createItem();

        mockMvc.perform(put(item.url())
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groups":[{"name":"Cuisson","selectionType":"SINGLE","minSelect":1,
                                "options":[{"name":"Saignant"},{"name":"Bien cuit"}]}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Cuisson"))
                .andExpect(jsonPath("$[0].selectionType").value("SINGLE"))
                .andExpect(jsonPath("$[0].maxSelect").value(1))
                .andExpect(jsonPath("$[0].options.length()").value(2));

        mockMvc.perform(get(item.url()).with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].options[0].name").value("Saignant"));
    }

    @Test
    void rejectsMinSelectGreaterThanMaxSelect() throws Exception {
        Item item = createItem();

        mockMvc.perform(put(item.url())
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groups\":[{\"name\":\"Suppléments\",\"selectionType\":\"MULTIPLE\","
                                + "\"minSelect\":3,\"maxSelect\":1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownItemReturns404() throws Exception {
        String restaurantBody = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Sans Item " + System.nanoTime() + "\",\"offer\":\"PRO\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String restaurantId = JsonPath.read(restaurantBody, "$.id");

        mockMvc.perform(get("/api/admin/restaurants/" + restaurantId + "/menu/items/"
                        + UUID.randomUUID() + "/modifier-groups")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }
}
