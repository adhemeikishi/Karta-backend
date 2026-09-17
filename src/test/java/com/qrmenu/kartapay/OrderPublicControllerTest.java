package com.qrmenu.kartapay;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Commande publique Karta Pay, résolue par le code QR :
 * {@code /api/public/restaurants/{code}/orders}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private record Setup(String code, String itemId) {
    }

    private Setup setupRestaurant(boolean kartaPayEnabled) throws Exception {
        String restaurantBody = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Public " + System.nanoTime() + "\",\"offer\":\"PRO\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String restaurantId = JsonPath.read(restaurantBody, "$.id");

        if (kartaPayEnabled) {
            mockMvc.perform(put("/api/admin/restaurants/" + restaurantId + "/karta-pay")
                            .with(httpBasic("admin", "test-password"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"enabled\":true}"))
                    .andExpect(status().isOk());
        }

        String qrBody = mockMvc.perform(get("/api/admin/restaurants/" + restaurantId + "/qr-codes")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(qrBody, "$[0].code");

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

        return new Setup(code, itemId);
    }

    @Test
    void createsAnOrderThenTracksItPublicly() throws Exception {
        Setup setup = setupRestaurant(true);

        String body = mockMvc.perform(post("/api/public/restaurants/" + setup.code() + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fulfillmentType\":\"TAKEAWAY\",\"customerName\":\"Client Test\","
                                + "\"lines\":[{\"itemId\":\"" + setup.itemId() + "\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCents").value(2580))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String orderNumber = JsonPath.read(body, "$.orderNumber");

        mockMvc.perform(get("/api/public/restaurants/" + setup.code() + "/orders/" + orderNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value(orderNumber))
                .andExpect(jsonPath("$.totalCents").value(2580));
    }

    @Test
    void noAuthenticationIsRequired() throws Exception {
        Setup setup = setupRestaurant(true);

        // Pas de Basic Auth fournie, contrairement à /api/admin/** — la commande passe.
        mockMvc.perform(post("/api/public/restaurants/" + setup.code() + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fulfillmentType\":\"TAKEAWAY\",\"customerName\":\"Client Test\","
                                + "\"lines\":[{\"itemId\":\"" + setup.itemId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsWhenKartaPayIsDisabled() throws Exception {
        Setup setup = setupRestaurant(false);

        mockMvc.perform(post("/api/public/restaurants/" + setup.code() + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fulfillmentType\":\"TAKEAWAY\",\"customerName\":\"Client Test\","
                                + "\"lines\":[{\"itemId\":\"" + setup.itemId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownRestaurantCodeReturns404() throws Exception {
        mockMvc.perform(post("/api/public/restaurants/code-inconnu/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fulfillmentType\":\"TAKEAWAY\",\"customerName\":\"Client Test\",\"lines\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownOrderNumberReturns404() throws Exception {
        Setup setup = setupRestaurant(true);

        mockMvc.perform(get("/api/public/restaurants/" + setup.code() + "/orders/A9999"))
                .andExpect(status().isNotFound());
    }
}
