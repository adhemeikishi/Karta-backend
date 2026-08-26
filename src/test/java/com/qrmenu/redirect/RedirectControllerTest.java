package com.qrmenu.redirect;

import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeRepository;
import com.qrmenu.qrcode.QrCodeService;
import com.qrmenu.qrscan.QrScanRepository;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private QrCodeService qrCodeService;

    @Autowired
    private QrCodeRepository qrCodeRepository;

    @Autowired
    private QrScanRepository qrScanRepository;

    @Test
    void activeQrCodeRedirectsWith302() throws Exception {
        QrCode qrCode = createActiveQrCode("https://example.com/menu-active.pdf");

        mockMvc.perform(get("/q/{code}", qrCode.getCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/menu-active.pdf"));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mockMvc.perform(get("/q/{code}", "DOESNOTEXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatedQrCodeDoesNotRedirect() throws Exception {
        QrCode qrCode = createActiveQrCode("https://example.com/menu-inactive.pdf");
        qrCodeService.deactivate(qrCode.getId());

        mockMvc.perform(get("/q/{code}", qrCode.getCode()))
                .andExpect(status().isGone());
    }

    @Test
    void scanIsRecordedOnSuccessfulRedirect() throws Exception {
        QrCode qrCode = createActiveQrCode("https://example.com/menu-scan.pdf");
        long before = qrScanRepository.countByQrCodeId(qrCode.getId());

        mockMvc.perform(get("/q/{code}", qrCode.getCode()))
                .andExpect(status().isFound());

        // L'enregistrement du scan est asynchrone (ne doit pas ralentir la redirection),
        // on attend donc qu'il apparaisse en base au lieu de le vérifier immédiatement.
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(qrScanRepository.countByQrCodeId(qrCode.getId())).isEqualTo(before + 1)
        );
    }

    private QrCode createActiveQrCode(String destinationUrl) {
        Restaurant restaurant = restaurantService.create("Restaurant Test " + System.nanoTime());
        return qrCodeService.create(restaurant.getId(), "QR test", destinationUrl);
    }
}
