package com.qrmenu.kartapay;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderLineRepository extends JpaRepository<OrderLine, UUID> {

    List<OrderLine> findByOrderIdOrderBySortOrderAsc(UUID orderId);

    /**
     * Quantité vendue et CA généré par plat, sur les commandes non annulées d'un client
     * depuis une date, triés par CA décroissant.
     *
     * Filtré via une sous-requête sur {@code Order} : comme partout dans le projet, aucune
     * relation JPA entre les deux entités (colonne UUID brute), donc aucune ligne d'un autre
     * restaurant ne peut entrer dans le résultat.
     */
    @Query("select ol.itemNameSnapshot as itemName, sum(ol.quantity) as totalQuantity, " +
            "sum(ol.lineTotalCents) as totalRevenueCents " +
            "from OrderLine ol where ol.orderId in " +
            "(select o.id from Order o where o.restaurantId = :restaurantId " +
            "and o.status <> :excludedStatus and o.createdAt >= :since) " +
            "group by ol.itemNameSnapshot order by sum(ol.lineTotalCents) desc")
    List<ItemAggregate> findItemAggregates(
            @Param("restaurantId") UUID restaurantId,
            @Param("excludedStatus") OrderStatus excludedStatus,
            @Param("since") OffsetDateTime since);

    /** Projection Spring Data — évite un DTO de constructeur JPQL pour un simple triplet. */
    interface ItemAggregate {
        String getItemName();

        long getTotalQuantity();

        long getTotalRevenueCents();
    }
}
