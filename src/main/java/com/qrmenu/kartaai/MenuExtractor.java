package com.qrmenu.kartaai;

import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;

/**
 * Extraction du contenu d'une carte PDF vers une structure de données.
 *
 * Une seule interface, une seule implémentation réelle : pas de Factory, pas
 * d'Orchestrator, pas de Strategy. Elle existe pour deux raisons concrètes — permettre
 * aux tests de ne jamais appeler d'API payante, et permettre de changer de fournisseur
 * sans toucher au service ni aux contrôleurs.
 *
 * L'implémentation ne produit jamais de HTML : uniquement un {@link ExtractedMenu}, dont
 * le contenu est ensuite considéré comme <strong>non fiable</strong> et validé par
 * {@link ExtractionValidator}.
 */
public interface MenuExtractor {

    /**
     * @param pdf      contenu du PDF, déjà validé (signature + taille) par MediaService
     * @param filename nom d'origine, utile au modèle comme indice de contexte
     * @throws ExtractionException si l'extraction échoue ou ne rend rien d'exploitable
     */
    ExtractedMenu extract(byte[] pdf, String filename);

    /** Le service est-il configuré ? Faux = KartaAI désactivé (aucune clé fournie). */
    boolean isAvailable();
}
