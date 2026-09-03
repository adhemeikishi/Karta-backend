package com.qrmenu.media;

/**
 * Formats d'image acceptés par Karta, identifiés par leur <strong>signature</strong>
 * (magic bytes) et non par l'extension ou le {@code Content-Type} annoncé par le
 * navigateur — tous deux librement falsifiables.
 *
 * Le type MIME servi ensuite par {@code GET /media/{id}} est celui déduit ici :
 * un fichier renommé {@code .png} ne peut donc pas être servi comme autre chose que
 * ce qu'il est réellement.
 */
public enum ImageFormat {

    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp");

    private final String contentType;
    private final String extension;

    ImageFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /**
     * Format réel du contenu, ou {@code null} s'il ne correspond à aucun format accepté.
     *
     * <ul>
     *   <li>JPEG : {@code FF D8 FF}</li>
     *   <li>PNG  : {@code 89 50 4E 47 0D 0A 1A 0A}</li>
     *   <li>WebP : conteneur RIFF — {@code "RIFF"} en 0 et {@code "WEBP"} en 8</li>
     * </ul>
     */
    public static ImageFormat detect(byte[] content) {
        if (content == null) {
            return null;
        }
        if (startsWith(content, 0, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (startsWith(content, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        if (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P')) {
            return WEBP;
        }
        return null;
    }

    private static boolean startsWith(byte[] content, int offset, int... signature) {
        if (content.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
