package com.qrmenu.qrscan;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QrScanService {

    /** Largeur de la fenêtre du graphique de scans, en jours (aujourd'hui inclus). */
    public static final int DAILY_WINDOW_DAYS = 30;

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

    /**
     * Statistiques de scans d'un <strong>client</strong>, sur une fenêtre glissante de
     * 30 jours se terminant aujourd'hui.
     *
     * Les trois compteurs de période et la série quotidienne sont calculés à partir du
     * même jeu d'horodatages : le graphique ne peut donc jamais contredire les chiffres
     * affichés au-dessus de lui. Seul {@code total} (tout l'historique) vient d'un
     * compteur distinct.
     *
     * Les jours sans scan figurent dans la série avec {@code 0} — un axe temporel troué
     * se lit mal et laisse croire à des données manquantes.
     */
    @Transactional(readOnly = true)
    public RestaurantScanStats restaurantStats(UUID restaurantId) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate windowStart = today.minusDays(DAILY_WINDOW_DAYS - 1L);
        OffsetDateTime since = windowStart.atStartOfDay(zone).toOffsetDateTime();

        Map<LocalDate, Long> byDay = qrScanRepository
                .findScanTimesByRestaurantIdSince(restaurantId, since).stream()
                .collect(Collectors.groupingBy(
                        instant -> instant.atZoneSameInstant(zone).toLocalDate(),
                        Collectors.counting()));

        List<DailyScans> daily = new ArrayList<>(DAILY_WINDOW_DAYS);
        for (int i = 0; i < DAILY_WINDOW_DAYS; i++) {
            LocalDate day = windowStart.plusDays(i);
            daily.add(new DailyScans(day, byDay.getOrDefault(day, 0L)));
        }

        return new RestaurantScanStats(
                byDay.getOrDefault(today, 0L),
                sumOfLastDays(daily, 7),
                sumOfLastDays(daily, DAILY_WINDOW_DAYS),
                qrScanRepository.countByRestaurantId(restaurantId),
                daily);
    }

    /** Somme des {@code days} derniers jours de la série (aujourd'hui inclus). */
    private static long sumOfLastDays(List<DailyScans> daily, int days) {
        return daily.subList(Math.max(0, daily.size() - days), daily.size()).stream()
                .mapToLong(DailyScans::scans)
                .sum();
    }

    public record QrScanStats(long today, long thisWeek, long thisMonth, long total) {
    }

    /** Un jour de la série. {@code date} est une date locale, pas un instant. */
    public record DailyScans(LocalDate date, long scans) {
    }

    public record RestaurantScanStats(
            long today,
            long last7Days,
            long last30Days,
            long total,
            List<DailyScans> daily
    ) {
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
