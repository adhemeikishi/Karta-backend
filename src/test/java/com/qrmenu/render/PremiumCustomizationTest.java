package com.qrmenu.render;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Personnalisation PREMIUM de bout en bout : branding, typographie, traductions, photos,
 * QR. L'invariant protégé partout : ces réglages sont <strong>réservés à PREMIUM</strong>
 * — un client PRO peut les envoyer, ils ne sont ni rendus, ni publiés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PremiumCustomizationTest {

    private static final byte[] PNG = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    private static final String TRANSLATED_MENU = """
            {
              "languages": ["en", "zh", "xx"],
              "categories": [
                {
                  "name": "Entrées",
                  "translations": { "en": { "name": "Starters" }, "fr": { "name": "ignoré" } },
                  "items": [
                    {
                      "name": "Soupe du jour", "description": "Légumes de saison", "price": 750, "currency": "EUR",
                      "translations": { "en": { "name": "Soup of the day", "description": "Seasonal vegetables" },
                                        "zh": { "name": "例汤" } }
                    }
                  ]
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
                        .content("{\"name\":\"Resto Premium " + System.nanoTime() + "\",\"offer\":\"" + offer + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private String qrCodeOf(String restaurantId) throws Exception {
        String body = mockMvc.perform(get("/api/admin/restaurants/" + restaurantId + "/qr-codes")
                        .with(httpBasic("admin", "test-password")))
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

    private void saveMenu(String restaurantId, String json) throws Exception {
        mockMvc.perform(put("/api/admin/restaurants/" + restaurantId + "/menu")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private void publish(String restaurantId) throws Exception {
        mockMvc.perform(put("/api/admin/restaurants/" + restaurantId + "/menu/publish")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk());
    }

    private String saveDesign(String restaurantId, String json, int expectedStatus) throws Exception {
        return mockMvc.perform(put("/api/admin/restaurants/" + restaurantId + "/menu/design")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private static String simpleMenu() {
        return "{\"categories\":[{\"name\":\"Plats\",\"items\":[{\"name\":\"Steak\",\"price\":1900}]}]}";
    }

    // ---------------------------------------------------------------- branding + typographie

    @Test
    void premiumCanRemoveKartaBrandingFromTheCardAndTheQr() throws Exception {
        String id = createClient("PREMIUM");
        saveMenu(id, simpleMenu());
        saveDesign(id, "{\"preset\":\"MODERN\",\"hideBranding\":true}", 200);
        publish(id);

        mockMvc.perform(get("/m/{code}", qrCodeOf(id)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<b>Karta</b>"))));

        // QR sans bande de mention : image carrée.
        byte[] png = mockMvc.perform(get("/api/admin/restaurants/" + id + "/qr-code/image.png")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getHeight()).isEqualTo(image.getWidth());

        String svg = mockMvc.perform(get("/api/admin/restaurants/" + id + "/qr-code/image.svg")
                        .with(httpBasic("admin", "test-password")))
                .andReturn().getResponse().getContentAsString();
        assertThat(svg).doesNotContain("kartaqr.fr");
    }

    @Test
    void proKeepsKartaBrandingEvenIfItAsksToHideIt() throws Exception {
        String id = createClient("PRO");
        saveMenu(id, simpleMenu());
        saveDesign(id, "{\"preset\":\"MODERN\",\"hideBranding\":true,\"font\":\"LORA\"}", 200);
        publish(id);

        mockMvc.perform(get("/api/admin/restaurants/" + id + "/menu/design")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.customization.hideBranding").value(false))
                .andExpect(jsonPath("$.customization.font").isEmpty());

        mockMvc.perform(get("/m/{code}", qrCodeOf(id)))
                .andExpect(content().string(containsString("<b>Karta</b>")))
                .andExpect(content().string(not(containsString("fonts.googleapis.com"))));

        String svg = mockMvc.perform(get("/api/admin/restaurants/" + id + "/qr-code/image.svg")
                        .with(httpBasic("admin", "test-password")))
                .andReturn().getResponse().getContentAsString();
        assertThat(svg).contains("kartaqr.fr");
    }

    @Test
    void premiumFontIsLoadedOnThePublicCardAndNowhereElseByDefault() throws Exception {
        String id = createClient("PREMIUM");
        saveMenu(id, simpleMenu());
        publish(id);

        mockMvc.perform(get("/m/{code}", qrCodeOf(id)))
                .andExpect(content().string(not(containsString("fonts.googleapis.com"))));

        saveDesign(id, "{\"preset\":\"MODERN\",\"font\":\"PLAYFAIR_DISPLAY\"}", 200);

        mockMvc.perform(get("/m/{code}", qrCodeOf(id)))
                .andExpect(content().string(containsString("fonts.googleapis.com/css2?family=Playfair+Display")))
                .andExpect(content().string(containsString("--t-font:&quot;Playfair Display&quot;")));

        // L'aperçu suit une police non enregistrée, comme pour les couleurs.
        mockMvc.perform(get("/api/admin/restaurants/" + id + "/menu/preview")
                        .param("font", "LORA")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(content().string(containsString("family=Lora")));
    }

    // ---------------------------------------------------------------- traductions

    @Test
    void premiumMenuIsServedInTheRequestedLanguageWithFrenchFallback() throws Exception {
        String id = createClient("PREMIUM");
        saveMenu(id, TRANSLATED_MENU);
        publish(id);
        String code = qrCodeOf(id);

        // Langues activées : « xx » écarté, « fr » implicite.
        mockMvc.perform(get("/api/admin/restaurants/" + id + "/menu").with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.structure.languages[0]").value("en"))
                .andExpect(jsonPath("$.structure.languages[1]").value("zh"))
                .andExpect(jsonPath("$.structure.languages.length()").value(2))
                .andExpect(jsonPath("$.structure.categories[0].translations.en.name").value("Starters"))
                .andExpect(jsonPath("$.structure.categories[0].translations.fr").doesNotExist());

        mockMvc.perform(get("/m/{code}", code).param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"en\"")))
                .andExpect(content().string(containsString("Starters")))
                .andExpect(content().string(containsString("Soup of the day")))
                .andExpect(content().string(containsString("Seasonal vegetables")))
                .andExpect(content().string(containsString("?lang=zh")))
                .andExpect(content().string(containsString("?lang=fr")));

        // Chinois : le plat est traduit, la description et la catégorie retombent sur le français.
        mockMvc.perform(get("/m/{code}", code).param("lang", "zh"))
                .andExpect(content().string(containsString("例汤")))
                .andExpect(content().string(containsString("Légumes de saison")))
                .andExpect(content().string(containsString("Entrées")))
                .andExpect(content().string(containsString("菜单")));

        // Langue non activée : français, sans erreur.
        mockMvc.perform(get("/m/{code}", code).param("lang", "es"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soupe du jour")));

        mockMvc.perform(get("/api/public/menus/{code}", code).param("lang", "en"))
                .andExpect(jsonPath("$.language.code").value("en"))
                .andExpect(jsonPath("$.categories[0].items[0].name").value("Soup of the day"));
    }

    /** Conservées en base (rien n'est perdu à une rétrogradation), jamais rendues hors PREMIUM. */
    @Test
    void proTranslationsAreStoredButTheCardStaysFrench() throws Exception {
        String id = createClient("PRO");
        saveMenu(id, TRANSLATED_MENU);
        publish(id);

        mockMvc.perform(get("/api/admin/restaurants/" + id + "/menu").with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.structure.languages.length()").value(2))
                .andExpect(jsonPath("$.structure.categories[0].translations.en.name").value("Starters"));

        mockMvc.perform(get("/m/{code}", qrCodeOf(id)).param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Soupe du jour")))
                .andExpect(content().string(not(containsString("Soup of the day"))))
                .andExpect(content().string(not(containsString("?lang="))));
    }

    // ---------------------------------------------------------------- photos

    @Test
    void itemPhotosAreRenderedForPremiumOnly() throws Exception {
        for (String offer : new String[]{"PREMIUM", "PRO"}) {
            String id = createClient(offer);
            String assetId = uploadImage(id);
            saveMenu(id, "{\"categories\":[{\"name\":\"Plats\",\"items\":[{\"name\":\"Steak\",\"price\":1900,\"imageAssetId\":\""
                    + assetId + "\"}]}]}");
            publish(id);

            boolean premium = offer.equals("PREMIUM");
            // Stockée quelle que soit l'offre (comportement existant), rendue pour PREMIUM seulement.
            mockMvc.perform(get("/api/admin/restaurants/" + id + "/menu").with(httpBasic("admin", "test-password")))
                    .andExpect(jsonPath("$.structure.categories[0].items[0].imageAssetId").value(assetId));

            mockMvc.perform(get("/api/public/menus/{code}", qrCodeOf(id)))
                    .andExpect(jsonPath("$.categories[0].items[0].imageUrl")
                            .value(premium ? org.hamcrest.Matchers.containsString(assetId) : org.hamcrest.Matchers.nullValue()));
        }
    }

    // ---------------------------------------------------------------- QR

    @Test
    void premiumQrDesignIsPersistedRenderedAndKeptScannable() throws Exception {
        String id = createClient("PREMIUM");
        String logo = uploadImage(id);

        saveDesign(id, "{\"preset\":\"MODERN\",\"qrFgColor\":\"#012fa4\",\"qrBgColor\":\"#f6f6f6\","
                + "\"qrModuleStyle\":\"DOTS\",\"qrEyeStyle\":\"CIRCLE\",\"qrLogoAssetId\":\"" + logo + "\"}", 200);

        mockMvc.perform(get("/api/admin/restaurants/" + id + "/menu/design").with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.customization.qr.fgColor").value("#012FA4"))
                .andExpect(jsonPath("$.customization.qr.moduleStyle").value("DOTS"))
                .andExpect(jsonPath("$.customization.qr.eyeStyle").value("CIRCLE"))
                .andExpect(jsonPath("$.customization.qr.logoUrl").value(containsString(logo)))
                .andExpect(jsonPath("$.fonts.length()").value(6));

        String svg = mockMvc.perform(get("/api/admin/restaurants/" + id + "/qr-code/image.svg")
                        .with(httpBasic("admin", "test-password")))
                .andReturn().getResponse().getContentAsString();
        assertThat(svg).contains("<circle").contains("#012fa4").contains("<image").contains("kartaqr.fr");

        // Aperçu : une surcharge non enregistrée, sans rien écrire.
        String preview = mockMvc.perform(get("/api/admin/restaurants/" + id + "/qr-code/image.svg")
                        .param("moduleStyle", "SQUARE").param("hideBranding", "true")
                        .with(httpBasic("admin", "test-password")))
                .andReturn().getResponse().getContentAsString();
        assertThat(preview).doesNotContain("kartaqr.fr").contains("crispEdges");
        mockMvc.perform(get("/api/admin/restaurants/" + id + "/menu/design").with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.customization.qr.moduleStyle").value("DOTS"));

        // Modules plus clairs que le fond : refusé, le QR ne se scannerait pas.
        String error = saveDesign(id, "{\"preset\":\"MODERN\",\"qrFgColor\":\"#FFFF00\",\"qrBgColor\":\"#FFFFFF\"}", 400);
        assertThat(error).contains("illisible");
    }

    @Test
    void proQrIsAlwaysTheDefaultOne() throws Exception {
        String id = createClient("PRO");
        saveDesign(id, "{\"preset\":\"MODERN\",\"qrFgColor\":\"#012FA4\",\"qrModuleStyle\":\"DOTS\"}", 200);

        String svg = mockMvc.perform(get("/api/admin/restaurants/" + id + "/qr-code/image.svg")
                        .param("moduleStyle", "DOTS").param("fgColor", "#012FA4")
                        .with(httpBasic("admin", "test-password")))
                .andReturn().getResponse().getContentAsString();
        assertThat(svg).doesNotContain("<circle").doesNotContain("#012fa4").contains("#000000").contains("kartaqr.fr");
    }
}
