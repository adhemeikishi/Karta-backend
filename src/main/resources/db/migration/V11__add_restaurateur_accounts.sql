-- Inscription en libre-service : vraie table de comptes RESTAURATEUR (1 compte = 1 restaurant),
-- et distinction entre "offre choisie" (restaurants.offer, inchangé) et "abonnement payé"
-- (nouveau : restaurants.subscription_active).
--
-- Le compte ADMIN n'est pas concerné : il reste in-memory (voir SecurityConfig), comme avant.
--
-- Migration additive, sans risque pour les restaurants existants :
--   - subscription_active est ajoutée avec DEFAULT TRUE, donc tout restaurant déjà en
--     base (créé avant l'existence même de la notion d'abonnement) reste pleinement actif.
--   - restaurateur_accounts est une table neuve : rien n'y est migré automatiquement, le
--     compte restaurateur historique (resto@karta.local, config statique) continue de
--     fonctionner sans y être présent (voir SecurityConfig / RestaurateurAccountResolver).

ALTER TABLE restaurants
    ADD COLUMN subscription_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE restaurateur_accounts (
    id             UUID PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    restaurant_id  UUID NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_restaurateur_accounts_email UNIQUE (email),
    CONSTRAINT uq_restaurateur_accounts_restaurant UNIQUE (restaurant_id),
    CONSTRAINT fk_restaurateur_accounts_restaurant FOREIGN KEY (restaurant_id)
        REFERENCES restaurants (id) ON DELETE CASCADE
);
