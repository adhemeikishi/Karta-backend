package com.qrmenu.admin;

import com.qrmenu.common.NotFoundException;
import com.qrmenu.menu.Menu;
import com.qrmenu.menu.MenuDesign;
import com.qrmenu.menu.MenuRepository;
import com.qrmenu.menu.QrDesign;
import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeDtos.CreateQrCodeRequest;
import com.qrmenu.qrcode.QrCodeDtos.QrCodeResponse;
import com.qrmenu.qrcode.QrCodeDtos.QrCodeStatsResponse;
import com.qrmenu.qrcode.QrCodeService;
import com.qrmenu.qrcode.QrEyeStyle;
import com.qrmenu.qrcode.QrImageGenerator;
import com.qrmenu.qrcode.QrModuleStyle;
import com.qrmenu.qrcode.QrStyle;
import com.qrmenu.qrscan.QrScanService;
import com.qrmenu.render.MenuThemeResolver;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
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
    private final MenuRepository menuRepository;
    private final MenuThemeResolver themeResolver;

    public QrCodeAdminController(
            QrCodeService qrCodeService,
            RestaurantService restaurantService,
            QrImageGenerator qrImageGenerator,
            QrScanService qrScanService,
            MenuRepository menuRepository,
            MenuThemeResolver themeResolver
    ) {
        this.qrCodeService = qrCodeService;
        this.restaurantService = restaurantService;
        this.qrImageGenerator = qrImageGenerator;
        this.qrScanService = qrScanService;
        this.menuRepository = menuRepository;
        this.themeResolver = themeResolver;
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
        byte[] png = qrImageGenerator.generatePng(qrCode.getCode(), styleFor(qrCode, null));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping(value = "/api/admin/qr-codes/{id}/image.svg", produces = "image/svg+xml")
    public ResponseEntity<String> imageSvg(@PathVariable UUID id) {
        QrCode qrCode = qrCodeService.getOrThrow(id);
        String svg = qrImageGenerator.generateSvg(qrCode.getCode(), styleFor(qrCode, null));
        return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml")).body(svg);
    }

    /**
     * Image du QR d'un restaurant, adressée <strong>par le restaurant</strong>.
     *
     * <p>Les deux routes ci-dessus ({@code /api/admin/qr-codes/{id}/image.*}) désignent le
     * QR par son propre identifiant : rien dans le chemin ne dit à quel restaurant il
     * appartient, et {@code RestaurateurScopeFilter} les refuse donc à un restaurateur,
     * faute de pouvoir vérifier. Porter le restaurant dans l'URL rend la vérification
     * possible sans rien changer au filtre — c'est le même contrôle que pour la carte ou
     * l'apparence. Le back-office continue d'utiliser les routes par identifiant de QR.
     *
     * <p>Règle produit V1 : 1 restaurant = 1 QR. On sert donc le sien, sans choix à faire.
     *
     * <p>Les paramètres optionnels ({@code fgColor}, {@code bgColor}, {@code moduleStyle},
     * {@code eyeStyle}, {@code logoAssetId}, {@code hideBranding}) sont des surcharges
     * d'<strong>aperçu</strong>, appliquées par-dessus le design enregistré sans rien
     * écrire — le même mécanisme que l'aperçu du menu. Comme lui, elles ne contournent
     * aucun droit : hors PREMIUM, {@code MenuThemeResolver} les ignore.
     */
    @GetMapping(value = "/api/admin/restaurants/{restaurantId}/qr-code/image.png",
            produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> restaurantImagePng(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) String fgColor,
            @RequestParam(required = false) String bgColor,
            @RequestParam(required = false) QrModuleStyle moduleStyle,
            @RequestParam(required = false) QrEyeStyle eyeStyle,
            @RequestParam(required = false) UUID logoAssetId,
            @RequestParam(required = false) Boolean hideBranding
    ) {
        QrCode qrCode = restaurantQrCode(restaurantId);
        MenuDesign overrides = overrides(fgColor, bgColor, moduleStyle, eyeStyle, logoAssetId, hideBranding);
        byte[] png = qrImageGenerator.generatePng(qrCode.getCode(), styleFor(qrCode, overrides));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping(value = "/api/admin/restaurants/{restaurantId}/qr-code/image.svg",
            produces = "image/svg+xml")
    public ResponseEntity<String> restaurantImageSvg(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) String fgColor,
            @RequestParam(required = false) String bgColor,
            @RequestParam(required = false) QrModuleStyle moduleStyle,
            @RequestParam(required = false) QrEyeStyle eyeStyle,
            @RequestParam(required = false) UUID logoAssetId,
            @RequestParam(required = false) Boolean hideBranding
    ) {
        QrCode qrCode = restaurantQrCode(restaurantId);
        MenuDesign overrides = overrides(fgColor, bgColor, moduleStyle, eyeStyle, logoAssetId, hideBranding);
        String svg = qrImageGenerator.generateSvg(qrCode.getCode(), styleFor(qrCode, overrides));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.valueOf("image/svg+xml")).body(svg);
    }

    /**
     * Apparence du QR : celle du design du restaurant, résolue avec son offre. Un
     * restaurant sans ligne {@code menus} (ou BASIC) obtient le QR par défaut.
     */
    private QrStyle styleFor(QrCode qrCode, MenuDesign overrides) {
        Restaurant restaurant = restaurantService.getOrThrow(qrCode.getRestaurantId());
        MenuDesign design = menuRepository.findByRestaurantId(restaurant.getId())
                .map(Menu::getDesign)
                .orElseGet(MenuDesign::defaults)
                .mergedWith(overrides);
        return themeResolver.resolveQr(design, restaurant.getOffer());
    }

    private static MenuDesign overrides(
            String fgColor, String bgColor, QrModuleStyle moduleStyle, QrEyeStyle eyeStyle,
            UUID logoAssetId, Boolean hideBranding
    ) {
        if (fgColor == null && bgColor == null && moduleStyle == null && eyeStyle == null
                && logoAssetId == null && hideBranding == null) {
            return null;
        }
        return new MenuDesign(null, null, null, null, null, null, hideBranding, null, null,
                new QrDesign(fgColor, bgColor, moduleStyle, eyeStyle, logoAssetId));
    }

    /** Le QR du restaurant. 404 tant qu'il n'en a pas — un restaurant en a un dès sa création. */
    private QrCode restaurantQrCode(UUID restaurantId) {
        restaurantService.getOrThrow(restaurantId);
        return qrCodeService.findByRestaurant(restaurantId).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Ce restaurant n'a pas de QR code."));
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
