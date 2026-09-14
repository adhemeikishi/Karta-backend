-- V9: Personnalisation avancée de l'offre PREMIUM.
--
-- Tout s'ajoute au document design existant (table menus, voir V5) : un seul endroit
-- pour l'identité du restaurant, un seul gate d'offre côté serveur. Colonnes plates,
-- nullables — NULL = « rien de choisi », le preset ou le rendu par défaut s'applique.
-- Syntaxe volontairement portable (PostgreSQL en prod, H2 en tests).

-- 1. Branding Karta. Un seul drapeau pour la carte ET le QR : les deux ne peuvent pas
--    se contredire.
ALTER TABLE menus ADD COLUMN hide_branding BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Typographie choisie. NULL = celle du preset (aucune police distante chargée).
ALTER TABLE menus ADD COLUMN font VARCHAR(32);

ALTER TABLE menus
    ADD CONSTRAINT chk_menus_font
        CHECK (font IS NULL OR font IN (
            'PLUS_JAKARTA_SANS', 'DM_SANS', 'SPACE_GROTESK', 'PLAYFAIR_DISPLAY', 'LORA', 'INSTRUMENT_SERIF'));

-- 3. Langues activées, en plus du français (codes séparés par des virgules : « en,es »).
ALTER TABLE menus ADD COLUMN languages VARCHAR(32);

-- 4. Apparence du QR. Les couleurs sont stockées en #RRGGBB comme celles du menu.
ALTER TABLE menus ADD COLUMN qr_fg_color VARCHAR(7);
ALTER TABLE menus ADD COLUMN qr_bg_color VARCHAR(7);
ALTER TABLE menus ADD COLUMN qr_module_style VARCHAR(16);
ALTER TABLE menus ADD COLUMN qr_eye_style VARCHAR(16);
ALTER TABLE menus ADD COLUMN qr_logo_asset_id UUID;

ALTER TABLE menus
    ADD CONSTRAINT chk_menus_qr_module_style
        CHECK (qr_module_style IS NULL OR qr_module_style IN ('SQUARE', 'ROUNDED', 'DOTS'));

ALTER TABLE menus
    ADD CONSTRAINT chk_menus_qr_eye_style
        CHECK (qr_eye_style IS NULL OR qr_eye_style IN ('SQUARE', 'ROUNDED', 'CIRCLE'));

ALTER TABLE menus
    ADD CONSTRAINT fk_menus_qr_logo
        FOREIGN KEY (qr_logo_asset_id) REFERENCES media_assets (id) ON DELETE SET NULL;

-- 5. Traductions du contenu. Un document JSON par ligne ({"en":{"name":..,"description":..}}) :
--    il vit et meurt avec la catégorie / le plat, aucune table de jointure à nettoyer.
--    Personne n'interroge une traduction par SQL — un TEXT suffit, comme menu_drafts.payload.
ALTER TABLE menu_categories ADD COLUMN translations TEXT;
ALTER TABLE menu_items ADD COLUMN translations TEXT;
