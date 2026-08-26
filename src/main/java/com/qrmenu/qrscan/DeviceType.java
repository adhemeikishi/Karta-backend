package com.qrmenu.qrscan;

public enum DeviceType {
    MOBILE,
    DESKTOP,
    UNKNOWN;

    /**
     * Classification très simple à partir du User-Agent.
     * Pas de fingerprinting, pas de librairie tierce - juste une détection basique
     * suffisante pour des statistiques indicatives (voir §12 du contexte projet).
     */
    public static DeviceType fromUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        String ua = userAgent.toLowerCase();
        boolean looksMobile = ua.contains("mobi") || ua.contains("android")
                || ua.contains("iphone") || ua.contains("ipad");
        return looksMobile ? MOBILE : DESKTOP;
    }
}
