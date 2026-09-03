package com.qrmenu.admin;

import com.jayway.jsonpath.JsonPath;
import com.qrmenu.media.TestImages;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload d'image : bout en bout, de la requête multipart à l'URL publique servie. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String createRestaurant() throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Img " + System.nanoTime() + "\",\"offer\":\"PRO\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void imageUploadRequiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/admin/restaurants/" + UUID.randomUUID() + "/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", TestImages.PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadsPngAndReturnsPublicUrl() throws Exception {
        String id = createRestaurant();

        mockMvc.perform(multipart("/api/admin/restaurants/" + id + "/images")
                        .file(new MockMultipartFile("file", "plat.png", "image/png", TestImages.PNG))
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetId").exists())
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(TestImages.PNG.length))
                .andExpect(jsonPath("$.originalFilename").value("plat.png"))
                .andExpect(jsonPath("$.url", Matchers.containsString("/media/")));
    }

    /**
     * Boucle complète demandée par le produit : upload → URL publique → image réellement
     * servie, avec son vrai type MIME (et non celui du PDF, jadis codé en dur).
     */
    @Test
    void uploadedImageIsServedPubliclyWithItsRealContentType() throws Exception {
        String id = createRestaurant();

        String body = mockMvc.perform(multipart("/api/admin/restaurants/" + id + "/images")
                        .file(new MockMultipartFile("file", "plat.webp", "image/webp", TestImages.WEBP))
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String assetId = JsonPath.read(body, "$.assetId");

        // Pas d'authentification : l'image doit être lisible par le public (renderer).
        byte[] served = mockMvc.perform(get("/media/" + assetId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"))
                .andReturn().getResponse().getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(served).isEqualTo(TestImages.WEBP);
    }

    /** Le navigateur annonce « image/png » : seul le contenu réel fait foi. */
    @Test
    void rejectsPdfDisguisedAsPng() throws Exception {
        String id = createRestaurant();

        mockMvc.perform(multipart("/api/admin/restaurants/" + id + "/images")
                        .file(new MockMultipartFile("file", "plat.png", "image/png", TestImages.PDF))
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyFile() throws Exception {
        String id = createRestaurant();

        mockMvc.perform(multipart("/api/admin/restaurants/" + id + "/images")
                        .file(new MockMultipartFile("file", "vide.png", "image/png", new byte[0]))
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRestaurantReturns404() throws Exception {
        mockMvc.perform(multipart("/api/admin/restaurants/" + UUID.randomUUID() + "/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", TestImages.PNG))
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }
}
