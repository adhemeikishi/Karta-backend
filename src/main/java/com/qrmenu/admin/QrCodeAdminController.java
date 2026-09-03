package com.qrmenu.admin;

import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeDtos.CreateQrCodeRequest;
import com.qrmenu.qrcode.QrCodeDtos.QrCodeResponse;
import com.qrmenu.qrcode.QrCodeDtos.QrCodeStatsResponse;
import com.qrmenu.qrcode.QrCodeService;
import com.qrmenu.qrcode.QrImageGenerator;
import com.qrmenu.qrscan.QrScanService;
import com.qrmenu.restaurant.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class QrCodeAdminController {

    private final QrCodeService qrCodeService;
    private final RestaurantService restaurantService;
    private final QrImageGenerator qrImageGenerator;
    private final QrScanService qrScanService;

    public QrCodeAdminController(
            QrCodeService qrCodeService,
            RestaurantService restaurantService,
            QrImageGenerator qrImageGenerator,
            QrScanService qrScanService
    ) {
        this.qrCodeService = qrCodeService;
        this.restaurantService = restaurantService;
        this.qrImageGenerator = qrImageGenerator;
        this.qrScanService = qrScanService;
    }

    /**
     * Voie de secours uniquement : la création normale d'un QR est automatique, à la
     * création du restaurant ({@code RestaurantAdminController.create}). Jamais exposée
     * dans l'interface — utile seulement pour réparer manuellement un restaurant hérité
     * qui n'aurait pas de QR. Refuse un second QR (409) si un existe déjà.
     */
    @PostMapping("/api/admin/restaurants/{restaurantId}/qr-codes")
    public ResponseEntity<QrCodeResponse> create(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody CreateQrCodeRequest request
    ) {
        restaurantService.getOrThrow(restaurantId); // 404 propre si le restaurant n'existe pas
        QrCode qrCode = qrCodeService.create(restaurantId, request.name(), request.destinationUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(qrCode));
    }

    @GetMapping("/api/admin/restaurants/{restaurantId}/qr-codes")
    public List<QrCodeResponse> findByRestaurant(@PathVariable UUID restaurantId) {
        restaurantService.getOrThrow(restaurantId);
        return qrCodeService.findByRestaurant(restaurantId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/api/admin/qr-codes/{id}")
    public QrCodeResponse findById(@PathVariable UUID id) {
        return toResponse(qrCodeService.getOrThrow(id));
    }

    @PostMapping("/api/admin/qr-codes/{id}/activate")
    public QrCodeResponse activate(@PathVariable UUID id) {
        return toResponse(qrCodeService.activate(id));
    }

    @PostMapping("/api/admin/qr-codes/{id}/deactivate")
    public QrCodeResponse deactivate(@PathVariable UUID id) {
        return toResponse(qrCodeService.deactivate(id));
    }

    @GetMapping(value = "/api/admin/qr-codes/{id}/image.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> imagePng(@PathVariable UUID id) {
        QrCode qrCode = qrCodeService.getOrThrow(id);
        byte[] png = qrImageGenerator.generatePng(qrCode.getCode());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping(value = "/api/admin/qr-codes/{id}/image.svg", produces = "image/svg+xml")
    public ResponseEntity<String> imageSvg(@PathVariable UUID id) {
        QrCode qrCode = qrCodeService.getOrThrow(id);
        String svg = qrImageGenerator.generateSvg(qrCode.getCode());
        return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml")).body(svg);
    }

    @GetMapping("/api/admin/qr-codes/{id}/stats")
    public QrCodeStatsResponse stats(@PathVariable UUID id) {
        QrCode qrCode = qrCodeService.getOrThrow(id);
        return QrCodeStatsResponse.from(qrCode.getId(), qrScanService.statsFor(qrCode.getId()));
    }

    private QrCodeResponse toResponse(QrCode qrCode) {
        return QrCodeResponse.from(qrCode, qrImageGenerator.buildRedirectUrl(qrCode.getCode()));
    }
}
