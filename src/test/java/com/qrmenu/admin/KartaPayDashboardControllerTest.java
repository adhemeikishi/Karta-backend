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

import static org.hamcrest.Matchers.closeTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tableau de bord Karta Pay d'un client : {@code /api/admin/restaurants/{id}/karta-pay/dashboard}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KartaPayDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private record Setup(String restaurantId, String code, String burgerId, String friesId) {
    }

    private Setup setupPayingRestaurant() throws Exception {
        String restaurantBody = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Dashboard " + System.nanoTime() + "\",\"offer\":\"PRO\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String restaurantId = JsonPath.read(restaurantBody, "$.id");

        mockMvc.perform(put("/api/admin/restaurants/" + restaurantId + "/karta-pay")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        String qrBody = mockMvc.perform(get("/api/admin/restaurants/" + restaurantId + "/qr-codes")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(qrBody, "$[0].code");

        String menuBody = mockMvc.perform(put("/api/admin/restaurants/" + restaurantId + "/menu")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories":[{"name":"Plats","items":[
                                  {"name":"Cheeseburger","price":1000,"currency":"EUR"},
                                  {"name":"Frites","price":300,"currency":"EUR"}]}]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String burgerId = JsonPath.read(menuBody, "$.structure.categories[0].items[0].id");
        String friesId = JsonPath.read(menuBody, "$.structure.categories[0].items[1].id");

        return new Setup(restaurantId, code, burgerId, friesId);
    }

    private String createOrder(Setup setup, String itemId, int quantity) throws Exception {
        String body = mockMvc.perform(post("/api/public/restaurants/" + setup.code() + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fulfillmentType\":\"TAKEAWAY\",\"customerName\":\"Client Test\","
                                + "\"lines\":[{\"itemId\":\"" + itemId + "\",\"quantity\":" + quantity + "}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.orderNumber");
    }

    private String orderIdFor(Setup setup, String orderNumber) throws Exception {
        String listBody = mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/orders")
                        .with(httpBasic("admin", "test-password")))
                .andReturn().getResponse().getContentAsString();
        int count = JsonPath.read(listBody, "$.length()");
        for (int i = 0; i < count; i++) {
            String number = JsonPath.read(listBody, "$[" + i + "].orderNumber");
            if (number.equals(orderNumber)) {
                return JsonPath.read(listBody, "$[" + i + "].id");
            }
        }
        throw new IllegalStateException("Commande introuvable: " + orderNumber);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants/" + UUID.randomUUID() + "/karta-pay/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRestaurantReturns404() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants/" + UUID.randomUUID() + "/karta-pay/dashboard")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void withNoOrdersEverythingIsEmpty() throws Exception {
        Setup setup = setupPayingRestaurant();

        mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/karta-pay/dashboard")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue.todayCents").value(0))
                .andExpect(jsonPath("$.revenue.thisWeekCents").value(0))
                .andExpect(jsonPath("$.revenue.thisMonthCents").value(0))
                .andExpect(jsonPath("$.topItems.length()").value(0))
                .andExpect(jsonPath("$.revenueBreakdown.length()").value(0))
                .andExpect(jsonPath("$.avgBasketCents").value(0))
                .andExpect(jsonPath("$.peakHours.length()").value(24))
                .andExpect(jsonPath("$.qrScans.total").value(0));
    }

    @Test
    void aggregatesRevenueTopItemsAndAverageBasketExcludingCancelledOrders() throws Exception {
        Setup setup = setupPayingRestaurant();

        createOrder(setup, setup.burgerId(), 2); // 2000 cents
        createOrder(setup, setup.friesId(), 1);  // 300 cents
        String cancelledOrderNumber = createOrder(setup, setup.burgerId(), 5); // 5000 cents, annulée ensuite

        String cancelledOrderId = orderIdFor(setup, cancelledOrderNumber);
        mockMvc.perform(put("/api/admin/restaurants/" + setup.restaurantId() + "/orders/" + cancelledOrderId + "/status")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/karta-pay/dashboard")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                // 2000 + 300 = 2300 ; la commande annulée (5000) est exclue.
                .andExpect(jsonPath("$.revenue.todayCents").value(2300))
                .andExpect(jsonPath("$.revenue.thisWeekCents").value(2300))
                .andExpect(jsonPath("$.revenue.thisMonthCents").value(2300))
                .andExpect(jsonPath("$.avgBasketCents").value(1150)) // 2300 / 2 commandes
                .andExpect(jsonPath("$.topItems.length()").value(2))
                .andExpect(jsonPath("$.topItems[0].name").value("Cheeseburger"))
                .andExpect(jsonPath("$.topItems[0].quantity").value(2))
                .andExpect(jsonPath("$.topItems[0].revenueCents").value(2000))
                .andExpect(jsonPath("$.topItems[1].name").value("Frites"))
                .andExpect(jsonPath("$.topItems[1].revenueCents").value(300))
                .andExpect(jsonPath("$.revenueBreakdown.length()").value(2))
                .andExpect(jsonPath("$.revenueBreakdown[0].name").value("Cheeseburger"))
                .andExpect(jsonPath("$.revenueBreakdown[0].percentage").value(closeTo(86.96, 0.1)))
                .andExpect(jsonPath("$.revenueBreakdown[1].percentage").value(closeTo(13.04, 0.1)));
    }
}
