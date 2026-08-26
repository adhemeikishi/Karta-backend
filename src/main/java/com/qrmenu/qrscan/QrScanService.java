package com.qrmenu.qrscan;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class QrScanService {

    private final QrScanRepository qrScanRepository;

    public QrScanService(QrScanRepository qrScanRepository) {
        this.qrScanRepository = qrScanRepository;
    }

    /**
     * Enregistre un scan de façon asynchrone (fire-and-forget) : un souci d'écriture
     * des stats ne doit jamais bloquer ou retarder le 302 renvoyé au client.
     * Transaction dédiée, indépendante de celle de la requête HTTP appelante.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordScan(UUID qrCodeId, String userAgent) {
        DeviceType deviceType = DeviceType.fromUserAgent(userAgent);
        QrScan scan = new QrScan(qrCodeId, userAgent, deviceType);
        qrScanRepository.save(scan);
    }

    public QrScanStats statsFor(UUID qrCodeId) {
        TimeWindows w = TimeWindows.fromNow();

        long today = qrScanRepository.countByQrCodeIdSince(qrCodeId, w.startOfToday());
        long thisWeek = qrScanRepository.countByQrCodeIdSince(qrCodeId, w.startOfWeek());
        long thisMonth = qrScanRepository.countByQrCodeIdSince(qrCodeId, w.startOfMonth());
        long total = qrScanRepository.countByQrCodeId(qrCodeId);

        return new QrScanStats(today, thisWeek, thisMonth, total);
    }

    /**
     * Mêmes fenêtres temporelles que {@link #statsFor(UUID)}, mais agrégées sur
     * l'ensemble des scans (tous QR / tous restaurants confondus) - utilisé par le
     * dashboard global du back-office.
     */
    public QrScanStats globalStats() {
        TimeWindows w = TimeWindows.fromNow();

        long today = qrScanRepository.countSince(w.startOfToday());
        long thisWeek = qrScanRepository.countSince(w.startOfWeek());
        long thisMonth = qrScanRepository.countSince(w.startOfMonth());
        long total = qrScanRepository.count();

        return new QrScanStats(today, thisWeek, thisMonth, total);
    }

    public record QrScanStats(long today, long thisWeek, long thisMonth, long total) {
    }

    private record TimeWindows(OffsetDateTime startOfToday, OffsetDateTime startOfWeek, OffsetDateTime startOfMonth) {
        static TimeWindows fromNow() {
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime startOfToday = now.truncatedTo(ChronoUnit.DAYS);
            OffsetDateTime startOfWeek = startOfToday.minusDays(now.getDayOfWeek().getValue() - 1L);
            OffsetDateTime startOfMonth = startOfToday.withDayOfMonth(1);
            return new TimeWindows(startOfToday, startOfWeek, startOfMonth);
        }
    }
}
