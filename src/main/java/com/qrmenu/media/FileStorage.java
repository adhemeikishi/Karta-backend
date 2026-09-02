package com.qrmenu.media;

/**
 * Abstraction du stockage de fichiers. Phase 1 : implémentation disque local
 * ({@link LocalFileStorage}). Permet de basculer plus tard vers un stockage objet
 * (S3…) sans toucher aux appelants.
 */
public interface FileStorage {

    /** Écrit le contenu sous la clé donnée (créée ou écrasée). */
    void store(String storageKey, byte[] content);

    /** Lit le contenu. Lève si la clé est absente. */
    byte[] read(String storageKey);

    /** Supprime le fichier s'il existe (aucune erreur s'il est déjà absent). */
    void delete(String storageKey);

    boolean exists(String storageKey);
}
