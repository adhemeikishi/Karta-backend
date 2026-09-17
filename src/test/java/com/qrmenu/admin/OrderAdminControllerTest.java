package com.qrmenu.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Commandes Karta Pay d'un client : {@code /api/admin/restaurants/{id}/orders}. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;

    private record Setup(String restaurantId, String code, String itemId) {
    }

    private Setup setupPayingRestaurant() throws Exception {
        String restaurantBody = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Commandes " + System.nanoTime() + "\",\"offer\":\"PRO\"}"))
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
                                {"categories":[{"name":"Burgers","items":[
                                  {"name":"Cheeseburger","price":1290,"currency":"EUR"}]}]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(menuBody, "$.structure.categories[0].items[0].id");

        return new Setup(restaurantId, code, itemId);
    }

    private void createOrder(Setup setup) throws Exception {
        mockMvc.perform(post("/api/public/restaurants/" + setup.code() + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fulfillmentType\":\"TAKEAWAY\",\"customerName\":\"Client Test\","
                                + "\"lines\":[{\"itemId\":\"" + setup.itemId() + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants/" + UUID.randomUUID() + "/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsAndReadsBackAnOrder() throws Exception {
        Setup setup = setupPayingRestaurant();
        createOrder(setup);

        String listBody = mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/orders")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(listBody, "$[0].id");

        mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/orders/" + orderId)
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].itemNameSnapshot").value("Cheeseburger"));
    }

    @Test
    void filtersListByStatus() throws Exception {
        Setup setup = setupPayingRestaurant();
        createOrder(setup);

        mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/orders?status=PENDING")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/orders?status=COMPLETED")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updatesStatusFollowingTheAllowedTransitions() throws Exception {
        Setup setup = setupPayingRestaurant();
        createOrder(setup);
        String orderId = firstOrderId(setup);

        mockMvc.perform(put("/api/admin/restaurants/" + setup.restaurantId() + "/orders/" + orderId + "/status")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void rejectsSkippingAStatusStep() throws Exception {
        Setup setup = setupPayingRestaurant();
        createOrder(setup);
        String orderId = firstOrderId(setup);

        mockMvc.perform(put("/api/admin/restaurants/" + setup.restaurantId() + "/orders/" + orderId + "/status")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownOrderReturns404() throws Exception {
        Setup setup = setupPayingRestaurant();

        mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/orders/" + UUID.randomUUID())
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }

    private String firstOrderId(Setup setup) throws Exception {
        String listBody = mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/orders")
                        .with(httpBasic("admin", "test-password")))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(listBody, "$[0].id");
    }

    /** Recule {@code created_at} directement en base : la commande est fixée à la création, aucun setter de domaine. */
    private void backdateOrder(String orderId, int daysAgo) {
        OffsetDateTime when = LocalDate.now(ZoneId.systemDefault())
                .minusDays(daysAgo)
                .atTime(12, 0)
                .atZone(ZoneId.systemDefault())
                .toOffsetDateTime();
        jdbc.update("UPDATE orders SET created_at = ? WHERE id = ?",
                Timestamp.from(when.toInstant()), UUID.fromString(orderId));
    }

    @Test
    void defaultListOnlyShowsTodaysOrdersButAllHistoryIncludesYesterday() throws Exception {
        Setup setup = setupPayingRestaurant();
        createOrder(setup);
        String yesterdayOrderId = firstOrderId(setup);
        backdateOrder(yesterdayOrderId, 1);

        createOrder(setup);

        mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/orders")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(org.hamcrest.Matchers.not(yesterdayOrderId)));

        mockMvc.perform(get("/api/admin/restaurants/" + setup.restaurantId() + "/orders?all=true")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
