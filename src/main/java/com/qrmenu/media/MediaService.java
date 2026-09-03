package com.qrmenu.media;

import com.qrmenu.common.InvalidUploadException;
import com.qrmenu.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MediaService {

    /** Taille maximale d'un PDF de menu : 10 Mo. */
    public static final long MAX_PDF_BYTES = 10L * 1024 * 1024;

    /** Taille maximale d'une image de produit : 5 Mo (photo de plat, pas de master print). */
    public static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

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

    /**
     * Valide puis stocke une image de produit (JPEG / PNG / WebP).
     *
     * Le type retenu est celui déduit de la <strong>signature</strong> du fichier, jamais
     * celui annoncé par le client : un exécutable renommé {@code plat.png} est rejeté, et
     * un fichier accepté ne peut pas être servi sous un type MIME qui n'est pas le sien.
     */
    public MediaAsset storeImage(UUID restaurantId, byte[] content, String originalFilename) {
        ImageFormat format = validateImage(content);

        UUID assetId = UUID.randomUUID();
        String storageKey = restaurantId + "/" + assetId + "." + format.extension();
        storage.store(storageKey, content);

        MediaAsset asset = new MediaAsset(
                assetId,
                restaurantId,
                MediaKind.IMAGE,
                storageKey,
                format.contentType(),
                content.length,
                cleanFilename(originalFilename));
        return repository.save(asset);
    }

    public MediaAsset getOrThrow(UUID assetId) {
        return repository.findById(assetId)
                .orElseThrow(() -> new NotFoundException("Média introuvable: " + assetId));
    }

    /**
     * Image existante appartenant bien à ce client, ou {@code null} si {@code assetId}
     * l'est.
     *
     * Trois refus distincts, tous nécessaires : un asset inconnu, un asset d'un autre
     * client (référence croisée entre restaurants) et un PDF présenté comme image
     * produiraient chacun une image cassée sur la page publique — que le renderer ne peut
     * pas rattraper. On tranche donc à l'écriture.
     */
    public UUID requireOwnedImage(UUID restaurantId, UUID assetId) {
        if (assetId == null) {
            return null;
        }
        MediaAsset asset;
        try {
            asset = getOrThrow(assetId);
        } catch (NotFoundException e) {
            throw new InvalidUploadException("Image introuvable: " + assetId);
        }
        if (!asset.getRestaurantId().equals(restaurantId)) {
            throw new InvalidUploadException("Image non rattachée à ce client: " + assetId);
        }
        if (asset.getKind() != MediaKind.IMAGE) {
            throw new InvalidUploadException("L'asset référencé n'est pas une image: " + assetId);
        }
        return assetId;
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

    /** Contrôles de taille puis de signature. Renvoie le format réellement détecté. */
    private ImageFormat validateImage(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidUploadException("Le fichier est vide.");
        }
        if (content.length > MAX_IMAGE_BYTES) {
            throw new InvalidUploadException("L'image dépasse la taille maximale de 5 Mo.");
        }
        ImageFormat format = ImageFormat.detect(content);
        if (format == null) {
            throw new InvalidUploadException("Seules les images JPEG, PNG et WebP sont acceptées.");
        }
        return format;
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
