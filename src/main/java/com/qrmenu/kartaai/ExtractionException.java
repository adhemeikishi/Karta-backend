package com.qrmenu.kartaai;

/**
 * Échec d'extraction KartaAI, avec un message destiné au restaurateur.
 *
 * Ne porte jamais de détail technique du fournisseur (corps de réponse, clé, en-têtes) :
 * ces informations sont journalisées côté serveur, pas renvoyées au client.
 */
public class ExtractionException extends RuntimeException {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
