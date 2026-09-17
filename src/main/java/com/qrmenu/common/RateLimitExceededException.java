package com.qrmenu.common;

/**
 * Quota d'appels dépassé pour un usage public non authentifié (ex : démo KartaAI).
 * Traduit en HTTP 429 par {@link GlobalExceptionHandler}.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
