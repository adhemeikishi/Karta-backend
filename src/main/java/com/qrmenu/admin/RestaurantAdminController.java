package com.qrmenu.admin;

import com.qrmenu.media.MediaService;
import com.qrmenu.qrcode.QrCodeRepository;
import com.qrmenu.qrscan.QrScanRepository;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantDtos.ChangeOfferRequest;
import com.qrmenu.restaurant.RestaurantDtos.CreateRestaurantRequest;
import com.qrmenu.restaurant.RestaurantDtos.RestaurantResponse;
import com.qrmenu.restaurant.RestaurantDtos.RestaurantSummaryResponse;
import com.qrmenu.restaurant.RestaurantDtos.UpdateRestaurantRequest;
import com.qrmenu.restaurant.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/restaurants")
public class RestaurantAdminController {

    private final RestaurantService restaurantService;
    private final QrCodeRepository qrCodeRepository;
    private final QrScanRepository qrScanRepository;
    private final MediaService mediaService;

    public RestaurantAdminController(
            RestaurantService restaurantService,
            QrCodeRepository qrCodeRepository,
            QrScanRepository qrScanRepository,
            MediaService mediaService
    ) {
        this.restaurantService = restaurantService;
        this.qrCodeRepository = qrCodeRepository;
        this.qrScanRepository = qrScanRepository;
        this.mediaService = mediaService;
    }

    @PostMapping
    public ResponseEntity<RestaurantResponse> create(@Valid @RequestBody CreateRestaurantRequest request) {
        Restaurant restaurant = restaurantService.create(request.name(), request.offer());
        return ResponseEntity.status(HttpStatus.CREATED).body(RestaurantResponse.from(restaurant));
    }

    /**
     * Liste enrichie avec compteurs (QR total/actifs, scans totaux) pour la page
     * "Restaurants" du back-office - évite un appel par restaurant côté frontend.
     */
    @GetMapping
    public List<RestaurantSummaryResponse> findAll() {
        return restaurantService.findAll().stream().map(this::toSummary).toList();
    }

    @GetMapping("/{id}")
    public RestaurantResponse findById(@PathVariable UUID id) {
        return RestaurantResponse.from(restaurantService.getOrThrow(id));
    }

    @PutMapping("/{id}")
    public RestaurantResponse rename(@PathVariable UUID id, @Valid @RequestBody UpdateRestaurantRequest request) {
        Restaurant restaurant = restaurantService.rename(id, request.name());
        return RestaurantResponse.from(restaurant);
    }

    @PutMapping("/{id}/offer")
    public RestaurantResponse changeOffer(@PathVariable UUID id, @Valid @RequestBody ChangeOfferRequest request) {
        return RestaurantResponse.from(restaurantService.changeOffer(id, request.offer()));
    }

    /**
     * Suppression physique du client. Cohérente avec la V1 (aucun soft-delete nulle
     * part) : les FK ON DELETE CASCADE suppriment qr_codes / qr_scans / menus /
     * media_assets ; les fichiers disque sont nettoyés avant (best effort).
     * Le QR imprimé devient définitivement inactif (410/404 au scan).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        restaurantService.getOrThrow(id); // 404 explicite si absent
        mediaService.deleteFilesForRestaurant(id);
        restaurantService.delete(id);
    }

    private RestaurantSummaryResponse toSummary(Restaurant restaurant) {
        long qrCount = qrCodeRepository.countByRestaurantId(restaurant.getId());
        long activeQrCount = qrCodeRepository.countByRestaurantIdAndActive(restaurant.getId(), true);
        long totalScans = qrScanRepository.countByRestaurantId(restaurant.getId());
        return new RestaurantSummaryResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getOffer(),
                restaurant.getCreatedAt(),
                restaurant.getUpdatedAt(),
                qrCount,
                activeQrCount,
                totalScans
        );
    }
}
