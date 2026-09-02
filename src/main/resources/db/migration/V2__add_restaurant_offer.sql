-- V2: Offre commerciale du restaurant (Karta V1 : BASIC | PRO | PREMIUM).
-- Règle métier : 1 restaurant = 1 offre. Le paiement / changement d'offre est hors périmètre V1.
-- Syntaxe volontairement portable (PostgreSQL en prod, H2 en tests).

ALTER TABLE restaurants ADD COLUMN offer VARCHAR(20);

-- Restaurants déjà présents avant cette migration : rattachés à l'offre d'entrée.
UPDATE restaurants SET offer = 'BASIC' WHERE offer IS NULL;

ALTER TABLE restaurants ALTER COLUMN offer SET NOT NULL;

ALTER TABLE restaurants
    ADD CONSTRAINT chk_restaurants_offer CHECK (offer IN ('BASIC', 'PRO', 'PREMIUM'));
