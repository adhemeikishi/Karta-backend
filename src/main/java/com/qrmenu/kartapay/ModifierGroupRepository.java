package com.qrmenu.kartapay;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, UUID> {

    List<ModifierGroup> findByItemIdOrderBySortOrderAscNameAsc(UUID itemId);

    List<ModifierGroup> findByItemIdInOrderBySortOrderAscNameAsc(Collection<UUID> itemIds);
}
