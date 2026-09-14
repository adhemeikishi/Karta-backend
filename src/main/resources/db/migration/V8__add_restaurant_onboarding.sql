-- Fin d'onboarding du restaurateur.
--
-- Persistée en base, jamais dans le navigateur : le restaurateur doit retrouver son
-- espace configuré depuis n'importe quel appareil.
--
-- Marqueur à sens unique, posé à la PREMIÈRE publication du menu (voir MenuService.publish).
-- Dépublier ensuite ne le retire pas : le restaurant a été configuré une fois pour toutes,
-- et repasser quelqu'un par l'accueil « Bienvenue sur Karta » parce qu'il a masqué sa carte
-- une journée serait absurde.
--
-- NULL = onboarding non terminé. Les restaurants existants sont donc considérés comme
-- non onboardés ; ceux qui ont déjà un menu publié sont rattrapés juste après, pour ne
-- pas renvoyer un client en production dans un parcours de découverte.
ALTER TABLE restaurants
    ADD COLUMN onboarding_completed_at TIMESTAMP WITH TIME ZONE;

-- Sous-requête corrélée plutôt qu'un `UPDATE ... FROM` : cette syntaxe PostgreSQL n'est
-- pas comprise par H2, sur lequel tournent les tests. `menus` porte une contrainte UNIQUE
-- sur `restaurant_id` (V3), la sous-requête ne peut donc renvoyer qu'une ligne au plus.
UPDATE restaurants r
SET onboarding_completed_at = (
    SELECT m.published_at FROM menus m WHERE m.restaurant_id = r.id
)
WHERE EXISTS (
    SELECT 1 FROM menus m WHERE m.restaurant_id = r.id AND m.published_at IS NOT NULL
);
