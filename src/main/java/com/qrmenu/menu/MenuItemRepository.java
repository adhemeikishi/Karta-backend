package com.qrmenu.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    List<MenuItem> findByCategoryIdInOrderBySortOrderAscNameAsc(Collection<UUID> categoryIds);
}
