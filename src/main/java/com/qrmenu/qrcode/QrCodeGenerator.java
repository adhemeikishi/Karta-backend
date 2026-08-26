package com.qrmenu.qrcode;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Génère le code public opaque utilisé dans l'URL du QR (ex: /q/ABC123XYZ).
 * <p>
 * Exigences (voir contexte projet §6/§9) :
 * - aléatoire, non séquentiel, difficile à deviner
 * - basé sur SecureRandom
 * - jamais un simple compteur incrémental
 */
@Component
public class QrCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans O/0/I/1 ambigus
    private static final int DEFAULT_LENGTH = 10;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return generate(DEFAULT_LENGTH);
    }

    public String generate(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(ALPHABET.length());
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }
}
