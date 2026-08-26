package com.qrmenu.admin;

import com.qrmenu.admin.DashboardDtos.DashboardResponse;
import com.qrmenu.qrcode.QrCodeRepository;
import com.qrmenu.qrscan.QrScanService;
import com.qrmenu.restaurant.RestaurantRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardAdminController {

    private final RestaurantRepository restaurantRepository;
    private final QrCodeRepository qrCodeRepository;
    private final QrScanService qrScanService;

    public DashboardAdminController(
            RestaurantRepository restaurantRepository,
            QrCodeRepository qrCodeRepository,
            QrScanService qrScanService
    ) {
        this.restaurantRepository = restaurantRepository;
        this.qrCodeRepository = qrCodeRepository;
        this.qrScanService = qrScanService;
    }

    @GetMapping
    public DashboardResponse dashboard() {
        long totalRestaurants = restaurantRepository.count();
        long totalQrCodes = qrCodeRepository.count();
        long activeQrCodes = qrCodeRepository.countByActive(true);
        return DashboardResponse.from(totalRestaurants, totalQrCodes, activeQrCodes, qrScanService.globalStats());
    }
}
