-- V5: Apparence du menu structuré (studio de design Karta).
--
-- Cinq colonnes plates plutôt qu'un theme_json : le preset est contraint en base, les
-- couleurs sont validées à l'écriture, et rien n'a besoin d'être parsé pour compter les
-- menus par style. PREMIUM pose une identité (nom, logo, deux couleurs, une image) —
-- ce n'est pas un moteur de thème.
--
-- Aucune de ces colonnes n'appartient au contenu : les modifier ne touche ni aux
-- catégories, ni aux produits, ni au statut de publication.
-- Syntaxe volontairement portable (PostgreSQL en prod, H2 en tests).

-- 1. Style de base. Les menus existants prennent MODERN, le preset par défaut.
ALTER TABLE menus ADD COLUMN preset VARCHAR(32);

UPDATE menus SET preset = 'MODERN' WHERE preset IS NULL;

ALTER TABLE menus ALTER COLUMN preset SET NOT NULL;

ALTER TABLE menus
    ADD CONSTRAINT chk_menus_preset
        CHECK (preset IN ('MODERN', 'DARK', 'STREET_FOOD', 'MINIMAL', 'LUXE'));

-- 2. Identité PREMIUM. Toutes nullables : l'absence de valeur = on garde le preset.
--    Les couleurs sont stockées en #RRGGBB (7 caractères), jamais en mot-clé CSS.
ALTER TABLE menus ADD COLUMN brand_name VARCHAR(120);
ALTER TABLE menus ADD COLUMN primary_color VARCHAR(7);
ALTER TABLE menus ADD COLUMN secondary_color VARCHAR(7);
ALTER TABLE menus ADD COLUMN logo_asset_id UUID;
ALTER TABLE menus ADD COLUMN hero_asset_id UUID;

-- Les images vivent dans media_assets : en supprimer une ne doit jamais supprimer le
-- menu, seulement retirer l'illustration.
ALTER TABLE menus
    ADD CONSTRAINT fk_menus_logo
        FOREIGN KEY (logo_asset_id) REFERENCES media_assets (id) ON DELETE SET NULL;

ALTER TABLE menus
    ADD CONSTRAINT fk_menus_hero
        FOREIGN KEY (hero_asset_id) REFERENCES media_assets (id) ON DELETE SET NULL;
