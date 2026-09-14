package com.qrmenu.common;

import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
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
 * Isolation du compte restaurateur, vérifiée côté serveur.
 *
 * <p>Le test qui compte est {@link #cannotReachAnotherRestaurantByChangingTheUrl()} : un
 * restaurateur authentifié qui remplace l'UUID dans l'URL par celui d'un autre client ne
 * doit rien obtenir. Tout le reste — le rôle, le filtre, la configuration — n'existe que
 * pour ça.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "restaurateur.username=resto@karta.local",
        "restaurateur.password=test-only-password",
        "restaurateur.restaurant-id=11111111-1111-4111-8111-111111111111",
})
class RestaurateurScopeTest {

    private static final UUID OWNED = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantRepository restaurantRepository;

    private UUID otherRestaurantId;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor resto() {
        return httpBasic("resto@karta.local", "test-only-password");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin", "test-password");
    }

    @BeforeEach
    void seedRestaurants() {
        if (restaurantRepository.findById(OWNED).isEmpty()) {
            restaurantRepository.save(new Restaurant(OWNED, "Restaurant Test", RestaurantOffer.PRO));
        }
        otherRestaurantId = restaurantRepository
                .save(new Restaurant("Restaurant d'un autre client", RestaurantOffer.PRO))
                .getId();
    }

    // ------------------------------------------------------------------ isolation

    @Test
    void cannotReachAnotherRestaurantByChangingTheUrl() throws Exception {
        for (String path : new String[]{
                "/api/admin/restaurants/" + otherRestaurantId,
                "/api/admin/restaurants/" + otherRestaurantId + "/menu",
                "/api/admin/restaurants/" + otherRestaurantId + "/menu/design",
                "/api/admin/restaurants/" + otherRestaurantId + "/qr-codes",
                "/api/admin/restaurants/" + otherRestaurantId + "/stats",
        }) {
            mockMvc.perform(get(path).with(resto()))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void cannotWriteIntoAnotherRestaurantMenu() throws Exception {
        mockMvc.perform(put("/api/admin/restaurants/" + otherRestaurantId + "/menu/publish")
                        .with(resto()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotSeeTheOtherClientsAtAll() throws Exception {
        // La liste et le tableau de bord exposeraient les autres restaurants.
        mockMvc.perform(get("/api/admin/restaurants").with(resto()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/dashboard").with(resto()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotPerformKartaCommercialActions() throws Exception {
        mockMvc.perform(post("/api/admin/restaurants").with(resto())
                        .contentType("application/json")
                        .content("{\"name\":\"Le mien aussi\",\"offer\":\"PRO\"}"))
                .andExpect(status().isForbidden());

        // Même sur SON restaurant : renommer, changer d'offre et supprimer sont des
        // actions de Karta, pas du restaurateur.
        mockMvc.perform(put("/api/admin/restaurants/" + OWNED).with(resto())
                        .contentType("application/json")
                        .content("{\"name\":\"Nouveau nom\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/restaurants/" + OWNED + "/offer").with(resto())
                        .contentType("application/json")
                        .content("{\"offer\":\"PREMIUM\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/restaurants/" + OWNED).with(resto()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotReachQrCodeEndpointsWhoseOwnerIsNotInTheUrl() throws Exception {
        mockMvc.perform(get("/api/admin/qr-codes/" + UUID.randomUUID()).with(resto()))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ son périmètre

    @Test
    void reachesItsOwnRestaurantAndMenu() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants/" + OWNED).with(resto()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer").value("PRO"));

        // Phase 1 (Ma carte) et Phase 2 (Apparence).
        mockMvc.perform(get("/api/admin/restaurants/" + OWNED + "/menu").with(resto()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/restaurants/" + OWNED + "/menu/design").with(resto()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.presets.length()").value(5));
    }

    @Test
    void savesItsOwnDesignWithoutPublishing() throws Exception {
        mockMvc.perform(put("/api/admin/restaurants/" + OWNED + "/menu/design").with(resto())
                        .contentType("application/json")
                        .content("{\"preset\":\"LUXE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preset").value("LUXE"));

        mockMvc.perform(get("/api/admin/restaurants/" + OWNED + "/menu").with(resto()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));
    }

    // ------------------------------------------------------------------ identité

    @Test
    void identityTellsTheFrontendWhereToGo() throws Exception {
        mockMvc.perform(get("/api/admin/me").with(resto()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("resto@karta.local"))
                .andExpect(jsonPath("$.role").value("RESTAURATEUR"))
                .andExpect(jsonPath("$.restaurantId").value(OWNED.toString()));
    }

    @Test
    void identityOfAnAdministratorCarriesNoRestaurant() throws Exception {
        mockMvc.perform(get("/api/admin/me").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.restaurantId").doesNotExist());
    }

    @Test
    void identityStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/me")
                        .with(httpBasic("resto@karta.local", "mauvais")))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ non-régression admin

    @Test
    void administratorIsNeverRestricted() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/restaurants").with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/restaurants/" + OWNED).with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/restaurants/" + otherRestaurantId).with(admin()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/restaurants/" + otherRestaurantId + "/offer").with(admin())
                        .contentType("application/json")
                        .content("{\"offer\":\"PREMIUM\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void publicRoutesAreUnaffected() throws Exception {
        // Le filtre ne doit pas gêner ce qui n'est pas sous /api/admin.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
