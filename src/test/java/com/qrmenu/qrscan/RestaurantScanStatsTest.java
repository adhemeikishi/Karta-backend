package com.qrmenu.qrscan;

import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeService;
import com.qrmenu.qrscan.QrScanService.DailyScans;
import com.qrmenu.qrscan.QrScanService.RestaurantScanStats;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Statistiques de scans par client.
 *
 * Les scans sont insérés directement en base : {@code QrScan} horodate au moment de sa
 * construction, il n'existe donc aucun moyen légitime de fabriquer un scan daté d'hier
 * via le modèle — et on ne va pas ajouter un setter de test au domaine pour ça.
 */
@SpringBootTest
@ActiveProfiles("test")
class RestaurantScanStatsTest {

    @Autowired
    private QrScanService qrScanService;
    @Autowired
    private RestaurantService restaurantService;
    @Autowired
    private QrCodeService qrCodeService;
    @Autowired
    private JdbcTemplate jdbc;

    private Restaurant restaurant() {
        return restaurantService.create("Stats " + System.nanoTime(), RestaurantOffer.BASIC);
    }

    private QrCode qrFor(Restaurant r) {
        return qrCodeService.create(r.getId(), "QR", "https://exemple.test/");
    }

    /** Insère un scan daté de {@code daysAgo} jours, à midi (loin de toute frontière de jour). */
    private void recordScanDaysAgo(UUID qrCodeId, int daysAgo) {
        OffsetDateTime when = LocalDate.now(ZoneId.systemDefault())
                .minusDays(daysAgo)
                .atTime(12, 0)
                .atZone(ZoneId.systemDefault())
                .toOffsetDateTime();
        jdbc.update(
                "INSERT INTO qr_scans (id, qr_code_id, scanned_at, user_agent, device_type) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), qrCodeId, Timestamp.from(when.toInstant()), "test-agent", "mobile");
    }

    private static long scansOn(RestaurantScanStats stats, int daysAgo) {
        LocalDate day = LocalDate.now(ZoneId.systemDefault()).minusDays(daysAgo);
        return stats.daily().stream()
                .filter(d -> d.date().equals(day))
                .mapToLong(DailyScans::scans)
                .sum();
    }

    @Test
    void noScansYieldsZerosAndAFullThirtyDaySeries() {
        Restaurant r = restaurant();
        qrFor(r);

        RestaurantScanStats stats = qrScanService.restaurantStats(r.getId());

        assertThat(stats.today()).isZero();
        assertThat(stats.last7Days()).isZero();
        assertThat(stats.last30Days()).isZero();
        assertThat(stats.total()).isZero();
        // La série reste complète : un axe troué se lit comme une donnée manquante.
        assertThat(stats.daily()).hasSize(QrScanService.DAILY_WINDOW_DAYS);
        assertThat(stats.daily()).allSatisfy(d -> assertThat(d.scans()).isZero());
        assertThat(stats.daily().get(stats.daily().size() - 1).date())
                .isEqualTo(LocalDate.now(ZoneId.systemDefault()));
    }

    @Test
    void singleScanTodayCountsOnce() {
        Restaurant r = restaurant();
        recordScanDaysAgo(qrFor(r).getId(), 0);

        RestaurantScanStats stats = qrScanService.restaurantStats(r.getId());

        assertThat(stats.today()).isEqualTo(1);
        assertThat(stats.last7Days()).isEqualTo(1);
        assertThat(stats.last30Days()).isEqualTo(1);
        assertThat(stats.total()).isEqualTo(1);
        assertThat(scansOn(stats, 0)).isEqualTo(1);
    }

    @Test
    void severalScansOnTheSameDayAreAggregated() {
        Restaurant r = restaurant();
        QrCode qr = qrFor(r);
        recordScanDaysAgo(qr.getId(), 0);
        recordScanDaysAgo(qr.getId(), 0);
        recordScanDaysAgo(qr.getId(), 0);

        RestaurantScanStats stats = qrScanService.restaurantStats(r.getId());

        assertThat(stats.today()).isEqualTo(3);
        assertThat(scansOn(stats, 0)).isEqualTo(3);
    }

    @Test
    void scansAreSpreadAcrossTheCorrectDays() {
        Restaurant r = restaurant();
        QrCode qr = qrFor(r);
        recordScanDaysAgo(qr.getId(), 0);
        recordScanDaysAgo(qr.getId(), 1);
        recordScanDaysAgo(qr.getId(), 1);
        recordScanDaysAgo(qr.getId(), 5);

        RestaurantScanStats stats = qrScanService.restaurantStats(r.getId());

        assertThat(scansOn(stats, 0)).isEqualTo(1);
        assertThat(scansOn(stats, 1)).isEqualTo(2);
        assertThat(scansOn(stats, 5)).isEqualTo(1);
        assertThat(stats.today()).isEqualTo(1);
        assertThat(stats.last7Days()).isEqualTo(4);
        assertThat(stats.last30Days()).isEqualTo(4);
    }

    /** La fenêtre 7 jours inclut aujourd'hui : J-6 est dedans, J-7 non. */
    @Test
    void sevenDayWindowIncludesTodayAndExcludesTheEighthDay() {
        Restaurant r = restaurant();
        QrCode qr = qrFor(r);
        recordScanDaysAgo(qr.getId(), 6);
        recordScanDaysAgo(qr.getId(), 7);

        RestaurantScanStats stats = qrScanService.restaurantStats(r.getId());

        assertThat(stats.last7Days()).isEqualTo(1);
        assertThat(stats.last30Days()).isEqualTo(2);
    }

    @Test
    void thirtyDayWindowExcludesOlderScansButTotalKeepsThem() {
        Restaurant r = restaurant();
        QrCode qr = qrFor(r);
        recordScanDaysAgo(qr.getId(), 29); // dernier jour de la fenêtre
        recordScanDaysAgo(qr.getId(), 30); // juste en dehors
        recordScanDaysAgo(qr.getId(), 120);

        RestaurantScanStats stats = qrScanService.restaurantStats(r.getId());

        assertThat(stats.last30Days()).isEqualTo(1);
        assertThat(stats.total()).as("le total couvre tout l'historique").isEqualTo(3);
        assertThat(stats.daily()).hasSize(30);
    }

    /** Exigence stricte : jamais de mélange entre deux clients. */
    @Test
    void statsAreStrictlyScopedToOneRestaurant() {
        Restaurant a = restaurant();
        Restaurant b = restaurant();
        QrCode qrA = qrFor(a);
        QrCode qrB = qrFor(b);

        recordScanDaysAgo(qrA.getId(), 0);
        recordScanDaysAgo(qrB.getId(), 0);
        recordScanDaysAgo(qrB.getId(), 2);
        recordScanDaysAgo(qrB.getId(), 40);

        RestaurantScanStats statsA = qrScanService.restaurantStats(a.getId());
        RestaurantScanStats statsB = qrScanService.restaurantStats(b.getId());

        assertThat(statsA.today()).isEqualTo(1);
        assertThat(statsA.last30Days()).isEqualTo(1);
        assertThat(statsA.total()).isEqualTo(1);

        assertThat(statsB.today()).isEqualTo(1);
        assertThat(statsB.last30Days()).isEqualTo(2);
        assertThat(statsB.total()).isEqualTo(3);
    }

    @Test
    void restaurantWithoutAnyQrCodeHasEmptyStats() {
        Restaurant r = restaurant();

        RestaurantScanStats stats = qrScanService.restaurantStats(r.getId());

        assertThat(stats.total()).isZero();
        assertThat(stats.daily()).hasSize(QrScanService.DAILY_WINDOW_DAYS);
    }

    /** Les compteurs de période dérivent de la série : ils ne peuvent pas la contredire. */
    @Test
    void periodCountersAlwaysMatchTheDailySeries() {
        Restaurant r = restaurant();
        QrCode qr = qrFor(r);
        for (int daysAgo : new int[]{0, 0, 1, 3, 6, 9, 15, 29}) {
            recordScanDaysAgo(qr.getId(), daysAgo);
        }

        RestaurantScanStats stats = qrScanService.restaurantStats(r.getId());

        long seriesTotal = stats.daily().stream().mapToLong(DailyScans::scans).sum();
        assertThat(stats.last30Days()).isEqualTo(seriesTotal);
        assertThat(stats.today()).isEqualTo(scansOn(stats, 0));
    }
}
