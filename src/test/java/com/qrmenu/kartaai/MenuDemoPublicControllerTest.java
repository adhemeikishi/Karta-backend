package com.qrmenu.kartaai;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Démo publique KartaAI ({@code /api/public/menu-demo/extract}), utilisée par
 * {@code /create/design} : aucune authentification, aucun restaurant, aucune écriture en
 * base — seuls le quota par IP et les mêmes garde-fous que l'onboarding réel protègent
 * cette route.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeMenuExtractor.Config.class)
class MenuDemoPublicControllerTest {

    private static final byte[] PDF = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8);
    private static final String URL = "/api/public/menu-demo/extract";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FakeMenuExtractor extractor;

    @BeforeEach
    void resetExtractor() {
        extractor.reset();
    }

    @Test
    void validPdfIsExtractedWithoutAnyAuthentication() throws Exception {
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "carte.pdf", "application/pdf", PDF))
                        .header("X-Forwarded-For", "203.0.113.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].items[0].name").value("Classic Burger"))
                .andExpect(jsonPath("$.categories[0].items[0].price").value(950));
    }

    @Test
    void missingFileIsRejected() throws Exception {
        mockMvc.perform(multipart(URL).header("X-Forwarded-For", "203.0.113.11"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonPdfFileIsRejected() throws Exception {
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "carte.txt", "text/plain", "pas un pdf".getBytes()))
                        .header("X-Forwarded-For", "203.0.113.12"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedPdfIsRejected() throws Exception {
        byte[] tooBig = new byte[11 * 1024 * 1024];
        System.arraycopy(PDF, 0, tooBig, 0, PDF.length);
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "carte.pdf", "application/pdf", tooBig))
                        .header("X-Forwarded-For", "203.0.113.13"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unreadableMenuNeverReturnsAFakeMenu() throws Exception {
        extractor.willReturn(new ExtractionDtos.ExtractedMenu(java.util.List.of()));
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "carte.pdf", "application/pdf", PDF))
                        .header("X-Forwarded-For", "203.0.113.14"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Aucun plat n'a pu être lu")));
    }

    @Test
    void providerFailureIsReportedWithoutLeakingDetails() throws Exception {
        extractor.willFail(new ExtractionException("Le service d'analyse est momentanément indisponible."));
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "carte.pdf", "application/pdf", PDF))
                        .header("X-Forwarded-For", "203.0.113.15"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Le service d'analyse est momentanément indisponible."));
    }

    @Test
    void aSingleIpIsThrottledPastTheHourlyQuota() throws Exception {
        String ip = "203.0.113.99";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(multipart(URL)
                            .file(new MockMultipartFile("file", "carte.pdf", "application/pdf", PDF))
                            .header("X-Forwarded-For", ip))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "carte.pdf", "application/pdf", PDF))
                        .header("X-Forwarded-For", ip))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void quotaIsIsolatedPerIp() throws Exception {
        String throttled = "203.0.113.100";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(multipart(URL)
                            .file(new MockMultipartFile("file", "carte.pdf", "application/pdf", PDF))
                            .header("X-Forwarded-For", throttled))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(multipart(URL)
                        .file(new MockMultipartFile("file", "carte.pdf", "application/pdf", PDF))
                        .header("X-Forwarded-For", "203.0.113.101"))
                .andExpect(status().isOk());
    }
}
