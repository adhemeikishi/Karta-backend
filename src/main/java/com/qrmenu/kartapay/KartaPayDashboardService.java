package com.qrmenu.kartapay;

import com.qrmenu.kartapay.KartaPayDashboardDtos.ItemShare;
import com.qrmenu.kartapay.KartaPayDashboardDtos.KartaPayDashboardResponse;
import com.qrmenu.kartapay.KartaPayDashboardDtos.RevenuePeriods;
import com.qrmenu.kartapay.KartaPayDashboardDtos.TopItem;
import com.qrmenu.kartapay.OrderLineRepository.ItemAggregate;
import com.qrmenu.qrscan.QrScanService;
import com.qrmenu.restaurant.RestaurantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agrégation du tableau de bord Karta Pay d'un client : scans, chiffre d'affaires, plats
 * les plus vendus, répartition du CA, panier moyen et heures de pointe.
 *
 * Le CA (aujourd'hui/semaine/mois, dénominateur du camembert, panier moyen) part toujours
 * de {@link Order#getTotalCents()} — jamais recalculé depuis les lignes, qui ne servent
 * qu'à répartir ce CA par plat (voir {@link OrderLineRepository}). Les commandes
 * {@link OrderStatus#CANCELLED} sont exclues de tous les calculs : elles ne sont pas du
 * chiffre d'affaires réel.
 */
@Service
public class KartaPayDashboardService {

    /**
     * Fenêtre des plats les plus vendus / camembert / panier moyen — alignée sur
     * {@link QrScanService#DAILY_WINDOW_DAYS} pour rester cohérente avec le reste du
     * tableau de bord (scans, heures de pointe).
     */
    private static final int WINDOW_DAYS = QrScanService.DAILY_WINDOW_DAYS;

    /** Au-delà de ce rang, les plats sont regroupés sous "Autres" dans le camembert. */
    private static final int BREAKDOWN_TOP_N = 6;

    private static final int TOP_ITEMS_COUNT = 3;

    private final RestaurantService restaurantService;
    private final QrScanService qrScanService;
    private final OrderRepository orderRepository;
    private final OrderLineRepository orderLineRepository;

    public KartaPayDashboardService(
            RestaurantService restaurantService,
            QrScanService qrScanService,
            OrderRepository orderRepository,
            OrderLineRepository orderLineRepository
    ) {
        this.restaurantService = restaurantService;
        this.qrScanService = qrScanService;
        this.orderRepository = orderRepository;
        this.orderLineRepository = orderLineRepository;
    }

    @Transactional(readOnly = true)
    public KartaPayDashboardResponse dashboard(UUID restaurantId) {
        restaurantService.getOrThrow(restaurantId); // 404 explicite si le client n'existe pas

        OffsetDateTime windowStart = windowStart();

        long windowRevenueCents = orderRepository.sumTotalCentsByRestaurantIdExcludingStatusSince(
                restaurantId, OrderStatus.CANCELLED, windowStart);
        long windowOrderCount = orderRepository.countByRestaurantIdExcludingStatusSince(
                restaurantId, OrderStatus.CANCELLED, windowStart);
        long avgBasketCents = windowOrderCount == 0
                ? 0
                : Math.round(windowRevenueCents / (double) windowOrderCount);

        List<ItemAggregate> aggregates = orderLineRepository.findItemAggregates(
                restaurantId, OrderStatus.CANCELLED, windowStart);

        List<TopItem> topItems = aggregates.stream()
                .limit(TOP_ITEMS_COUNT)
                .map(a -> new TopItem(a.getItemName(), a.getTotalQuantity(), a.getTotalRevenueCents()))
                .toList();

        return new KartaPayDashboardResponse(
                qrScanService.restaurantStats(restaurantId),
                revenuePeriods(restaurantId),
                topItems,
                revenueBreakdown(aggregates, windowRevenueCents),
                avgBasketCents,
                qrScanService.hourlyDistribution(restaurantId));
    }

    private RevenuePeriods revenuePeriods(UUID restaurantId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfToday = now.truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime startOfWeek = startOfToday.minusDays(now.getDayOfWeek().getValue() - 1L);
        OffsetDateTime startOfMonth = startOfToday.withDayOfMonth(1);

        long todayCents = orderRepository.sumTotalCentsByRestaurantIdExcludingStatusSince(
                restaurantId, OrderStatus.CANCELLED, startOfToday);
        long thisWeekCents = orderRepository.sumTotalCentsByRestaurantIdExcludingStatusSince(
                restaurantId, OrderStatus.CANCELLED, startOfWeek);
        long thisMonthCents = orderRepository.sumTotalCentsByRestaurantIdExcludingStatusSince(
                restaurantId, OrderStatus.CANCELLED, startOfMonth);
        return new RevenuePeriods(todayCents, thisWeekCents, thisMonthCents);
    }

    private static OffsetDateTime windowStart() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate windowStart = LocalDate.now(zone).minusDays(WINDOW_DAYS - 1L);
        return windowStart.atStartOfDay(zone).toOffsetDateTime();
    }

    /** Regroupe les plats au-delà du top {@link #BREAKDOWN_TOP_N} sous "Autres". */
    private static List<ItemShare> revenueBreakdown(List<ItemAggregate> aggregates, long windowRevenueCents) {
        List<ItemShare> shares = new ArrayList<>();
        long othersRevenueCents = 0;
        for (int i = 0; i < aggregates.size(); i++) {
            ItemAggregate a = aggregates.get(i);
            if (i < BREAKDOWN_TOP_N) {
                shares.add(new ItemShare(
                        a.getItemName(), a.getTotalRevenueCents(),
                        percentage(a.getTotalRevenueCents(), windowRevenueCents)));
            } else {
                othersRevenueCents += a.getTotalRevenueCents();
            }
        }
        if (othersRevenueCents > 0) {
            shares.add(new ItemShare("Autres", othersRevenueCents, percentage(othersRevenueCents, windowRevenueCents)));
        }
        return shares;
    }

    private static double percentage(long partCents, long totalCents) {
        return totalCents == 0 ? 0.0 : (partCents * 100.0) / totalCents;
    }
}
