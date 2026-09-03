package com.qrmenu.menu;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Studio de design : choix du preset, personnalisation PREMIUM, aperçu live et cohérence
 * entre l'aperçu et la page publique.
 *
 * Les deux invariants que ces tests protègent :
 * <ol>
 *   <li>choisir un style ne publie rien et ne touche pas au contenu ;</li>
 *   <li>l'aperçu ne peut pas montrer un rendu que la publication ne produirait pas —
 *       en particulier, la personnalisation reste ignorée hors PREMIUM même en aperçu.</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuDesignTest {

    /** PNG 1x1 valide : seule la signature compte pour MediaService. */
    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    private static final String MENU_JSON = """
            {
              "categories": [
                {
                  "name": "Burgers",
                  "items": [ { "name": "Cheeseburger", "price": 1290, "currency": "EUR" } ]
                }
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    // ---------------------------------------------------------------- fixtures

    private String createClient(String offer) throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Design " + System.nanoTime() + "\",\"offer\":\"" + offer + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    /** Le QR est créé automatiquement à la création du restaurant (createClient) : on le lit, on n'en crée pas un second. */
    private String fetchQrCode(String restaurantId) throws Exception {
        String body = mockMvc.perform(get("/api/admin/restaurants/" + restaurantId + "/qr-codes")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].code");
    }

    private String uploadImage(String restaurantId) throws Exception {
        String body = mockMvc.perform(multipart("/api/admin/restaurants/" + restaurantId + "/images")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", PNG))
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.assetId");
    }

    private void saveMenu(String restaurantId) throws Exception {
        mockMvc.perform(put("/api/admin/restaurants/" + restaurantId + "/menu")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MENU_JSON))
                .andExpect(status().isOk());
    }

    private String designUrl(String restaurantId) {
        return "/api/admin/restaurants/" + restaurantId + "/menu/design";
    }

    private String previewUrl(String restaurantId) {
        return "/api/admin/restaurants/" + restaurantId + "/menu/preview";
    }

    // ---------------------------------------------------------------- lecture

    @Test
    void newClientStartsOnTheDefaultPresetWithTheFullCatalogue() throws Exception {
        String id = createClient("PRO");

        mockMvc.perform(get(designUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preset").value("MODERN"))
                .andExpect(jsonPath("$.offer").value("PRO"))
                .andExpect(jsonPath("$.customizable").value(false))
                .andExpect(jsonPath("$.presets.length()").value(5))
                .andExpect(jsonPath("$.presets[0].id").value("MODERN"))
                .andExpect(jsonPath("$.presets[0].accent").value("#F05A00"));
    }

    @Test
    void premiumClientIsFlaggedAsCustomizable() throws Exception {
        String id = createClient("PREMIUM");

        mockMvc.perform(get(designUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customizable").value(true));
    }

    // ---------------------------------------------------------------- écriture

    @Test
    void savingAPresetPersistsItWithoutTouchingTheContent() throws Exception {
        String id = createClient("PRO");
        saveMenu(id);

        mockMvc.perform(put(designUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"LUXE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preset").value("LUXE"));

        // Relecture : c'est bien la base qui fait foi, pas la réponse précédente.
        mockMvc.perform(get(designUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.preset").value("LUXE"));

        mockMvc.perform(get("/api/admin/restaurants/" + id + "/menu")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.structure.categories.length()").value(1))
                .andExpect(jsonPath("$.structure.categories[0].items[0].price").value(1290));
    }

    @Test
    void savingADesignNeverPublishesTheMenu() throws Exception {
        String id = createClient("PRO");
        saveMenu(id);

        mockMvc.perform(put(designUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"DARK\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/restaurants/" + id + "/menu")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void designCanBeChosenBeforeAnyContentExists() throws Exception {
        String id = createClient("PRO");

        mockMvc.perform(put(designUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"MINIMAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preset").value("MINIMAL"));
    }

    @Test
    void premiumIdentityIsPersisted() throws Exception {
        String id = createClient("PREMIUM");
        String assetId = uploadImage(id);

        mockMvc.perform(put(designUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"DARK\",\"brandName\":\"Chez Karta\",\"primaryColor\":\"#00ff88\","
                                + "\"secondaryColor\":\"#101010\",\"logoAssetId\":\"" + assetId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customization.brandName").value("Chez Karta"))
                .andExpect(jsonPath("$.customization.primaryColor").value("#00FF88"))
                .andExpect(jsonPath("$.customization.logoAssetId").value(assetId))
                .andExpect(jsonPath("$.customization.logoUrl").value(containsString("/media/" + assetId)));
    }

    @Test
    void proCanChangePresetButItsIdentityIsNeverWritten() throws Exception {
        String id = createClient("PRO");

        mockMvc.perform(put(designUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"STREET_FOOD\",\"brandName\":\"Tentative\",\"primaryColor\":\"#00FF00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preset").value("STREET_FOOD"))
                .andExpect(jsonPath("$.customization.brandName").doesNotExist())
                .andExpect(jsonPath("$.customization.primaryColor").doesNotExist());
    }

    @Test
    void basicClientHasNoDesignStudio() throws Exception {
        String id = createClient("BASIC");

        mockMvc.perform(put(designUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"DARK\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsAnUnknownPresetAndAnInvalidColor() throws Exception {
        String id = createClient("PREMIUM");

        mockMvc.perform(put(designUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"NEON\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(designUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"DARK\",\"primaryColor\":\"rouge\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(designUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"brandName\":\"Sans preset\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsALogoBelongingToAnotherClient() throws Exception {
        String owner = createClient("PREMIUM");
        String other = createClient("PREMIUM");
        String assetId = uploadImage(owner);

        mockMvc.perform(put(designUrl(other))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"LUXE\",\"logoAssetId\":\"" + assetId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void designEndpointsRequireAuthentication() throws Exception {
        String id = createClient("PRO");

        mockMvc.perform(get(designUrl(id))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(previewUrl(id))).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- aperçu live

    @Test
    void previewFollowsThePresetPassedInTheQueryWithoutSavingIt() throws Exception {
        String id = createClient("PRO");
        saveMenu(id);

        // Fond sombre du preset DARK, alors que le design enregistré est MODERN (clair).
        mockMvc.perform(get(previewUrl(id)).param("preset", "DARK")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--t-bg:#131312")))
                .andExpect(content().string(containsString("Aperçu — ce menu n'est pas publié")));

        // Rien n'a été écrit : le design enregistré est toujours MODERN.
        mockMvc.perform(get(designUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.preset").value("MODERN"));
    }

    @Test
    void everyPresetRendersWithItsOwnPalette() throws Exception {
        String id = createClient("PRO");
        saveMenu(id);

        assertPreviewUses(id, "MODERN", "--t-bg:#FFFFFF", "--t-accent:#F05A00");
        assertPreviewUses(id, "DARK", "--t-bg:#131312", "--t-accent:#012FA4");
        assertPreviewUses(id, "STREET_FOOD", "--t-bg:#131312", "--t-accent:#DC2626");
        assertPreviewUses(id, "MINIMAL", "--t-bg:#FFFFFF", "--t-accent:#131312");
        assertPreviewUses(id, "LUXE", "--t-bg:#131312", "--t-accent:#C9A96E");
    }

    private void assertPreviewUses(String id, String preset, String background, String accent) throws Exception {
        mockMvc.perform(get(previewUrl(id)).param("preset", preset)
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(background)))
                .andExpect(content().string(containsString(accent)));
    }

    @Test
    void previewIgnoresIdentityOverridesForANonPremiumClient() throws Exception {
        String id = createClient("PRO");
        saveMenu(id);

        // Un PRO qui forcerait ces paramètres verrait sinon un rendu qu'il ne peut pas publier.
        mockMvc.perform(get(previewUrl(id))
                        .param("preset", "MODERN")
                        .param("brandName", "Marque interdite")
                        .param("primaryColor", "#00FF00")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Marque interdite"))))
                .andExpect(content().string(containsString("--t-accent:#F05A00")));
    }

    @Test
    void previewAppliesIdentityOverridesForAPremiumClient() throws Exception {
        String id = createClient("PREMIUM");
        saveMenu(id);

        mockMvc.perform(get(previewUrl(id))
                        .param("preset", "MODERN")
                        .param("brandName", "Chez Karta")
                        .param("primaryColor", "#00FF88")
                        .param("secondaryColor", "#101010")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Chez Karta")))
                .andExpect(content().string(containsString("--t-accent:#00FF88")))
                .andExpect(content().string(containsString("--t-bg:#101010")))
                // Fond sombre imposé : le texte est redérivé pour rester lisible.
                .andExpect(content().string(containsString("--t-text:#FFFFFF")));
    }

    @Test
    void previewWorksBeforeAnyCategoryExists() throws Exception {
        String id = createClient("PRO");

        // Le studio doit être utilisable dès la première visite, sans carte saisie.
        mockMvc.perform(get(previewUrl(id)).param("preset", "LUXE")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("density-elegant")))
                .andExpect(content().string(containsString("Le menu sera disponible très prochainement")));
    }

    @Test
    void previewIsNeverCachedAndBasicClientsHaveNone() throws Exception {
        String pro = createClient("PRO");
        mockMvc.perform(get(previewUrl(pro)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Cache-Control", "no-store"));

        String basic = createClient("BASIC");
        mockMvc.perform(get(previewUrl(basic)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewSaysWhenTheCardIsAlreadyOnline() throws Exception {
        String id = createClient("PRO");
        saveMenu(id);

        mockMvc.perform(get(previewUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(content().string(containsString("Aperçu — ce menu n'est pas publié")));

        mockMvc.perform(put("/api/admin/restaurants/" + id + "/menu/publish")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk());

        // Annoncer « non publié » à un restaurateur dont la carte est en ligne serait faux.
        mockMvc.perform(get(previewUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(content().string(containsString("Aperçu — cette carte est en ligne")))
                .andExpect(content().string(not(containsString("ce menu n'est pas publié"))));
    }

    // ---------------------------------------------------------------- aperçu == public

    @Test
    void thePublishedPageUsesExactlyTheSavedDesign() throws Exception {
        String id = createClient("PREMIUM");
        String code = fetchQrCode(id);
        saveMenu(id);

        mockMvc.perform(put(designUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preset\":\"STREET_FOOD\",\"brandName\":\"Chez Karta\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/restaurants/" + id + "/menu/publish")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk());

        String publicHtml = mockMvc.perform(get("/m/{code}", code))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String previewHtml = mockMvc.perform(get(previewUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        org.assertj.core.api.Assertions.assertThat(publicHtml)
                .contains("--t-accent:#DC2626")
                .contains("density-compact")
                .contains("Chez Karta")
                .doesNotContain("Aperçu — ce menu n'est pas publié");

        // Même thème des deux côtés : l'aperçu ne peut pas mentir sur le rendu public.
        org.assertj.core.api.Assertions.assertThat(previewHtml)
                .contains("--t-accent:#DC2626")
                .contains("density-compact")
                .contains("Chez Karta");
    }
}
