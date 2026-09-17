package com.qrmenu.kartapay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByRestaurantIdOrderByCreatedAtDesc(UUID restaurantId);

    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtDesc(UUID restaurantId, OrderStatus status);

    /** Historique opérationnel du jour (vue par défaut de l'onglet Commandes) : voir {@code OrderService.findByRestaurant}. */
    List<Order> findByRestaurantIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            UUID restaurantId, OffsetDateTime since);

    List<Order> findByRestaurantIdAndStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            UUID restaurantId, OrderStatus status, OffsetDateTime since);

    Optional<Order> findByRestaurantIdAndOrderNumber(UUID restaurantId, String orderNumber);

    /**
     * CA d'un client depuis une date, un statut exclu (voir {@code KartaPayDashboardService} :
     * les commandes annulées ne sont jamais du chiffre d'affaires réel). Part de
     * {@code totalCents}, jamais recalculé depuis les lignes.
     */
    @Query("select coalesce(sum(o.totalCents), 0) from Order o where o.restaurantId = :restaurantId " +
            "and o.status <> :excludedStatus and o.createdAt >= :since")
    long sumTotalCentsByRestaurantIdExcludingStatusSince(
            @Param("restaurantId") UUID restaurantId,
            @Param("excludedStatus") OrderStatus excludedStatus,
            @Param("since") OffsetDateTime since);

    /** Nombre de commandes d'un client depuis une date, un statut exclu — pour le panier moyen. */
    @Query("select count(o) from Order o where o.restaurantId = :restaurantId " +
            "and o.status <> :excludedStatus and o.createdAt >= :since")
    long countByRestaurantIdExcludingStatusSince(
            @Param("restaurantId") UUID restaurantId,
            @Param("excludedStatus") OrderStatus excludedStatus,
            @Param("since") OffsetDateTime since);
}
