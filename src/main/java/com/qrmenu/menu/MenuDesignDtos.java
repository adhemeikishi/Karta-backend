package com.qrmenu.menu;

import com.qrmenu.restaurant.RestaurantOffer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Contrat JSON du studio de design : {@code GET / PUT .../menu/design}.
 *
 * La réponse est <strong>auto-suffisante</strong> — elle porte aussi le catalogue des
 * presets et leurs couleurs. L'interface n'a donc aucune palette codée en dur : ajouter
 * un preset côté serveur le fait apparaître dans le studio sans retoucher le front, et
 * les pastilles du sélecteur ne peuvent pas mentir sur le rendu réel.
 */
public class MenuDesignDtos {

    private MenuDesignDtos() {
    }

    /**
     * @param offer         offre du client, source de vérité des droits
     * @param customizable  vrai pour PREMIUM uniquement : conditionne l'édition de
     *                      l'identité, jamais l'accès à l'aperçu
     * @param preset        preset enregistré
     * @param presets       catalogue complet, avec les couleurs réellement rendues
     * @param customization identité enregistrée — conservée même hors PREMIUM, mais alors
     *                      ni rendue ni publiée (une rétrogradation ne détruit rien)
     */
    public record DesignResponse(
            RestaurantOffer offer,
            boolean customizable,
            MenuPreset preset,
            List<PresetOption> presets,
            Customization customization
    ) {

        public static List<PresetOption> catalogue() {
            return Arrays.stream(MenuPreset.values()).map(PresetOption::of).toList();
        }
    }

    public record PresetOption(
            MenuPreset id,
            String label,
            String background,
            String accent,
            String text
    ) {
        static PresetOption of(MenuPreset preset) {
            return new PresetOption(preset, preset.label(), preset.background(), preset.accent(), preset.text());
        }
    }

    public record Customization(
            String brandName,
            String primaryColor,
            String secondaryColor,
            UUID logoAssetId,
            String logoUrl,
            UUID heroAssetId,
            String heroUrl
    ) {
    }

    /**
     * Corps de {@code PUT .../menu/design}. Document complet : un champ absent efface la
     * valeur correspondante, ce qui donne à l'interface un moyen simple de retirer un
     * logo ou une couleur sans endpoint dédié.
     */
    public record SaveDesignRequest(
            @NotNull(message = "preset est obligatoire")
            MenuPreset preset,

            @Size(max = 120, message = "le nom affiché est trop long")
            String brandName,

            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "la couleur principale doit être au format #RRGGBB")
            String primaryColor,

            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "la couleur secondaire doit être au format #RRGGBB")
            String secondaryColor,

            UUID logoAssetId,
            UUID heroAssetId
    ) {
    }
}
