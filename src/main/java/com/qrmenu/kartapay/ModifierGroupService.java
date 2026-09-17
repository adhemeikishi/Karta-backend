package com.qrmenu.kartapay;

import com.qrmenu.common.InvalidMenuException;
import com.qrmenu.common.NotFoundException;
import com.qrmenu.kartapay.ModifierDtos.ModifierGroupResponse;
import com.qrmenu.kartapay.ModifierDtos.ModifierOptionResponse;
import com.qrmenu.kartapay.ModifierDtos.SaveModifierGroupRequest;
import com.qrmenu.kartapay.ModifierDtos.SaveModifierOptionRequest;
import com.qrmenu.menu.MenuCategory;
import com.qrmenu.menu.MenuCategoryRepository;
import com.qrmenu.menu.MenuItem;
import com.qrmenu.menu.MenuItemRepository;
import com.qrmenu.menu.MenuRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Groupes d'options d'un produit du menu (ex : "Cuisson", "Suppléments").
 *
 * Écriture par <strong>document complet</strong>, comme {@code MenuStructureService} :
 * {@link #replace} remplace tous les groupes (et leurs options) d'un produit en un appel.
 */
@Service
public class ModifierGroupService {

    private final ModifierGroupRepository groupRepository;
    private final ModifierOptionRepository optionRepository;
    private final MenuItemRepository itemRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuRepository menuRepository;

    public ModifierGroupService(
            ModifierGroupRepository groupRepository,
            ModifierOptionRepository optionRepository,
            MenuItemRepository itemRepository,
            MenuCategoryRepository categoryRepository,
            MenuRepository menuRepository
    ) {
        this.groupRepository = groupRepository;
        this.optionRepository = optionRepository;
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.menuRepository = menuRepository;
    }

    @Transactional(readOnly = true)
    public List<ModifierGroupResponse> load(UUID restaurantId, UUID itemId) {
        requireItemOfRestaurant(restaurantId, itemId);
        List<ModifierGroup> groups = groupRepository.findByItemIdOrderBySortOrderAscNameAsc(itemId);
        Map<UUID, List<ModifierOption>> optionsByGroup = loadOptions(groups);
        return groups.stream().map(group -> toResponse(group, optionsByGroup)).toList();
    }

    @Transactional
    public List<ModifierGroupResponse> replace(UUID restaurantId, UUID itemId, List<SaveModifierGroupRequest> requested) {
        requireItemOfRestaurant(restaurantId, itemId);
        List<SaveModifierGroupRequest> incoming = requested == null ? List.of() : requested;

        Map<UUID, ModifierGroup> existingGroups = groupRepository
                .findByItemIdOrderBySortOrderAscNameAsc(itemId).stream()
                .collect(Collectors.toMap(ModifierGroup::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, ModifierOption> existingOptions = loadOptions(existingGroups.values().stream().toList())
                .values().stream().flatMap(List::stream)
                .collect(Collectors.toMap(ModifierOption::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        Set<UUID> keptGroups = new LinkedHashSet<>();
        Set<UUID> keptOptions = new LinkedHashSet<>();
        List<ModifierGroupResponse> responses = new java.util.ArrayList<>();

        for (int i = 0; i < incoming.size(); i++) {
            SaveModifierGroupRequest request = incoming.get(i);
            ModifierGroup group = resolveGroup(itemId, existingGroups, keptGroups, request);

            String name = requireName(request.name());
            SelectionType selectionType = request.selectionType();
            int minSelect = request.minSelect() == null ? 0 : request.minSelect();
            Integer maxSelect = selectionType == SelectionType.SINGLE ? 1 : request.maxSelect();
            if (maxSelect != null && minSelect > maxSelect) {
                throw new InvalidMenuException("minSelect ne peut pas dépasser maxSelect: " + name);
            }
            group.update(name, selectionType, minSelect, maxSelect, request.sortOrder() == null ? i : request.sortOrder());
            groupRepository.save(group);

            List<SaveModifierOptionRequest> options = request.options() == null ? List.of() : request.options();
            List<ModifierOptionResponse> optionResponses = new java.util.ArrayList<>();
            for (int j = 0; j < options.size(); j++) {
                SaveModifierOptionRequest optionRequest = options.get(j);
                ModifierOption option = resolveOption(group.getId(), existingOptions, keptOptions, optionRequest);
                option.update(
                        requireName(optionRequest.name()),
                        optionRequest.priceDeltaCents() == null ? 0 : optionRequest.priceDeltaCents(),
                        optionRequest.available() == null || optionRequest.available(),
                        optionRequest.sortOrder() == null ? j : optionRequest.sortOrder());
                optionRepository.save(option);
                optionResponses.add(toOptionResponse(option));
            }
            responses.add(new ModifierGroupResponse(
                    group.getId(), group.getName(), group.getSelectionType(),
                    group.getMinSelect(), group.getMaxSelect(), group.getSortOrder(), optionResponses));
        }

        existingOptions.values().stream()
                .filter(option -> !keptOptions.contains(option.getId()))
                .forEach(optionRepository::delete);
        existingGroups.values().stream()
                .filter(group -> !keptGroups.contains(group.getId()))
                .forEach(groupRepository::delete);

        return responses;
    }

    // ---------------------------------------------------------------- internes

    private ModifierGroup resolveGroup(
            UUID itemId,
            Map<UUID, ModifierGroup> existing,
            Set<UUID> kept,
            SaveModifierGroupRequest request
    ) {
        if (request.id() == null) {
            ModifierGroup created = new ModifierGroup(itemId);
            kept.add(created.getId());
            return created;
        }
        ModifierGroup group = existing.get(request.id());
        if (group == null) {
            throw new InvalidMenuException("Groupe d'options inconnu pour ce produit: " + request.id());
        }
        if (!kept.add(group.getId())) {
            throw new InvalidMenuException("Groupe d'options envoyé deux fois: " + request.id());
        }
        return group;
    }

    private ModifierOption resolveOption(
            UUID groupId,
            Map<UUID, ModifierOption> existing,
            Set<UUID> kept,
            SaveModifierOptionRequest request
    ) {
        if (request.id() == null) {
            ModifierOption created = new ModifierOption(groupId);
            kept.add(created.getId());
            return created;
        }
        ModifierOption option = existing.get(request.id());
        if (option == null || !option.getGroupId().equals(groupId)) {
            throw new InvalidMenuException("Option inconnue pour ce groupe: " + request.id());
        }
        if (!kept.add(option.getId())) {
            throw new InvalidMenuException("Option envoyée deux fois: " + request.id());
        }
        return option;
    }

    private Map<UUID, List<ModifierOption>> loadOptions(List<ModifierGroup> groups) {
        if (groups.isEmpty()) {
            return Map.of();
        }
        List<UUID> groupIds = groups.stream().map(ModifierGroup::getId).toList();
        return optionRepository.findByGroupIdInOrderBySortOrderAscNameAsc(groupIds).stream()
                .collect(Collectors.groupingBy(ModifierOption::getGroupId, LinkedHashMap::new, Collectors.toList()));
    }

    private ModifierGroupResponse toResponse(ModifierGroup group, Map<UUID, List<ModifierOption>> optionsByGroup) {
        List<ModifierOptionResponse> options = optionsByGroup.getOrDefault(group.getId(), List.of()).stream()
                .map(this::toOptionResponse)
                .toList();
        return new ModifierGroupResponse(
                group.getId(), group.getName(), group.getSelectionType(),
                group.getMinSelect(), group.getMaxSelect(), group.getSortOrder(), options);
    }

    private ModifierOptionResponse toOptionResponse(ModifierOption option) {
        return new ModifierOptionResponse(
                option.getId(), option.getName(), option.getPriceDeltaCents(),
                option.isAvailable(), option.getSortOrder());
    }

    private static String requireName(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new InvalidMenuException("le nom est obligatoire");
        }
        return value;
    }

    /** Vérifie que le produit existe et appartient bien à ce restaurant (via catégorie -> menu). */
    private void requireItemOfRestaurant(UUID restaurantId, UUID itemId) {
        MenuItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Produit introuvable: " + itemId));
        MenuCategory category = categoryRepository.findById(item.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Catégorie introuvable: " + item.getCategoryId()));
        UUID menuRestaurantId = menuRepository.findById(category.getMenuId())
                .orElseThrow(() -> new NotFoundException("Menu introuvable: " + category.getMenuId()))
                .getRestaurantId();
        if (!menuRestaurantId.equals(restaurantId)) {
            throw new NotFoundException("Produit introuvable: " + itemId);
        }
    }
}
