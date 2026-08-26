package com.qrmenu.common;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Valide les URLs de destination d'un QR code.
 * <p>
 * Règle stricte : seuls http:// et https:// sont autorisés.
 * Tout le reste (javascript:, data:, file:, etc.) est refusé.
 * <p>
 * La valeur validée ici est celle qui sera stockée en base et utilisée
 * plus tard pour la redirection - jamais une valeur construite dynamiquement
 * au moment du scan.
 */
@Component
public class DestinationUrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    public void validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("La destination ne peut pas être vide.");
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL invalide: " + rawUrl);
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException(
                    "Schéma d'URL non autorisé. Seuls http:// et https:// sont acceptés.");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("URL invalide: hôte manquant.");
        }
    }
}
