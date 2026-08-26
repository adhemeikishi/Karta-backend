package com.qrmenu.admin;

import com.qrmenu.qrscan.QrScanService.QrScanStats;

public class DashboardDtos {

    private DashboardDtos() {
    }

    /**
     * Note : pas de "restaurants actifs" ici volontairement. L'entité Restaurant
     * n'a pas de notion de statut actif/inactif côté backend (seul QrCode en a
     * une) - on n'invente pas ce champ pour éviter d'afficher une donnée qui ne
     * correspond à rien de réel. Si ce concept devient nécessaire, il faudra
     * d'abord l'ajouter à l'entité Restaurant.
     */
    public record DashboardResponse(
            long totalRestaurants,
            long totalQrCodes,
            long activeQrCodes,
            long scansToday,
            long scansThisWeek,
            long scansThisMonth,
            long scansTotal
    ) {
        public static DashboardResponse from(
                long totalRestaurants,
                long totalQrCodes,
                long activeQrCodes,
                QrScanStats globalStats
        ) {
            return new DashboardResponse(
                    totalRestaurants,
                    totalQrCodes,
                    activeQrCodes,
                    globalStats.today(),
                    globalStats.thisWeek(),
                    globalStats.thisMonth(),
                    globalStats.total()
            );
        }
    }
}
