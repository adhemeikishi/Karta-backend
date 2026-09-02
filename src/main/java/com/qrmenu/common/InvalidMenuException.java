package com.qrmenu.common;

/** Contenu de menu invalide (nom vide, prix négatif, devise inconnue, référence incohérente). */
public class InvalidMenuException extends RuntimeException {

    public InvalidMenuException(String message) {
        super(message);
    }
}
