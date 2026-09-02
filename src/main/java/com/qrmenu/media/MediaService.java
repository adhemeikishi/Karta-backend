package com.qrmenu.media;

import com.qrmenu.common.InvalidUploadException;
import com.qrmenu.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MediaService {

    /** Taille maximale d'un PDF de menu : 10 Mo. */
    public static final long MAX_PDF_BYTES = 10L * 1024 * 1024;

    /** Signature d'un fichier PDF : les octets ASCII de "%PDF-". */
    private static final byte[] PDF_SIGNATURE = {0x25, 0x50, 0x44, 0x46, 0x2D};

    private final MediaAssetRepository repository;
    private final FileStorage storage;

    public MediaService(MediaAssetRepository repository, FileStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    /**
     * Valide puis stocke un PDF. Le nom de stockage interne est basé sur l'UUID de
     * l'asset, jamais sur le nom de fichier envoyé par le client.
     */
    public MediaAsset storePdf(UUID restaurantId, byte[] content, String declaredContentType, String originalFilename) {
        validatePdf(content, declaredContentType);

        UUID assetId = UUID.randomUUID();
        String storageKey = restaurantId + "/" + assetId + ".pdf";
        storage.store(storageKey, content);

        MediaAsset asset = new MediaAsset(
                assetId,
                restaurantId,
                MediaKind.PDF,
                storageKey,
                "application/pdf",
                content.length,
                cleanFilename(originalFilename));
        return repository.save(asset);
    }

    public MediaAsset getOrThrow(UUID assetId) {
        return repository.findById(assetId)
                .orElseThrow(() -> new NotFoundException("Média introuvable: " + assetId));
    }

    public byte[] readContent(MediaAsset asset) {
        return storage.read(asset.getStorageKey());
    }

    public void delete(MediaAsset asset) {
        repository.delete(asset);
        storage.delete(asset.getStorageKey());
    }

    /**
     * Supprime uniquement les FICHIERS de tous les médias d'un restaurant (best effort).
     * Les lignes media_assets sont supprimées ensuite par le CASCADE de la suppression
     * du restaurant. Séparer les deux évite tout problème d'ordre sur les FK.
     */
    public void deleteFilesForRestaurant(UUID restaurantId) {
        for (MediaAsset asset : repository.findByRestaurantId(restaurantId)) {
            try {
                storage.delete(asset.getStorageKey());
            } catch (RuntimeException ignored) {
                // fichier déjà absent : on continue
            }
        }
    }

    private void validatePdf(byte[] content, String declaredContentType) {
        if (content == null || content.length == 0) {
            throw new InvalidUploadException("Le fichier est vide.");
        }
        if (content.length > MAX_PDF_BYTES) {
            throw new InvalidUploadException("Le PDF dépasse la taille maximale de 10 Mo.");
        }
        String type = declaredContentType == null ? "" : declaredContentType.split(";")[0].trim().toLowerCase();
        if (!"application/pdf".equals(type)) {
            throw new InvalidUploadException("Seuls les fichiers PDF sont acceptés.");
        }
        if (!hasPdfSignature(content)) {
            throw new InvalidUploadException("Le fichier n'est pas un PDF valide (signature manquante).");
        }
    }

    private static boolean hasPdfSignature(byte[] content) {
        if (content.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < PDF_SIGNATURE.length; i++) {
            if (content[i] != PDF_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    /** Nettoie le nom fourni par le client (retire chemin, guillemets, sauts de ligne). */
    private static String cleanFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String name = raw.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\r\\n\"]", "").trim();
        if (name.isBlank()) {
            return null;
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
