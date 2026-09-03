-- V7: Garantit « 1 restaurant = 1 QR » au niveau du schéma, pas seulement par convention.
--
-- Jusqu'ici, rien n'empêchait d'appeler POST /api/admin/restaurants/{id}/qr-codes deux
-- fois pour le même restaurant : seule une convention frontend/documentaire limitait
-- l'usage au premier QR trouvé (voir QrCodeRepository.findFirstByRestaurantId).
--
-- Aucune donnée existante n'est modifiée ici — le rattrapage des restaurants sans QR
-- (créés avant cette règle) est fait en Java au démarrage (QrCodeBackfillRunner), pas en
-- SQL, pour réutiliser exactement la même génération de code que le reste du produit
-- (QrCodeGenerator, alphabet sans caractères ambigus, vérification d'unicité).
--
-- Sûr à appliquer immédiatement : au moment de l'écriture, aucun restaurant n'a plus
-- d'un QR (vérifié en base) — un restaurant sans aucun QR ne viole pas une contrainte
-- UNIQUE (rien à comparer).

ALTER TABLE qr_codes
    ADD CONSTRAINT uq_qr_codes_restaurant_id UNIQUE (restaurant_id);
