package com.qrmenu.menu;

import java.util.UUID;

/**
 * Signale qu'un menu vient d'être écrit et qu'un éventuel brouillon d'import peut être
 * consommé.
 *
 * Existe pour une raison précise : sans elle, {@code menu} dépendrait de {@code kartaai}
 * qui dépend déjà de {@code menu} — le premier cycle de paquets du projet. L'implémentation
 * vit dans {@code kartaai}, qui reste seul à connaître ce qu'est un brouillon.
 *
 * Appelée dans la transaction d'écriture du menu : un échec en amont annule tout, et le
 * brouillon reste disponible pour reprendre la Review.
 */
public interface MenuDraftConsumer {

    void consume(UUID restaurantId);
}
