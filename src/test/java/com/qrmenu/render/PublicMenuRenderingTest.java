package com.qrmenu.render;

import com.qrmenu.menu.MenuDtos.SaveCategoryRequest;
import com.qrmenu.menu.MenuDtos.SaveItemRequest;
import com.qrmenu.menu.MenuService;
import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeService;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rendu HTML public du menu structuré et règle de diffusion :
 * seul un menu PUBLISHED est visible, un brouillon ne fuit jamais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicMenuRenderingTest {

    private static final byte[] VALID_PDF = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MenuService menuService;
    @Autowired
    private RestaurantService restaurantService;
    @Autowired
    private QrCodeService qrCodeService;

    private record Fixture(Restaurant restaurant, QrCode qr) {
    }

    private Fixture proClient() {
        Restaurant restaurant = restaurantService.create("Le Bistrot " + System.nanoTime(), RestaurantOffer.PRO);
        QrCode qr = qrCodeService.create(restaurant.getId(), "QR", "https://example.com/fallback");
        return new Fixture(restaurant, qr);
    }

    private static SaveItemRequest item(String name, String description, int price, boolean available) {
        return new SaveItemRequest(null, name, description, price, "EUR", null, null, available);
    }

    private static SaveCategoryRequest category(String name, int sortOrder, boolean visible, SaveItemRequest... items) {
        return new SaveCategoryRequest(null, name, null, sortOrder, visible, List.of(items));
    }

    /** Une carte complète : deux catégories visibles, une masquée, un plat indisponible. */
    private Fixture publishedMenu() {
        Fixture fixture = proClient();
        menuService.saveStructure(fixture.restaurant().getId(), List.of(
                category("Desserts", 5, true,
                        item("Tiramisu", null, 600, true)),
                category("Burgers", 1, true,
                        item("Cheeseburger", "Steak, cheddar, salade", 1290, true),
                        item("Veggie", null, 1190, false)),
                category("Secret du chef", 9, false,
                        item("Plat cache", null, 9900, true))));
        menuService.publish(fixture.restaurant().getId());
        return fixture;
    }

    // ------------------------------------------------------------ accès public

    @Test
    void publishedStructuredMenuIsPubliclyRendered() throws Exception {
        Fixture fixture = publishedMenu();

        mockMvc.perform(get("/m/{code}", fixture.qr().getCode()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString(fixture.restaurant().getName())))
                .andExpect(content().string(containsString("Cheeseburger")))
                .andExpect(content().string(containsString("Steak, cheddar, salade")))
                .andExpect(content().string(containsString("12,90")));
    }

    @Test
    void draftMenuIsNeverPublic() throws Exception {
        Fixture fixture = proClient();
        menuService.createMenu(fixture.restaurant().getId());

        mockMvc.perform(get("/m/{code}", fixture.qr().getCode()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Menu indisponible")));
    }

    @Test
    void readyMenuIsNeverPublicAndItsContentDoesNotLeak() throws Exception {
        Fixture fixture = proClient();
        menuService.saveStructure(fixture.restaurant().getId(), List.of(
                category("Burgers", 1, true, item("Cheeseburger", "Secret", 1290, true))));

        mockMvc.perform(get("/m/{code}", fixture.qr().getCode()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Cheeseburger"))))
                .andExpect(content().string(not(containsString("Secret"))));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mockMvc.perform(get("/m/{code}", "DOESNOTEXIST"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Menu indisponible")));
    }

    @Test
    void deactivatedQrCodeHidesThePublicMenu() throws Exception {
        Fixture fixture = publishedMenu();
        qrCodeService.deactivate(fixture.qr().getId());

        mockMvc.perform(get("/m/{code}", fixture.qr().getCode()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Cheeseburger"))));
    }

    // ------------------------------------------------------------ contenu rendu

    @Test
    void categoriesAndItemsAreRenderedInSortOrder() throws Exception {
        Fixture fixture = publishedMenu();

        String html = mockMvc.perform(get("/m/{code}", fixture.qr().getCode()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Burgers (sortOrder 1) avant Desserts (sortOrder 5), malgré l'ordre d'envoi.
        assertThat(html.indexOf("Burgers")).isLessThan(html.indexOf("Desserts"));
        // Cheeseburger (sortOrder 0) avant Veggie (sortOrder 1).
        assertThat(html.indexOf("Cheeseburger")).isLessThan(html.indexOf("Veggie"));
    }

    @Test
    void hiddenCategoryIsAbsentFromTheHtmlSource() throws Exception {
        Fixture fixture = publishedMenu();

        mockMvc.perform(get("/m/{code}", fixture.qr().getCode()))
                .andExpect(status().isOk())
                // Écartée côté serveur : elle n'est pas seulement masquée en CSS.
                .andExpect(content().string(not(containsString("Secret du chef"))))
                .andExpect(content().string(not(containsString("Plat cache"))));
    }

    @Test
    void unavailableItemStaysVisibleButIsFlagged() throws Exception {
        Fixture fixture = publishedMenu();

        mockMvc.perform(get("/m/{code}", fixture.qr().getCode()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Veggie")))
                .andExpect(content().string(containsString("Indisponible")))
                .andExpect(content().string(containsString("is-unavailable")));
    }

    @Test
    void emptyPublishedMenuRendersACleanPlaceholder() throws Exception {
        Fixture fixture = proClient();
        menuService.saveStructure(fixture.restaurant().getId(), List.of(
                category("Burgers", 1, true, item("Cheeseburger", null, 1290, true))));
        menuService.publish(fixture.restaurant().getId());
        // Le menu est vidé après publication : il reste publié, sans contenu.
        menuService.saveStructure(fixture.restaurant().getId(), List.of());

        mockMvc.perform(get("/m/{code}", fixture.qr().getCode()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("très prochainement")));
    }

    @Test
    void administratorSuppliedTextIsEscaped() throws Exception {
        Fixture fixture = proClient();
        menuService.saveStructure(fixture.restaurant().getId(), List.of(
                category("Burgers", 1, true,
                        item("<script>alert(1)</script>", null, 500, true))));
        menuService.publish(fixture.restaurant().getId());

        mockMvc.perform(get("/m/{code}", fixture.qr().getCode()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
                .andExpect(content().string(containsString("&lt;script&gt;")));
    }

    // ------------------------------------------------------------ API publique JSON

    @Test
    void publicApiExposesPricesInCentsAndNoAdminData() throws Exception {
        Fixture fixture = publishedMenu();

        mockMvc.perform(get("/api/public/menus/{code}", fixture.qr().getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value(fixture.restaurant().getName()))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.preset").value("classic"))
                .andExpect(jsonPath("$.categories.length()").value(2)) // la masquée est exclue
                .andExpect(jsonPath("$.categories[0].name").value("Burgers"))
                .andExpect(jsonPath("$.categories[0].items[0].price").value(1290))
                .andExpect(jsonPath("$.categories[0].items[0].currency").value("EUR"))
                .andExpect(jsonPath("$.categories[0].items[1].available").value(false))
                // Aucune donnée d'administration ne doit apparaître.
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.offer").doesNotExist())
                .andExpect(jsonPath("$.categories[0].id").doesNotExist())
                .andExpect(jsonPath("$.categories[0].items[0].id").doesNotExist());
    }

    @Test
    void publicApiRefusesAnUnpublishedMenu() throws Exception {
        Fixture fixture = proClient();
        menuService.saveStructure(fixture.restaurant().getId(), List.of(
                category("Burgers", 1, true, item("Cheeseburger", null, 1290, true))));

        mockMvc.perform(get("/api/public/menus/{code}", fixture.qr().getCode()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ QR et publication

    @Test
    void publishingPointsTheQrToTheHtmlMenuAndUnpublishRestoresFallback() throws Exception {
        Fixture fixture = publishedMenu();
        String code = fixture.qr().getCode();

        QrCode afterPublish = qrCodeService.getOrThrow(fixture.qr().getId());
        assertThat(afterPublish.getDestinationUrl()).endsWith("/m/" + code);

        mockMvc.perform(get("/q/{code}", code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", afterPublish.getDestinationUrl()));

        menuService.unpublish(fixture.restaurant().getId());

        QrCode afterUnpublish = qrCodeService.getOrThrow(fixture.qr().getId());
        assertThat(afterUnpublish.getDestinationUrl()).isEqualTo("https://example.com/fallback");
        mockMvc.perform(get("/m/{code}", code)).andExpect(status().isNotFound());
    }

    @Test
    void basicPdfFlowIsUnchanged() throws Exception {
        Restaurant basic = restaurantService.create("Snack " + System.nanoTime(), RestaurantOffer.BASIC);
        QrCode qr = qrCodeService.create(basic.getId(), "QR", "https://example.com/fallback");
        menuService.uploadPdf(basic.getId(), VALID_PDF, "application/pdf", "carte.pdf");
        menuService.publish(basic.getId());

        // Le QR pointe toujours vers le média, pas vers le renderer HTML.
        QrCode published = qrCodeService.getOrThrow(qr.getId());
        assertThat(published.getDestinationUrl()).contains("/media/");
        mockMvc.perform(get("/q/{code}", qr.getCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", published.getDestinationUrl()));

        // Et la page HTML n'existe pas pour un menu PDF.
        mockMvc.perform(get("/m/{code}", qr.getCode())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ aperçu back-office

    @Test
    void previewRendersADraftForTheAdministrator() throws Exception {
        Fixture fixture = proClient();
        menuService.saveStructure(fixture.restaurant().getId(), List.of(
                category("Burgers", 1, true, item("Cheeseburger", null, 1290, true))));

        mockMvc.perform(get("/api/admin/restaurants/{id}/menu/preview", fixture.restaurant().getId())
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cheeseburger")))
                .andExpect(content().string(containsString("Aperçu")));
    }

    @Test
    void previewRequiresAuthentication() throws Exception {
        Fixture fixture = proClient();
        menuService.createMenu(fixture.restaurant().getId());

        mockMvc.perform(get("/api/admin/restaurants/{id}/menu/preview", fixture.restaurant().getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicRenderNeverShowsThePreviewBanner() throws Exception {
        Fixture fixture = publishedMenu();

        mockMvc.perform(get("/m/{code}", fixture.qr().getCode()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Aperçu"))));
    }

    @Test
    void publishingAnEmptyStructuredMenuIsRefused() throws Exception {
        Fixture fixture = proClient();
        menuService.createMenu(fixture.restaurant().getId());

        mockMvc.perform(get("/m/{code}", fixture.qr().getCode())).andExpect(status().isNotFound());
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> menuService.publish(fixture.restaurant().getId()))
                .isInstanceOf(com.qrmenu.common.ConflictException.class);
    }
}
