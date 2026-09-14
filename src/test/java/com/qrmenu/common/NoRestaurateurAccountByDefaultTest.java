package com.qrmenu.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Configuration par défaut — aucune propriété {@code restaurateur.*}, c'est exactement
 * l'état de la production.
 *
 * <p>C'est le test qui protège réellement : ajouter la possibilité d'un compte
 * restaurateur ne doit pas en créer un. Un second compte apparaissant sans qu'on l'ait
 * demandé serait une porte ouverte silencieuse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoRestaurateurAccountByDefaultTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void noRestaurateurAccountExists() throws Exception {
        mockMvc.perform(get("/api/admin/me")
                        .with(httpBasic("resto@karta.local", "test-only-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorIsUnaffected() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/restaurants").with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk());
    }

    @Test
    void adminApiRemainsClosedToAnonymousCallers() throws Exception {
        for (String path : new String[]{
                "/api/admin/me",
                "/api/admin/dashboard",
                "/api/admin/restaurants",
        }) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    /**
     * Le mot de passe de test ne doit exister que dans l'environnement, jamais dans le
     * dépôt : la propriété livrée n'a pas de valeur par défaut. C'est ce qui garantit à
     * la fois l'absence de secret dans Git et l'absence de compte en production.
     */
    @Test
    void devConfigurationShipsNoRestaurateurPassword() throws IOException {
        String devConfig = new String(
                new ClassPathResource("application-dev.yml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(devConfig).contains("password: ${RESTAURATEUR_PASSWORD:}");
        assertThat(devConfig).doesNotContain("KartaTest");
    }
}
