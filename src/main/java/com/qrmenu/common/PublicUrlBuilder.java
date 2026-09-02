package com.qrmenu.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Construit les URL publiques du produit à partir de {@code qr.base-url}.
 *
 * Point unique : le domaine public ne doit jamais être reconstruit à la main
 * ailleurs dans le code (un mauvais domaine dans un QR est un bug irréversible,
 * le QR étant déjà imprimé).
 */
@Component
public class PublicUrlBuilder {

    private final String baseUrl;

    public PublicUrlBuilder(@Value("${qr.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** Fichier servi par {@code GET /media/{assetId}} (PDF de menu, images). */
    public String forAsset(UUID assetId) {
        return baseUrl + "/media/" + assetId;
    }

    /** Page HTML publique du menu structuré, servie par {@code GET /m/{code}}. */
    public String forMenu(String qrCode) {
        return baseUrl + "/m/" + qrCode;
    }
}
