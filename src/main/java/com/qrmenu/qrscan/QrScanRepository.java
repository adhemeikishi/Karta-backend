package com.qrmenu.qrscan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface QrScanRepository extends JpaRepository<QrScan, UUID> {

    long countByQrCodeId(UUID qrCodeId);

    @Query("select count(s) from QrScan s where s.qrCodeId = :qrCodeId and s.scannedAt >= :since")
    long countByQrCodeIdSince(@Param("qrCodeId") UUID qrCodeId, @Param("since") OffsetDateTime since);

    @Query("select count(s) from QrScan s where s.qrCodeId in " +
            "(select q.id from QrCode q where q.restaurantId = :restaurantId)")
    long countByRestaurantId(@Param("restaurantId") UUID restaurantId);

    /**
     * Horodatages des scans d'un client depuis une date, triés.
     *
     * On rapatrie les instants plutôt que de faire agréger la base par jour : la
     * troncature de date n'a pas de syntaxe portable entre PostgreSQL (production) et
     * H2 (tests), et le regroupement doit de toute façon se faire dans le fuseau de
     * l'application. Sur une fenêtre de 30 jours et au volume de la V1, le coût est
     * négligeable — et les 4 compteurs comme le graphique dérivent alors des mêmes
     * données, donc ne peuvent pas se contredire.
     *
     * Le filtre par sous-requête sur {@code QrCode} garantit qu'aucun scan d'un autre
     * client ne peut entrer dans le résultat.
     */
    @Query("select s.scannedAt from QrScan s where s.qrCodeId in " +
            "(select q.id from QrCode q where q.restaurantId = :restaurantId) " +
            "and s.scannedAt >= :since order by s.scannedAt")
    List<OffsetDateTime> findScanTimesByRestaurantIdSince(
            @Param("restaurantId") UUID restaurantId,
            @Param("since") OffsetDateTime since);

    @Query("select count(s) from QrScan s where s.scannedAt >= :since")
    long countSince(@Param("since") OffsetDateTime since);
}
