package com.qrmenu.account;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inscription libre-service ({@code POST /api/public/signup}) et isolation multi-comptes.
 *
 * <p>Contrairement à {@code RestaurateurScopeTest} (compte restaurateur historique, unique,
 * configuré par variables d'environnement), ce test couvre des comptes créés dynamiquement,
 * en base — le vrai scénario multi-tenant que l'inscription rend possible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return httpBasic("admin", "test-password");
    }

    private String signup(String email, String password, String restaurantName) throws Exception {
        return mockMvc.perform(post("/api/public/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                                + "\",\"restaurantName\":\"" + restaurantName + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void signupCreatesAccountRestaurantAndQrWithoutActiveSubscription() throws Exception {
        String email = "nouveau" + System.nanoTime() + "@example.com";
        String body = signup(email, "un-mot-de-passe", "Le Nouveau Restaurant");
        String restaurantId = JsonPath.read(body, "$.restaurantId");

        // Le restaurant existe, offre PRO, sans abonnement actif, onboarding non fait.
        mockMvc.perform(get("/api/admin/restaurants/" + restaurantId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer").value("PRO"))
                .andExpect(jsonPath("$.subscriptionActive").value(false))
                .andExpect(jsonPath("$.onboardingCompletedAt").doesNotExist());

        // Le QR permanent a été généré à l'inscription, comme pour une création admin.
        mockMvc.perform(get("/api/admin/restaurants/" + restaurantId + "/qr-codes").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Le compte peut se connecter immédiatement avec les identifiants soumis.
        mockMvc.perform(get("/api/admin/me").with(httpBasic(email, "un-mot-de-passe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(email))
                .andExpect(jsonPath("$.role").value("RESTAURATEUR"))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId));
    }

    @Test
    void duplicateEmailIsRejectedWithConflict() throws Exception {
        String email = "double" + System.nanoTime() + "@example.com";
        signup(email, "un-mot-de-passe", "Premier Restaurant");

        mockMvc.perform(post("/api/public/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"un-autre-mdp\","
                                + "\"restaurantName\":\"Second Restaurant\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void tooShortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/public/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"court" + System.nanoTime() + "@example.com\","
                                + "\"password\":\"1234567\",\"restaurantName\":\"Resto\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupEndpointRequiresNoAuthentication() throws Exception {
        // Route publique : aucun en-tête Authorization, contrairement à /api/admin/**.
        mockMvc.perform(post("/api/public/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"public" + System.nanoTime() + "@example.com\","
                                + "\"password\":\"un-mot-de-passe\",\"restaurantName\":\"Resto\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void twoSignedUpAccountsAreIsolatedFromEachOther() throws Exception {
        String emailA = "a" + System.nanoTime() + "@example.com";
        String emailB = "b" + System.nanoTime() + "@example.com";
        String bodyA = signup(emailA, "mot-de-passe-a", "Restaurant A");
        String bodyB = signup(emailB, "mot-de-passe-b", "Restaurant B");
        String restaurantIdA = JsonPath.read(bodyA, "$.restaurantId");
        String restaurantIdB = JsonPath.read(bodyB, "$.restaurantId");

        // A atteint son propre restaurant...
        mockMvc.perform(get("/api/admin/restaurants/" + restaurantIdA)
                        .with(httpBasic(emailA, "mot-de-passe-a")))
                .andExpect(status().isOk());

        // ...mais pas celui de B, même en changeant l'identifiant dans l'URL.
        mockMvc.perform(get("/api/admin/restaurants/" + restaurantIdB)
                        .with(httpBasic(emailA, "mot-de-passe-a")))
                .andExpect(status().isForbidden());

        // Et réciproquement.
        mockMvc.perform(get("/api/admin/restaurants/" + restaurantIdA)
                        .with(httpBasic(emailB, "mot-de-passe-b")))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorAccountIsUnaffectedBySignup() throws Exception {
        signup("verif-admin" + System.nanoTime() + "@example.com", "un-mot-de-passe", "Resto Verif");

        mockMvc.perform(get("/api/admin/restaurants").with(admin()))
                .andExpect(status().isOk());
    }
}
