package com.qrmenu.media;

import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaControllerTest {

    private static final byte[] VALID_PDF = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MediaService mediaService;
    @Autowired
    private RestaurantService restaurantService;

    private java.util.UUID storeAsset() {
        Restaurant r = restaurantService.create("Media " + System.nanoTime(), RestaurantOffer.BASIC);
        return mediaService.storePdf(r.getId(), VALID_PDF, "application/pdf", "menu.pdf").getId();
    }

    @Test
    void servesPdfInlineWithCacheHeaders() throws Exception {
        UUID assetId = storeAsset();

        mockMvc.perform(get("/media/" + assetId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("inline")))
                .andExpect(header().exists("Cache-Control"))
                .andExpect(header().exists("ETag"));
    }

    @Test
    void publicMediaEndpointDoesNotRequireAuthentication() throws Exception {
        UUID assetId = storeAsset();

        mockMvc.perform(get("/media/" + assetId)).andExpect(status().isOk());
    }

    @Test
    void unknownAssetReturns404() throws Exception {
        mockMvc.perform(get("/media/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
