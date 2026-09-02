-- V4: Fondation du menu structuré Karta (offres PRO / PREMIUM).
--   menus            : statut explicite + version de contenu (remplace le booléen published).
--   menu_categories  : catégories ordonnables d'un menu.
--   menu_items       : produits, prix en CENTIMES ENTIERS (jamais de flottant sur de la monnaie).
-- Aucune table d'options / de thème / de commande ici : elles seront ajoutées en additif
-- (voir docs/MENU_STRUCTURED.md).
-- Syntaxe volontairement portable (PostgreSQL en prod, H2 en tests).

-- 1. Statut du menu : DRAFT -> READY -> PUBLISHED, source de vérité unique.
ALTER TABLE menus ADD COLUMN status VARCHAR(20);

UPDATE menus
SET status = CASE
                 WHEN published THEN 'PUBLISHED'
                 WHEN pdf_asset_id IS NOT NULL THEN 'READY'
                 ELSE 'DRAFT'
             END;

ALTER TABLE menus ALTER COLUMN status SET NOT NULL;

ALTER TABLE menus
    ADD CONSTRAINT chk_menus_status CHECK (status IN ('DRAFT', 'READY', 'PUBLISHED'));

-- 2. Révision du contenu (repère simple, pas d'historique).
ALTER TABLE menus ADD COLUMN version INTEGER DEFAULT 1 NOT NULL;

-- 3. Le booléen devient redondant : le statut le porte désormais.
ALTER TABLE menus DROP COLUMN published;

-- 4. Catégories.
CREATE TABLE menu_categories (
    id          UUID PRIMARY KEY,
    menu_id     UUID NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description TEXT,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    visible     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_menu_categories_menu
        FOREIGN KEY (menu_id) REFERENCES menus (id) ON DELETE CASCADE,
    CONSTRAINT chk_menu_categories_sort_order CHECK (sort_order >= 0)
);

CREATE INDEX idx_menu_categories_menu_id ON menu_categories (menu_id);

-- 5. Produits. price_cents : 1290 = 12,90 EUR.
CREATE TABLE menu_items (
    id             UUID PRIMARY KEY,
    category_id    UUID NOT NULL,
    name           VARCHAR(160) NOT NULL,
    description    TEXT,
    price_cents    INTEGER NOT NULL,
    currency       VARCHAR(3) NOT NULL DEFAULT 'EUR',
    image_asset_id UUID,
    sort_order     INTEGER NOT NULL DEFAULT 0,
    available      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_menu_items_category
        FOREIGN KEY (category_id) REFERENCES menu_categories (id) ON DELETE CASCADE,
    -- L'image vit dans media_assets : la supprimer ne doit jamais supprimer le plat.
    CONSTRAINT fk_menu_items_image
        FOREIGN KEY (image_asset_id) REFERENCES media_assets (id) ON DELETE SET NULL,
    CONSTRAINT chk_menu_items_price CHECK (price_cents >= 0),
    CONSTRAINT chk_menu_items_sort_order CHECK (sort_order >= 0)
);

CREATE INDEX idx_menu_items_category_id ON menu_items (category_id);
