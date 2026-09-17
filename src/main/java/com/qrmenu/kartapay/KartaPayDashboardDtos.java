package com.qrmenu.kartapay;

import com.qrmenu.qrscan.QrScanService.HourlyScans;
import com.qrmenu.qrscan.QrScanService.RestaurantScanStats;

import java.util.List;

/**
 * Contrat JSON du tableau de bord Karta Pay (restaurateur) :
 * {@code GET /api/admin/restaurants/{id}/karta-pay/dashboard}.
 */
public class KartaPayDashboardDtos {

    private KartaPayDashboardDtos() {
    }

    public record RevenuePeriods(long todayCents, long thisWeekCents, long thisMonthCents) {
    }

    public record TopItem(String name, long quantity, long revenueCents) {
    }

    /** Part du CA de la fenêtre représentée par un plat (ou "Autres", voir {@code KartaPayDashboardService}). */
    public record ItemShare(String name, long revenueCents, double percentage) {
    }

    public record KartaPayDashboardResponse(
            RestaurantScanStats qrScans,
            RevenuePeriods revenue,
            List<TopItem> topItems,
            List<ItemShare> revenueBreakdown,
            long avgBasketCents,
            List<HourlyScans> peakHours
    ) {
    }
}
