package com.qrmenu.redirect;

import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeService;
import com.qrmenu.qrscan.QrScanService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Route publique principale du produit : résout un code QR et redirige
 * vers la destination configurée par l'administrateur.
 * <p>
 * Priorité absolue : rapidité. Le tracking du scan ne doit jamais retarder le 302
 * (voir QrScanService#recordScan, exécuté de façon asynchrone).
 */
@RestController
public class RedirectController {

    private final QrCodeService qrCodeService;
    private final QrScanService qrScanService;

    public RedirectController(QrCodeService qrCodeService, QrScanService qrScanService) {
        this.qrCodeService = qrCodeService;
        this.qrScanService = qrScanService;
    }

    @GetMapping("/q/{code}")
    public ResponseEntity<String> redirect(@PathVariable String code, HttpServletRequest request) {
        QrCode qrCode = qrCodeService.getByCodeOrThrow(code); // -> 404 via GlobalExceptionHandler si absent

        if (!qrCode.isActive()) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Ce QR code n'est plus actif.");
        }

        // Le tracking ne doit pas bloquer la redirection.
        qrScanService.recordScan(qrCode.getId(), request.getHeader(HttpHeaders.USER_AGENT));

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(qrCode.getDestinationUrl()))
                .build();
    }
}
