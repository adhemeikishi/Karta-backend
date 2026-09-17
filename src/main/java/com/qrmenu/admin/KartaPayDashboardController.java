package com.qrmenu.admin;

import com.qrmenu.kartapay.KartaPayDashboardDtos.KartaPayDashboardResponse;
import com.qrmenu.kartapay.KartaPayDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Tableau de bord Karta Pay d'un client : scans, CA, plats les plus vendus, répartition du
 * CA, panier moyen et heures de pointe.
 * {@code GET /api/admin/restaurants/{id}/karta-pay/dashboard}.
 *
 * Strictement filtré par client (voir {@link KartaPayDashboardService}) : les données d'un
 * autre établissement ne peuvent pas entrer dans le résultat.
 */
@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}/karta-pay/dashboard")
public class KartaPayDashboardController {

    private final KartaPayDashboardService dashboardService;

    public KartaPayDashboardController(KartaPayDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public KartaPayDashboardResponse dashboard(@PathVariable UUID restaurantId) {
        return dashboardService.dashboard(restaurantId);
    }
}
