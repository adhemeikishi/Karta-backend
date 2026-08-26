package com.qrmenu.qrscan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface QrScanRepository extends JpaRepository<QrScan, UUID> {

    long countByQrCodeId(UUID qrCodeId);

    @Query("select count(s) from QrScan s where s.qrCodeId = :qrCodeId and s.scannedAt >= :since")
    long countByQrCodeIdSince(@Param("qrCodeId") UUID qrCodeId, @Param("since") OffsetDateTime since);

    @Query("select count(s) from QrScan s where s.qrCodeId in " +
            "(select q.id from QrCode q where q.restaurantId = :restaurantId)")
    long countByRestaurantId(@Param("restaurantId") UUID restaurantId);

    @Query("select count(s) from QrScan s where s.scannedAt >= :since")
    long countSince(@Param("since") OffsetDateTime since);
}
