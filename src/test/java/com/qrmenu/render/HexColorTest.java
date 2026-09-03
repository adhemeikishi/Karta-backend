package com.qrmenu.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dérivation des couleurs du thème.
 *
 * Ces règles portent une garantie produit : quelle que soit la couleur choisie par un
 * client PREMIUM, le menu publié reste lisible.
 */
class HexColorTest {

    @Test
    void acceptsOnlyFullHexadecimalColors() {
        assertThat(HexColor.isValid("#F05A00")).isTrue();
        assertThat(HexColor.isValid("#f05a00")).isTrue();

        assertThat(HexColor.isValid("#F5A")).isFalse();       // forme courte
        assertThat(HexColor.isValid("red")).isFalse();        // mot-clé CSS
        assertThat(HexColor.isValid("F05A00")).isFalse();     // sans dièse
        assertThat(HexColor.isValid("#F05A0")).isFalse();
        assertThat(HexColor.isValid(null)).isFalse();
    }

    @Test
    void normalizeUppercasesAndRejectsInvalidValues() {
        assertThat(HexColor.normalize("#f05a00")).isEqualTo("#F05A00");
        assertThat(HexColor.normalize("chartreuse")).isNull();
        assertThat(HexColor.normalize(null)).isNull();
    }

    @Test
    void mixInterpolatesBetweenTheTwoColors() {
        assertThat(HexColor.mix("#000000", "#FFFFFF", 0)).isEqualTo("#000000");
        assertThat(HexColor.mix("#000000", "#FFFFFF", 1)).isEqualTo("#FFFFFF");
        assertThat(HexColor.mix("#000000", "#FFFFFF", 0.5)).isEqualTo("#808080");
    }

    @Test
    void mixClampsRatiosOutsideZeroOne() {
        assertThat(HexColor.mix("#000000", "#FFFFFF", -2)).isEqualTo("#000000");
        assertThat(HexColor.mix("#000000", "#FFFFFF", 4)).isEqualTo("#FFFFFF");
    }

    @Test
    void readableTextIsDarkOnLightBackgroundsAndLightOnDarkOnes() {
        assertThat(HexColor.readableOn("#FFFFFF")).isEqualTo("#131312");
        assertThat(HexColor.readableOn("#F5EDD8")).isEqualTo("#131312");
        assertThat(HexColor.readableOn("#131312")).isEqualTo("#FFFFFF");
        assertThat(HexColor.readableOn("#012FA4")).isEqualTo("#FFFFFF");
    }

    @Test
    void rejectsInvalidColorsInsteadOfProducingSilentlyWrongOnes() {
        assertThatThrownBy(() -> HexColor.mix("nope", "#FFFFFF", 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
