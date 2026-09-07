package com.qrmenu.kartaai;

/**
 * Éléments communs aux implémentations de {@link MenuExtractor} (Gemini, Anthropic).
 *
 * <ul>
 *   <li>{@link #PROMPT} — la consigne d'extraction. C'est un <strong>contrat métier</strong>,
 *       indépendant du fournisseur : les deux providers l'envoient à l'identique, pour
 *       qu'un changement de provider ne change jamais ce qu'on demande au modèle.</li>
 *   <li>{@link #extractJsonObject} / {@link #safeFilename} — lecture d'une réponse de
 *       modèle traitée comme <strong>non fiable</strong> (un préambule, un bloc de code,
 *       un nom de fichier piégé restent possibles).</li>
 * </ul>
 *
 * L'assainissement sémantique du résultat reste le fait de {@link ExtractionValidator}.
 */
final class ExtractionSupport {

    private ExtractionSupport() {
    }

    /**
     * Consigne d'extraction — contrat métier, identique quel que soit le fournisseur.
     *
     * Exigences non négociables : lire la carte en entier sans rien écarter, des centimes
     * entiers (Karta n'exprime aucun montant en flottant), aucun HTML, et {@code price: null}
     * plutôt qu'une invention quand le prix est illisible. Un prix inventé serait publié
     * sans que personne ne le remarque — un prix absent est signalé en Review.
     */
    static final String PROMPT = """
            Tu extrais le contenu COMPLET d'une carte de restaurant à partir du PDF fourni.

            Réponds UNIQUEMENT avec un objet JSON valide, sans texte autour, sans bloc de
            code, sans HTML. Format exact :

            {"categories":[{"name":"...","items":[
              {"name":"...","description":"...","price":950,"currency":"EUR",
               "needsReview":false,"note":null}
            ]}]}

            Règles :
            - Lis l'INTÉGRALITÉ de la carte, page par page. Ne saute aucune section.
            - Reprends TOUS les éléments : entrées, plats, desserts, boissons, formules et
              menus, ainsi que les variantes (tailles, portions) et les suppléments. Si une
              variante ou un supplément n'a pas sa propre ligne de prix, indique-le dans la
              "description" du plat concerné. Ne supprime jamais un élément de la carte.
            - "price" est un ENTIER en CENTIMES : 9,50 € s'écrit 950. Jamais de décimale.
            - Si un prix est illisible, ambigu ou absent, mets "price": null et
              "needsReview": true. N'invente JAMAIS un prix.
            - "needsReview": true dès que tu as un doute sur un plat (nom coupé, deux
              prix possibles, rattachement de catégorie incertain), avec une "note" très
              courte en français expliquant le doute. Sinon "needsReview": false et
              "note": null.
            - "description" : la description du plat si elle figure sur la carte, sinon null.
            - "currency" : code ISO du prix affiché ("EUR" par défaut).
            - Respecte l'ordre et les catégories de la carte. N'invente aucun plat, aucune
              catégorie, aucune description.
            - Ignore ce qui n'est pas la carte : horaires, adresse, mentions légales, wifi.
            - Si le document n'est pas une carte de restaurant, réponds {"categories":[]}.
            """;

    /**
     * Isole l'objet JSON du texte renvoyé par le modèle.
     *
     * Le modèle est prié de ne rendre que du JSON (et les deux providers activent leur
     * mode JSON natif), mais un préambule ou un bloc de code reste possible ; on ne fait
     * pas dépendre le parcours d'une politesse de formatage. On borne sur les accolades
     * extrêmes plutôt qu'une expression régulière : un JSON imbriqué la mettrait en défaut.
     */
    static String extractJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start < 0 || end <= start) ? null : text.substring(start, end + 1);
    }

    /** Un nom de fichier arrive du client : jamais journalisé brut (injection de log). */
    static String safeFilename(String filename) {
        if (filename == null) {
            return "(sans nom)";
        }
        String cleaned = filename.replaceAll("[\\r\\n\\t]", "_");
        return cleaned.length() <= 120 ? cleaned : cleaned.substring(0, 120);
    }
}
