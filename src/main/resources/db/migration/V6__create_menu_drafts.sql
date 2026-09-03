-- V6: Brouillon d'extraction KartaAI (PDF -> JSON), en attente de Review.
--
-- Table SÉPARÉE du menu réel. C'est tout l'intérêt : l'extraction n'écrit jamais dans
-- menus / menu_categories / menu_items. Tant que le restaurateur n'a pas validé sa
-- Review, la carte servie aux clients est intacte — y compris si elle est publiée.
--
-- docs/MENU_STRUCTURED.md §4 prévoyait cette table « le jour où l'on voudra re-passer
-- KartaAI sur un menu déjà publié ». Ce jour est arrivé : la publication d'un menu
-- structuré existe depuis l'arrivée du renderer HTML.
--
-- Le brouillon est un DOCUMENT EN ATTENTE, pas une source de vérité : la structure
-- relationnelle reste seule à faire foi une fois la Review validée.
-- Syntaxe volontairement portable (PostgreSQL en prod, H2 en tests).

CREATE TABLE menu_drafts (
    id              UUID PRIMARY KEY,
    -- Un seul brouillon par client, comme il n'y a qu'un menu par client : une
    -- nouvelle extraction remplace la précédente au lieu de s'empiler.
    restaurant_id   UUID NOT NULL,
    -- PDF d'origine. ON DELETE SET NULL : supprimer le PDF ne doit pas faire
    -- disparaître un travail de Review déjà commencé.
    source_asset_id UUID,
    source_filename VARCHAR(255),
    -- Le document d'extraction tel quel. Volontairement TEXT et non des colonnes
    -- éclatées : ce contenu n'est jamais requêté, il est relu en bloc par la Review
    -- puis jeté. L'éclater dupliquerait le modèle du menu pour rien.
    payload         TEXT NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_menu_drafts_restaurant UNIQUE (restaurant_id),
    CONSTRAINT fk_menu_drafts_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE,
    CONSTRAINT fk_menu_drafts_source_asset
        FOREIGN KEY (source_asset_id) REFERENCES media_assets (id) ON DELETE SET NULL
);
