package com.qrmenu.admin;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MenuAdminControllerTest {

    private static final byte[] VALID_PDF = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    private String createRestaurant(String offer) throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto Menu " + System.nanoTime() + "\",\"offer\":\"" + offer + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private MockMultipartFile pdfPart() {
        return new MockMultipartFile("file", "carte.pdf", "application/pdf", VALID_PDF);
    }

    @Test
    void menuEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/restaurants/" + UUID.randomUUID() + "/menu"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMenuReturnsSyntheticStateForNewRestaurant() throws Exception {
        String id = createRestaurant("BASIC");

        mockMvc.perform(get("/api/admin/restaurants/" + id + "/menu").with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offer").value("BASIC"))
                .andExpect(jsonPath("$.type").value("PDF"))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.pdf").value(Matchers.nullValue()));
    }

    @Test
    void uploadThenPublishReflectsInMenu() throws Exception {
        String id = createRestaurant("BASIC");

        mockMvc.perform(multipart("/api/admin/restaurants/" + id + "/menu/pdf")
                        .file(pdfPart())
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdf.assetId").exists())
                .andExpect(jsonPath("$.pdf.sizeBytes").value(VALID_PDF.length))
                .andExpect(jsonPath("$.published").value(false));

        mockMvc.perform(put("/api/admin/restaurants/" + id + "/menu/publish")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.publishedAt").exists());
    }

    @Test
    void rejectsNonPdfUpload() throws Exception {
        String id = createRestaurant("BASIC");

        mockMvc.perform(multipart("/api/admin/restaurants/" + id + "/menu/pdf")
                        .file(new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes()))
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isBadRequest());
    }

    /**
     * Un client PRO peut importer une carte PDF : elle sert de source à la future
     * transformation KartaAI. Le menu reste STRUCTURED et non publié — le PDF ne
     * devient pas le menu diffusé.
     */
    @Test
    void acceptsSourcePdfForNonBasicRestaurant() throws Exception {
        String id = createRestaurant("PRO");

        mockMvc.perform(multipart("/api/admin/restaurants/" + id + "/menu/pdf")
                        .file(pdfPart())
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdf.originalFilename").value("carte.pdf"))
                .andExpect(jsonPath("$.type").value("STRUCTURED"))
                .andExpect(jsonPath("$.published").value(false));
    }

    @Test
    void publishWithoutPdfReturns409() throws Exception {
        String id = createRestaurant("BASIC");

        mockMvc.perform(put("/api/admin/restaurants/" + id + "/menu/publish")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isConflict());
    }

    @Test
    void deletePdfClearsMenu() throws Exception {
        String id = createRestaurant("BASIC");
        mockMvc.perform(multipart("/api/admin/restaurants/" + id + "/menu/pdf")
                        .file(pdfPart())
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/admin/restaurants/" + id + "/menu/pdf")
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdf").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.published").value(false));
    }
}
