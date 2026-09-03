package com.qrmenu.admin;

import com.qrmenu.kartaai.ExtractionDtos.DraftResponse;
import com.qrmenu.kartaai.MenuDraftService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * KartaAI : extraction d'une carte PDF vers un brouillon relisible.
 * Sous {@code /api/admin/**} : Basic Auth exigé (voir SecurityConfig).
 *
 * Trois routes, volontairement : produire un brouillon, le relire, l'abandonner.
 * <strong>Il n'existe aucune route de validation ici</strong> — le menu s'écrit par le
 * {@code PUT .../menu} existant, déjà validé et testé. Un endpoint « appliquer » aurait
 * dupliqué cette validation, avec le risque qu'une des deux copies dérive.
 */
@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}/menu/ai")
public class MenuDraftAdminController {

    private final MenuDraftService draftService;

    public MenuDraftAdminController(MenuDraftService draftService) {
        this.draftService = draftService;
    }

    /**
     * Analyse la carte PDF du client et produit un brouillon.
     *
     * N'écrit rien dans le menu : la carte publiée, s'il y en a une, reste intacte.
     * 409 si l'offre est BASIC ou si aucun PDF n'a été importé.
     */
    @PostMapping("/import")
    public DraftResponse importFromPdf(@PathVariable UUID restaurantId) {
        return draftService.importFromPdf(restaurantId);
    }

    /** Brouillon en attente de Review. 404 s'il n'y en a pas. */
    @GetMapping("/draft")
    public DraftResponse getDraft(@PathVariable UUID restaurantId) {
        return draftService.getDraft(restaurantId);
    }

    /** Abandonne la Review. Ne touche ni au menu ni au PDF source. */
    @DeleteMapping("/draft")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable UUID restaurantId) {
        draftService.discard(restaurantId);
    }
}
