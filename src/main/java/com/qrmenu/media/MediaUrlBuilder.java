package com.qrmenu.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Construit les URL publiques des médias servis par {@code GET /media/{assetId}}. */
@Component
public class MediaUrlBuilder {

    private final String baseUrl;

    public MediaUrlBuilder(@Value("${qr.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String forAsset(UUID assetId) {
        return baseUrl + "/media/" + assetId;
    }
}
