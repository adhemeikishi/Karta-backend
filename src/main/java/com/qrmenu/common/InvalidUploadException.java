package com.qrmenu.common;

/**
 * Fichier uploadé invalide (mauvais type, signature absente, trop volumineux…).
 * Traduit en HTTP 400 par {@link GlobalExceptionHandler}.
 */
public class InvalidUploadException extends RuntimeException {

    public InvalidUploadException(String message) {
        super(message);
    }
}
