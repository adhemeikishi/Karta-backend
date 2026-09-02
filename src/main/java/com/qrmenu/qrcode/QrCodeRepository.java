package com.qrmenu.qrcode;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QrCodeRepository extends JpaRepository<QrCode, UUID> {

    Optional<QrCode> findByCode(String code);

    boolean existsByCode(String code);

    List<QrCode> findByRestaurantId(UUID restaurantId);

    /** Karta V1 : 1 restaurant = 1 QR. Utilisé pour recalculer la destination effective. */
    Optional<QrCode> findFirstByRestaurantId(UUID restaurantId);

    long countByRestaurantId(UUID restaurantId);

    long countByRestaurantIdAndActive(UUID restaurantId, boolean active);

    long countByActive(boolean active);
}
