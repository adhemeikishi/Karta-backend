package com.qrmenu.kartaai;

import com.qrmenu.common.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Quota par IP pour la démo publique KartaAI ({@code /api/public/menu-demo}).
 *
 * Chaque appel à Gemini a un coût réel ; cette page est publique et non authentifiée,
 * contrairement au reste de KartaAI (Basic Auth + offre PRO/PREMIUM). Compteur en mémoire,
 * volontairement simple : suffisant pour une seule instance de backend. Ne survit pas à un
 * redémarrage et ne serait pas partagé entre plusieurs instances — à revoir (ex : Redis) le
 * jour où le backend est répliqué.
 */
@Component
public class DemoRateLimiter {

    private final int maxPerWindow;
    private final Duration window = Duration.ofHours(1);
    private final ConcurrentMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public DemoRateLimiter(@Value("${kartaai.demo.max-per-hour:5}") int maxPerWindow) {
        this.maxPerWindow = maxPerWindow;
    }

    /** @throws RateLimitExceededException si ce client a déjà atteint son quota horaire */
    public void checkAllowed(String clientKey) {
        Deque<Instant> timestamps = hits.computeIfAbsent(clientKey, key -> new ArrayDeque<>());
        Instant now = Instant.now();
        synchronized (timestamps) {
            Instant cutoff = now.minus(window);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxPerWindow) {
                throw new RateLimitExceededException(
                        "Trop d'essais depuis cette connexion. Réessayez dans un moment ou "
                                + "utilisez le menu de démonstration.");
            }
            timestamps.addLast(now);
        }
    }
}
