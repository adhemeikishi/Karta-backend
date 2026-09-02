package com.qrmenu.common;

/**
 * Opération en conflit avec l'état courant de la ressource (ex : publier un menu
 * sans PDF, flux PDF sur un restaurant non BASIC).
 * Traduit en HTTP 409 par {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
