-- V3: Système de menu Karta - Phase 1 (BASIC / PDF).
--   media_assets : références des fichiers stockés sur disque (jamais le contenu en base).
--   menus        : 1 par restaurant (contrainte UNIQUE sur restaurant_id).
-- Rien de STRUCTURED ici (pas de menu_categories / menu_items / theme_json / presets).
-- Syntaxe volontairement portable (PostgreSQL en prod, H2 en tests).

CREATE TABLE media_assets (
    id                UUID PRIMARY KEY,
    restaurant_id     UUID NOT NULL,
    kind              VARCHAR(20) NOT NULL,
    storage_key       VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT NOT NULL,
    original_filename VARCHAR(255),
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_media_assets_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE,
    CONSTRAINT chk_media_assets_kind CHECK (kind IN ('PDF', 'IMAGE'))
);

CREATE INDEX idx_media_assets_restaurant_id ON media_assets (restaurant_id);

CREATE TABLE menus (
    id             UUID PRIMARY KEY,
    restaurant_id  UUID NOT NULL,
    type           VARCHAR(20) NOT NULL,
    pdf_asset_id   UUID,
    fallback_url   TEXT,
    published      BOOLEAN NOT NULL DEFAULT FALSE,
    published_at   TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_menus_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES restaurants (id) ON DELETE CASCADE,
    CONSTRAINT uq_menus_restaurant UNIQUE (restaurant_id),
    CONSTRAINT fk_menus_pdf_asset
        FOREIGN KEY (pdf_asset_id) REFERENCES media_assets (id) ON DELETE SET NULL,
    CONSTRAINT chk_menus_type CHECK (type IN ('PDF', 'STRUCTURED'))
);
